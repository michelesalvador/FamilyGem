package app.familygem.main

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import app.familygem.Memory
import app.familygem.R
import app.familygem.detail.FamilyActivity

/**
 * RecyclerView adapter for FamiliesFragment.
 */
class FamiliesAdapter(private val fragment: FamiliesFragment) : RecyclerView.Adapter<FamiliesAdapter.FamilyHolder>() {

    class FamilyHolder(val view: View) : RecyclerView.ViewHolder(view) {
        val infoView: TextView = view.findViewById(R.id.family_info)
        val parentsView: TextView = view.findViewById(R.id.family_parents)
        val childrenView: TextView = view.findViewById(R.id.family_children)
        val strutView: View = view.findViewById(R.id.family_strut)
    }

    override fun onCreateViewHolder(group: ViewGroup, type: Int): FamilyHolder {
        val view = LayoutInflater.from(group.context).inflate(R.layout.families_item, group, false)
        fragment.registerForContextMenu(view)
        return FamilyHolder(view)
    }

    override fun onBindViewHolder(holder: FamilyHolder, position: Int) {
        val wrapper = fragment.filteredFamilies[position]
        val info = when (fragment.order) {
            FamiliesFragment.Order.ID_ASC, FamiliesFragment.Order.ID_DESC -> wrapper.id
            FamiliesFragment.Order.SURNAME_ASC, FamiliesFragment.Order.SURNAME_DESC -> wrapper.originalSurname
            FamiliesFragment.Order.MEMBERS_ASC, FamiliesFragment.Order.MEMBERS_DESC -> wrapper.members.toString()
            else -> null
        }
        if (info == null) holder.infoView.visibility = View.GONE
        else {
            holder.infoView.text = info
            holder.infoView.visibility = View.VISIBLE
        }
        holder.parentsView.text = wrapper.parents
        if (wrapper.children.isEmpty()) {
            holder.strutView.visibility = View.GONE
            holder.childrenView.visibility = View.GONE
        } else {
            holder.childrenView.text = wrapper.children
            holder.strutView.visibility = View.VISIBLE
            holder.childrenView.visibility = View.VISIBLE
        }
        holder.view.setOnClickListener {
            Memory.setLeader(wrapper.family)
            holder.view.context.startActivity(Intent(holder.view.context, FamilyActivity::class.java))
        }
        holder.view.setTag(R.id.tag_family, wrapper.family) // For 'Delete' in context menu of FamiliesFragment
    }

    override fun getItemCount() = fragment.filteredFamilies.size
}
