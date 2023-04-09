package app.familygem.merge

import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.commit
import androidx.navigation.fragment.findNavController
import app.familygem.R
import app.familygem.databinding.MergeMatchFragmentBinding

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
        binding.mergeKeep.setOnClickListener { nextMatch(Will.KEEP) }
        binding.mergeMerge.setOnClickListener { nextMatch(Will.MERGE) }
        // Accent color for selected option
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val destiny = model.matches[model.actualMatch].destiny
            val tint = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.accent))
            if (destiny == Will.KEEP) binding.mergeKeep.backgroundTintList = tint
            else if (destiny == Will.MERGE) binding.mergeMerge.backgroundTintList = tint
        }
    }

    private fun nextMatch(will: Will) {
        if (model.nextMatch(will)) findNavController().navigate(R.id.merge_matchFragment_self)
        else findNavController().navigate(R.id.merge_matchFragment_to_resultFragment)
    }
}
