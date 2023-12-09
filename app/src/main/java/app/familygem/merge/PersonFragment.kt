package app.familygem.merge

import android.os.Build
import android.os.Bundle
import android.text.Html
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import app.familygem.DetailActivity
import app.familygem.GedcomDateConverter
import app.familygem.ProfileFactsFragment
import app.familygem.R
import app.familygem.U
import app.familygem.constant.Gender
import app.familygem.databinding.MergePersonFragmentBinding
import app.familygem.util.FileUtil
import org.folg.gedcom.model.Gedcom
import org.folg.gedcom.model.Person

class PersonFragment : Fragment(R.layout.merge_person_fragment) {

    private lateinit var binding: MergePersonFragmentBinding
    private val model: MergeViewModel by activityViewModels()
    private lateinit var person: Person
    var data = ""

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
        FileUtil.selectMainImage(person, binding.mergeImage, 0, gedcom, treeId)
        // Death ribbon
        binding.mergeMourn.visibility = if (U.isDead(person)) View.VISIBLE else View.GONE
        // Personal events
        for (event in person.eventsFacts) {
            if (event.tag == "SEX") continue
            data += "<b>${ProfileFactsFragment.writeEventTitle(event)}</b><br>"
            if (event.value != null) {
                data += if (event.value == "Y") "${getString(R.string.yes)} "
                else "${event.value} "
            }
            if (event.date != null) data += "${GedcomDateConverter(event.date).writeDateLong()} "
            if (event.place != null) data += "${event.place} "
            if (event.address != null) data += "${DetailActivity.writeAddress(event.address, true)} "
            if (event.cause != null) data += event.cause
            data += "<br>"
        }
        if (data.any()) data = data.substring(0, data.length - 4)
        binding.mergeData.text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            Html.fromHtml(data, Html.FROM_HTML_MODE_COMPACT)
        else
            Html.fromHtml(data)
    }

    fun getName(): String {
        return if (person.names.any())
            U.firstAndLastName(person.names[0], " ")
        else "[${getString(R.string.no_name)}]"
    }
}
