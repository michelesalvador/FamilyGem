package app.familygem.merge

import android.os.Build
import android.os.Bundle
import android.text.Html
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import app.familygem.F
import app.familygem.GedcomDateConverter
import app.familygem.R
import app.familygem.U
import app.familygem.constant.Gender
import app.familygem.databinding.MergePersonFragmentBinding
import org.folg.gedcom.model.Gedcom
import org.folg.gedcom.model.Person

class PersonFragment : Fragment(R.layout.merge_person_fragment) {

    private lateinit var binding: MergePersonFragmentBinding
    private val model: MergeViewModel by activityViewModels()
    private lateinit var person: Person

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val position = requireArguments().getBoolean("position")
        person = model.getPerson(position)

        binding = MergePersonFragmentBinding.bind(view)
        binding.person = this

        // Border
        if (Gender.isMale(person)) binding.mergeBorder.setBackgroundResource(R.drawable.casella_bordo_maschio)
        else if (Gender.isFemale(person)) binding.mergeBorder.setBackgroundResource(R.drawable.casella_bordo_femmina)
        // Image
        val gedcom: Gedcom = if (position) model.secondGedcom else model.firstGedcom
        val treeId: Int = if (position) model.secondNum.value!! else model.firstNum
        F.showMainImageForPerson(gedcom, treeId, person, binding.mergeImage)
        // Death ribbon
        binding.mergeMourn.visibility = if (U.isDead(person)) View.VISIBLE else View.GONE
        // Personal events
        var str = ""
        for (event in person.eventsFacts) {
            if (event.tag == "SEX") continue
            str += "<b>${event.displayType}</b><br>"
            if (event.value != null) str += "${event.value}\n"
            if (event.date != null) str += "${GedcomDateConverter(event.date).writeDateLong()}\n"
            if (event.place != null) str += "${event.place}\n"
            str += "<br>"
        }
        if (str.any()) str = str.substring(0, str.length - 4)
        binding.mergeData.text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            Html.fromHtml(str, Html.FROM_HTML_MODE_COMPACT)
        else
            Html.fromHtml(str)
    }

    fun getName(): String {
        return U.firstAndLastName(person.names[0], " ")
    }
}
