package app.familygem.util

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.res.ResourcesCompat
import app.familygem.Memory
import app.familygem.R
import app.familygem.detail.RepositoryActivity
import org.folg.gedcom.model.Repository

object RepositoryUtil {

    /**
     * Creates into layout a clickable repository and returns the created view.
     */
    fun placeRepository(layout: LinearLayout, repo: Repository): View {
        val context = layout.context
        val repoView = LayoutInflater.from(context).inflate(R.layout.source_layout, layout, false)
        layout.addView(repoView)
        repoView.findViewById<TextView>(R.id.source_text).text = repo.name
        (repoView as CardView).setCardBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.repository, null))
        repoView.setOnClickListener {
            Memory.setLeader(repo)
            context.startActivity(Intent(context, RepositoryActivity::class.java))
        }
        return repoView
    }
}
