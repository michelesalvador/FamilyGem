package app.familygem.merge

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.MenuProvider
import androidx.fragment.app.commit
import androidx.navigation.fragment.findNavController
import app.familygem.R
import app.familygem.databinding.MergeMatchFragmentBinding

/** Compares the people of the two trees, to decide whether they are the same person. */
class MatchFragment : BaseFragment(R.layout.merge_match_fragment) {

    private lateinit var binding: MergeMatchFragmentBinding

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = MergeMatchFragmentBinding.bind(view)
        binding.model = model
        // Sets the two tree titles
        binding.mergeLeftTitle.text = model.firstTree.title
        binding.mergeRightTitle.text = model.secondTree.title
        // Creates two person fragments
        requireActivity().supportFragmentManager.commit {
            add(R.id.leftPerson, PersonFragment::class.java, bundleOf("position" to false))
        }
        requireActivity().supportFragmentManager.commit {
            add(R.id.rightPerson, PersonFragment::class.java, bundleOf("position" to true))
        }
        // Buttons
        binding.mergeAbort.setOnClickListener {
            findNavController().popBackStack(R.id.choiceFragment, false)
            model.actualMatch = 0
        }
        binding.mergeMerge.setOnClickListener { nextMatch(Will.MERGE) }
        binding.mergeKeep.setOnClickListener { nextMatch(Will.KEEP) }
        // Accent color for selected option
        val destiny = model.personMatches[model.actualMatch].destiny
        val tint = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.accent))
        if (destiny == Will.MERGE) binding.mergeMerge.backgroundTintList = tint
        else if (destiny == Will.KEEP) binding.mergeKeep.backgroundTintList = tint
        // Options menu
        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menu.add(0, 0, 0, R.string.yes_to_all)
                menu.add(0, 1, 0, R.string.no_to_all)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                menuItem.itemId.let {
                    val will = if (it == 0) Will.MERGE else if (it == 1) Will.KEEP else return false
                    model.resolveRemainingMatches(will)
                    findNavController().navigate(R.id.merge_matchFragment_to_resultFragment)
                }
                return true
            }
        }, viewLifecycleOwner)
    }

    private fun nextMatch(will: Will) {
        if (model.nextMatch(will)) findNavController().navigate(R.id.merge_matchFragment_self)
        else findNavController().navigate(R.id.merge_matchFragment_to_resultFragment)
    }
}
