package app.familygem.merge

import android.os.Bundle
import android.view.View
import android.widget.RadioButton
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat.getColor
import androidx.navigation.fragment.findNavController
import app.familygem.R
import app.familygem.databinding.MergeChoiceFragmentBinding
import app.familygem.util.getBasicData

/**
 * First fragment of the merging process, where user can choose the second tree to merge to the first one.
 */
class ChoiceFragment : BaseFragment(R.layout.merge_choice_fragment) {

    private lateinit var binding: MergeChoiceFragmentBinding

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = MergeChoiceFragmentBinding.bind(view)
        setupTreeView(binding.mergeFirstTree, model.firstTree)
        // Places available trees into layout
        for (tree in model.trees) {
            val treeView = layoutInflater.inflate(R.layout.merge_tree_layout, binding.mergeList, false)
            binding.mergeList.addView(treeView)
            treeView.findViewById<TextView>(R.id.merge_title).text = tree.title
            treeView.findViewById<TextView>(R.id.merge_detail).text = tree.getBasicData()
            fun click() {
                if (model.state.value == State.QUIET) model.setSecondTree(tree.id)
            }
            treeView.setOnClickListener { click() }
            treeView.findViewById<RadioButton>(R.id.merge_radio).setOnClickListener { click() }
        }
        // Highlights the selected tree
        model.secondNum.observe(viewLifecycleOwner) {
            for (i in 0 until binding.mergeList.childCount) {
                val treeItem: View = binding.mergeList.getChildAt(i)
                val radioButton = treeItem.findViewById<RadioButton>(R.id.merge_radio)
                if (model.trees[i].id == it) {
                    treeItem.setBackgroundColor(getColor(resources, R.color.accent_light, null))
                    radioButton.isChecked = true
                } else {
                    treeItem.setBackgroundColor(0x0000)
                    radioButton.isChecked = false
                }
            }
            binding.mergeNext.isEnabled = true
        }
        // Searching state
        model.state.observe(viewLifecycleOwner) {
            if (it == State.ACTIVE) {
                for (i in 0 until binding.mergeList.childCount) {
                    val treeView: View = binding.mergeList.getChildAt(i)
                    treeView.findViewById<TextView>(R.id.merge_title).setTextColor(getColor(resources, R.color.gray_text, null))
                    treeView.findViewById<RadioButton>(R.id.merge_radio).isEnabled = false
                }
                binding.mergeNext.isEnabled = false
                binding.mergeWheel.visibility = View.VISIBLE
            } else if (it == State.RESET) {
                for (i in 0 until binding.mergeList.childCount) {
                    val treeView: View = binding.mergeList.getChildAt(i)
                    treeView.findViewById<TextView>(R.id.merge_title).setTextColor(getColor(resources, R.color.text, null))
                    treeView.findViewById<RadioButton>(R.id.merge_radio).isEnabled = true
                }
                binding.mergeNext.isEnabled = true
                binding.mergeWheel.visibility = View.GONE
                model.state.value = State.QUIET
            } else if (it == State.COMPLETE) {
                if (model.personMatches.any())
                    findNavController().navigate(R.id.merge_choiceFragment_to_matchFragment)
                else
                    findNavController().navigate(R.id.merge_choiceFragment_to_resultFragment)
                model.state.value = State.QUIET
            }
        }
        // Action button
        binding.mergeNext.setOnClickListener {
            model.findMatches()
        }
        model.state.value = State.QUIET
    }
}
