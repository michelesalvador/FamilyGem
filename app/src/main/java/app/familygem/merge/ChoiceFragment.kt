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

/** First step of the merging process, where user can choose the second tree to merge to the first one. */
class ChoiceFragment : BaseFragment(R.layout.merge_choice_fragment) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        MergeChoiceFragmentBinding.bind(view).run {
            setupTreeView(mergeFirstTree.root, model.firstTree)
            // Places available trees into layout
            model.trees.forEach { tree ->
                val treeView = layoutInflater.inflate(R.layout.merge_tree_layout, mergeList, false)
                mergeList.addView(treeView)
                treeView.findViewById<TextView>(R.id.merge_title).text = tree.title
                treeView.findViewById<TextView>(R.id.merge_detail).text = tree.getBasicData()
                val click = { if (model.state.value == State.QUIET) model.setSecondTree(tree.id) }
                treeView.setOnClickListener { click() }
                treeView.findViewById<RadioButton>(R.id.merge_radio).setOnClickListener { click() }
            }
            // Highlights the selected tree
            model.secondNum.observe(viewLifecycleOwner) {
                for (i in 0 until mergeList.childCount) {
                    val treeItem = mergeList.getChildAt(i)
                    val radioButton = treeItem.findViewById<RadioButton>(R.id.merge_radio)
                    if (model.trees[i].id == it) {
                        treeItem.setBackgroundColor(getColor(resources, R.color.accent_light, null))
                        radioButton.isChecked = true
                    } else {
                        treeItem.setBackgroundColor(0x0000)
                        radioButton.isChecked = false
                    }
                }
                mergeSearchDuplicates.isEnabled = true
                mergeSkip.isEnabled = true
            }
            // Layout modified by state
            model.state.observe(viewLifecycleOwner) {
                when (it) {
                    State.ACTIVE -> {
                        for (i in 0 until mergeList.childCount) {
                            val treeView = mergeList.getChildAt(i)
                            treeView.findViewById<TextView>(R.id.merge_title).setTextColor(getColor(resources, R.color.gray_text, null))
                            treeView.findViewById<RadioButton>(R.id.merge_radio).isEnabled = false
                        }
                        mergeSearchDuplicates.isEnabled = false
                        mergeSkip.isEnabled = false
                        mergeWheel.root.visibility = View.VISIBLE
                    }
                    State.RESET -> {
                        for (i in 0 until mergeList.childCount) {
                            val treeView = mergeList.getChildAt(i)
                            treeView.findViewById<TextView>(R.id.merge_title).setTextColor(getColor(resources, R.color.text, null))
                            treeView.findViewById<RadioButton>(R.id.merge_radio).isEnabled = true
                        }
                        mergeSearchDuplicates.isEnabled = true
                        mergeSkip.isEnabled = true
                        mergeWheel.root.visibility = View.GONE
                        model.state.value = State.QUIET
                    }
                    State.COMPLETE -> {
                        if (model.personMatches.any())
                            findNavController().navigate(R.id.merge_choiceFragment_to_matchFragment)
                        else
                            findNavController().navigate(R.id.merge_choiceFragment_to_resultFragment)
                        model.state.value = State.QUIET
                    }
                    else -> {} // State.QUIET or null
                }
            }
            // Action buttons
            mergeSearchDuplicates.setOnClickListener {
                model.openTwoGedcom(true)
            }
            mergeSkip.setOnClickListener {
                model.openTwoGedcom(false)
            }
            model.state.value = State.QUIET
        }
    }
}
