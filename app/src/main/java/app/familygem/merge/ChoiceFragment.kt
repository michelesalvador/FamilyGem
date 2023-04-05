package app.familygem.merge

import android.os.Bundle
import android.view.View
import android.widget.RadioButton
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.navigation.fragment.findNavController
import app.familygem.R
import app.familygem.TreesActivity
import app.familygem.databinding.MergeChoiceFragmentBinding

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
            treeView.findViewById<TextView>(R.id.merge_detail).text = TreesActivity.writeData(context, tree)
            treeView.setOnClickListener { model.setSecondTree(tree.id) }
            treeView.findViewById<RadioButton>(R.id.merge_radio).setOnClickListener { model.setSecondTree(tree.id) }
        }
        // Highlights the selected tree
        model.secondNum.observe(viewLifecycleOwner) {
            for (i in 0 until binding.mergeList.childCount) {
                val treeItem: View = binding.mergeList.getChildAt(i)
                val radioButton = treeItem.findViewById<RadioButton>(R.id.merge_radio)
                if (model.trees[i].id == it) {
                    treeItem.setBackgroundColor(ResourcesCompat.getColor(resources, R.color.accent_light, null))
                    radioButton.isChecked = true
                } else {
                    treeItem.setBackgroundColor(0x0000)
                    radioButton.isChecked = false
                }
            }
            binding.mergeNext.isEnabled = true
        }
        // Action button
        binding.mergeNext.setOnClickListener {
            model.findMatches()
            if (model.matches.any())
                findNavController().navigate(R.id.merge_choiceFragment_to_matchFragment)
            else
                findNavController().navigate(R.id.merge_choiceFragment_to_resultFragment)
        }
    }
}
