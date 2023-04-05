package app.familygem.merge

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import app.familygem.Global
import app.familygem.PurchaseActivity
import app.familygem.R
import app.familygem.constant.Extra
import app.familygem.databinding.MergeResultFragmentBinding

class ResultFragment : BaseFragment(R.layout.merge_result_fragment) {

    private lateinit var binding: MergeResultFragmentBinding

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = MergeResultFragmentBinding.bind(view)
        setupTreeView(binding.mergeFirstTree, model.firstTree)
        setupTreeView(binding.mergeSecondTree, model.secondTree)
        // Setups radio buttons
        binding.mergeRadioAnnex.text = getString(R.string.merge_into, model.firstTree.title, model.secondTree.title)
        binding.mergeRetitle.setText("${model.firstTree.title} ${model.secondTree.title}")
        // Selecting a radio button
        binding.mergeRadiogroup.setOnCheckedChangeListener { _, checked ->
            if (checked == R.id.merge_radio_generate) binding.mergeRetitle.visibility = View.VISIBLE
            else binding.mergeRetitle.visibility = View.GONE
            binding.mergeButton.isEnabled = true
        }
        // Merge thread
        val mergeThread = object : Thread() {
            override fun run() {
                if (binding.mergeRadioAnnex.isChecked)
                    model.performAnnexMerge(requireContext())
                else if (binding.mergeRadioGenerate.isChecked)
                    model.performGenerateMerge(requireContext(), binding.mergeRetitle.text.toString())
                Global.gc = if (Global.settings.openTree == model.firstNum) model.firstGedcom else null
                activity?.runOnUiThread {
                    requireActivity().finish()
                    Toast.makeText(context, R.string.merge_complete, Toast.LENGTH_LONG).show()
                }
            }
        }
        // Merge button
        binding.mergeButton.setOnClickListener {
            if (Global.settings.premium) {
                showMergingState()
                mergeThread.start()
            } else {
                startActivity(Intent(context, PurchaseActivity::class.java).putExtra(Extra.STRING, R.string.merge_tree))
            }
        }
    }

    /**
     * Disables all views and displays the progress wheel.
     */
    private fun showMergingState() {
        binding.mergeRadioAnnex.isEnabled = false
        binding.mergeRadioGenerate.isEnabled = false
        binding.mergeRetitle.isEnabled = false
        binding.mergeButton.isEnabled = false
        activity?.findViewById<ProgressBar>(R.id.progress_wheel)?.visibility = View.VISIBLE
    }
}
