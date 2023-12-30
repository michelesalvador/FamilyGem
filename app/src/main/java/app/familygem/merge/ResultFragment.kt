package app.familygem.merge

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import app.familygem.BuildConfig
import app.familygem.Global
import app.familygem.R
import app.familygem.constant.Extra
import app.familygem.databinding.MergeResultFragmentBinding
import app.familygem.purchase.PurchaseActivity

class ResultFragment : BaseFragment(R.layout.merge_result_fragment) {

    private lateinit var bind: MergeResultFragmentBinding

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bind = MergeResultFragmentBinding.bind(view)
        setupTreeView(bind.mergeFirstTree.root, model.firstTree)
        setupTreeView(bind.mergeSecondTree.root, model.secondTree)
        // Setups radio buttons
        bind.mergeRadioAnnex.text = getString(R.string.merge_into, model.firstTree.title, model.secondTree.title)
        bind.mergeRetitle.setText("${model.firstTree.title} ${model.secondTree.title}")
        // Selecting a radio button
        bind.mergeRadiogroup.setOnCheckedChangeListener { _, checked ->
            if (checked == R.id.merge_radio_generate) bind.mergeRetitle.visibility = View.VISIBLE
            else bind.mergeRetitle.visibility = View.GONE
            bind.mergeButton.isEnabled = true
        }
        // Observable state
        model.state.observe(viewLifecycleOwner) {
            when (it) {
                State.ACTIVE -> {
                    // Disables all views and displays the progress wheel
                    bind.mergeRadioAnnex.isEnabled = false
                    bind.mergeRadioGenerate.isEnabled = false
                    bind.mergeRetitle.isEnabled = false
                    bind.mergeButton.isEnabled = false
                    activity?.findViewById<ProgressBar>(R.id.progress_wheel)?.visibility = View.VISIBLE
                }
                State.COMPLETE -> {
                    requireActivity().finish()
                    Toast.makeText(context, R.string.merge_complete, Toast.LENGTH_LONG).show()
                }
                else -> { // Normal state
                    bind.mergeRadioAnnex.isEnabled = true
                    bind.mergeRadioGenerate.isEnabled = true
                    bind.mergeRetitle.isEnabled = true
                    bind.mergeButton.isEnabled = bind.mergeRadioAnnex.isChecked || bind.mergeRadioGenerate.isChecked
                    activity?.findViewById<ProgressBar>(R.id.progress_wheel)?.visibility = View.GONE
                }
            }
            // Merge button
            bind.mergeButton.setOnClickListener {
                if (Global.settings.premium || BuildConfig.PASS_KEY.isEmpty()) {
                    if (bind.mergeRadioAnnex.isChecked)
                        model.performAnnexMerge(requireContext())
                    else if (bind.mergeRadioGenerate.isChecked)
                        model.performGenerateMerge(requireContext(), bind.mergeRetitle.text.toString())
                } else {
                    startActivity(Intent(context, PurchaseActivity::class.java).putExtra(Extra.STRING, R.string.merge_tree))
                }
            }
        }
    }
}
