package app.familygem

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat.getColor
import androidx.core.content.ContextCompat.getDrawable
import androidx.core.view.children
import androidx.core.view.setPadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import app.familygem.BackupViewModel.Companion.NO_URI
import app.familygem.databinding.BackupActivityBinding
import app.familygem.databinding.BackupRecoverFragmentBinding
import app.familygem.databinding.BackupSaveFragmentBinding
import app.familygem.util.TreeUtil
import app.familygem.util.Util
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.zip.ZipInputStream

/** Local backup manager for all trees. */
class BackupActivity : BaseActivity() {

    private lateinit var binding: BackupActivityBinding
    private val viewModel: BackupViewModel by viewModels()

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BackupActivityBinding.inflate(layoutInflater).run {
            binding = this
            setContentView(root)
            backupContainer.visibility = if (Global.settings.backup) View.VISIBLE else View.GONE
            // Backup folder
            viewModel.displayFolder()
            val backupFolderLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    val uri = result.data?.data
                    if (uri != null) {
                        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                        viewModel.setFolder(uri)
                    }
                }
            }
            val pickFolder = View.OnClickListener {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val initialUri = if (Global.settings.backupUri != NO_URI) Uri.parse(Global.settings.backupUri) else Uri.EMPTY
                    intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialUri)
                }
                backupFolderLauncher.launch(intent)
            }
            viewModel.mainState.observe(this@BackupActivity) {
                when (it) {
                    is MainState.Success -> {
                        backupTitle.visibility = View.VISIBLE
                        backupTitle.setOnClickListener(pickFolder)
                        backupText.visibility = View.VISIBLE
                        backupText.text = if (Global.settings.expert) Uri.decode(it.uri.toString()) else it.uri.lastPathSegment
                        backupText.setOnClickListener(pickFolder)
                        backupButton.visibility = View.GONE
                    }
                    is MainState.Error -> {
                        if (it.exception is InvalidUriException) {
                            backupTitle.visibility = View.GONE
                            backupText.visibility = View.GONE
                            backupButton.visibility = View.VISIBLE
                            backupButton.setOnClickListener(pickFolder)
                        } else {
                            backupTitle.visibility = View.VISIBLE
                            backupTitle.setOnClickListener(pickFolder)
                            backupText.visibility = View.VISIBLE
                            backupText.text = it.exception.localizedMessage
                            backupText.setOnClickListener(pickFolder)
                            backupButton.visibility = View.GONE
                        }
                    }
                }
                viewModel.updateState()
            }
            viewModel.loading.observe(this@BackupActivity) {
                backupWheel.root.visibility = if (it) View.VISIBLE else View.GONE
            }
            backupPager.adapter = PagesAdapter(this@BackupActivity)
            TabLayoutMediator(backupTabs, backupPager) { tab, position ->
                tab.text = getString(if (position == 0) R.string.save else R.string.recover)
            }.attach()
            viewModel.getBackupFolder() // To update backup button state
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Backup general switch
        menuInflater.inflate(R.menu.switcher, menu)
        val item = menu.findItem(R.id.switch_item)
        item.setActionView(R.layout.switch_layout)
        val switch = item.actionView!!.findViewById<SwitchCompat>(R.id.switch_widget)
        switch.isChecked = Global.settings.backup
        switch.setOnCheckedChangeListener { _, isChecked ->
            Global.settings.backup = isChecked
            Global.settings.save()
            binding.backupContainer.visibility = if (isChecked) View.VISIBLE else View.GONE
        }
        return true
    }

    class PagesAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {
        override fun createFragment(position: Int): Fragment {
            return if (position == 0) SaveFragment() else RecoverFragment()
        }

        override fun getItemCount() = 2
    }

    /** List of trees that can be selected for backup. */
    class SaveFragment : Fragment() {
        private val viewModel: BackupViewModel by activityViewModels()
        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
            BackupSaveFragmentBinding.inflate(layoutInflater).run {
                viewModel.listActualTrees()
                viewModel.saveState.observe(requireActivity()) { state ->
                    when (state) {
                        SaveState.Success -> {
                            backupSaveBox.removeAllViews()
                            viewModel.treeItems.forEach { item ->
                                val treeView = inflater.inflate(R.layout.backup_tree_item, backupSaveBox, false)
                                backupSaveBox.addView(treeView)
                                treeView.findViewById<TextView>(R.id.backupTree_title).text = item.tree.title
                                val detailView = treeView.findViewById<TextView>(R.id.backupTree_detail)
                                detailView.text = item.detail
                                if (item.backupDone == true)
                                    detailView.setTextColor(getColor(requireContext(), R.color.text))
                                else if (item.backupDone == false)
                                    detailView.setTextColor(getColor(requireContext(), android.R.color.holo_red_dark))
                                val checkbox = treeView.findViewById<CheckBox>(R.id.backupTree_checkbox)
                                checkbox.isChecked = item.tree.backup
                                checkbox.setOnClickListener {
                                    viewModel.selectTree(item)
                                }
                                treeView.setOnClickListener {
                                    viewModel.selectTree(item)
                                    checkbox.isChecked = item.tree.backup
                                }
                            }
                            viewModel.working(false)
                        }
                        is SaveState.Error -> {
                            val view = TextView(requireContext())
                            view.text = state.message
                            view.setPadding(U.dpToPx(16F))
                            view.gravity = Gravity.CENTER_HORIZONTAL
                            backupSaveBox.addView(view)
                            viewModel.working(false)
                        }
                        SaveState.Saving -> {
                            viewModel.working(true)
                            viewModel.backupSelectedTrees()
                        }
                    }
                    viewModel.updateState()
                }
                // Backup button
                viewModel.canBackup.observe(requireActivity()) {
                    backupSaveButton.isEnabled = it
                }
                backupSaveButton.setOnClickListener {
                    viewModel.saveState.value = SaveState.Saving
                }
                return root
            }
        }
    }

    /** List of recoverable backup files. */
    class RecoverFragment : Fragment() {
        private val viewModel: BackupViewModel by activityViewModels()
        private lateinit var binding: BackupRecoverFragmentBinding
        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
            binding = BackupRecoverFragmentBinding.inflate(layoutInflater)
            val boxView = binding.backupRecoverBox
            viewLifecycleOwner.lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    viewModel.recoverState.collect { state ->
                        when (state) {
                            RecoverState.Loading -> {
                                viewModel.working(true)
                                viewModel.loadBackupFiles()
                            }
                            is RecoverState.Success -> {
                                displayBackupFiles(state.files)
                                viewModel.working(false)
                            }
                            is RecoverState.Error -> {
                                displayMessage(boxView, state.message ?: getString(R.string.something_wrong))
                            }
                            is RecoverState.Notice -> {
                                displayMessage(boxView, state.message)
                            }
                        }
                        viewModel.updateState()
                    }
                }
            }
            return binding.root
        }

        /** Makes a message appear in the layout. */
        private fun displayMessage(layout: LinearLayout, message: String) {
            layout.removeAllViews()
            val textView = TextView(requireContext())
            textView.text = message
            textView.setPadding(U.dpToPx(16F))
            textView.gravity = Gravity.CENTER
            layout.addView(textView)
            viewModel.working(false)
        }

        private fun displayBackupFiles(files: List<BackupItem>) {
            val boxView = binding.backupRecoverBox
            boxView.removeAllViews()
            files.forEach { file ->
                val backupView = layoutInflater.inflate(R.layout.backup_file_item, boxView, false)
                boxView.addView(backupView)
                val nameView = backupView.findViewById<TextView>(R.id.backup_fileName)
                nameView.text = file.documentFile.name
                val titleView = backupView.findViewById<TextView>(R.id.backup_treeTitle)
                titleView.text = file.treeTitle
                backupView.findViewById<TextView>(R.id.backup_size).text = file.size
                backupView.findViewById<TextView>(R.id.backup_date).text = file.date
                // Detail view
                val detailView = backupView.findViewById<RelativeLayout>(R.id.backup_detail)
                val deleteButton = detailView.findViewById<Button>(R.id.backup_delete)
                deleteButton.setOnClickListener {
                    Util.confirmDelete(requireContext()) {
                        viewModel.deleteBackupFile(file.documentFile)
                        boxView.removeView(backupView)
                    }
                }
                val recoverButton = detailView.findViewById<Button>(R.id.backup_recover)
                if (file.invalid) {
                    nameView.setTextColor(getColor(requireContext(), R.color.gray_text))
                    titleView.setTextColor(getColor(requireContext(), R.color.gray_text))
                    recoverButton.visibility = View.GONE
                    val params = deleteButton.layoutParams as RelativeLayout.LayoutParams
                    params.addRule(RelativeLayout.ALIGN_PARENT_END)
                } else {
                    recoverButton.setOnClickListener {
                        viewModel.working(true)
                        lifecycleScope.launch(Dispatchers.IO) {
                            try {
                                val isValidBackup =
                                    ZipInputStream(requireContext().contentResolver.openInputStream(file.documentFile.uri)).use { zipInputStream ->
                                        generateSequence { zipInputStream.nextEntry }.filterNot { it.isDirectory }.map { it.name }
                                            .toList().containsAll(listOf("settings.json", "tree.json"))
                                    }
                                if (isValidBackup) {
                                    TreeUtil.unZipTree(requireContext(), null, file.documentFile.uri,
                                        { viewModel.listActualTrees() }, { viewModel.working(false) })
                                } else Util.toast(R.string.backup_invalid)
                            } catch (e: Exception) {
                                Util.toast(e.localizedMessage)
                            }
                            viewModel.working(false)
                        }
                    }
                }
                detailView.visibility = View.GONE
                // Shows or hides detail
                backupView.setOnClickListener {
                    boxView.children.filterNot { it == backupView }.forEach {
                        it.findViewById<RelativeLayout>(R.id.backup_detail).visibility = View.GONE
                        it.background = null
                    }
                    detailView.visibility = if (detailView.visibility == View.VISIBLE) {
                        backupView.background = null
                        View.GONE
                    } else {
                        backupView.background = getDrawable(requireContext(), R.drawable.generic_background)
                        View.VISIBLE
                    }
                }
            }
        }
    }
}
