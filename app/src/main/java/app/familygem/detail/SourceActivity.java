package app.familygem.detail;

import static app.familygem.Global.gc;

import android.content.Intent;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.cardview.widget.CardView;

import org.folg.gedcom.model.RepositoryRef;
import org.folg.gedcom.model.Source;

import app.familygem.DetailActivity;
import app.familygem.Memory;
import app.familygem.R;
import app.familygem.U;
import app.familygem.main.SourcesFragment;
import app.familygem.util.ChangeUtil;
import app.familygem.util.NoteUtil;
import app.familygem.visitor.ListOfSourceCitations;

public class SourceActivity extends DetailActivity {

    Source source;

    @Override
    protected void format() {
        setTitle(R.string.source);
        source = (Source)cast(Source.class);
        placeSlug("SOUR", source.getId());
        ListOfSourceCitations citations = new ListOfSourceCitations(gc, source.getId());
        source.putExtension("citaz", citations.list.size()); // For SourcesFragment
        int multiLine = InputType.TYPE_TEXT_FLAG_MULTI_LINE;
        place(getString(R.string.abbreviation), "Abbreviation");
        place(getString(R.string.title), "Title", true, multiLine);
        place(getString(R.string.type), "Type", false, 0); // '_TYPE' tag not GEDCOM standard
        place(getString(R.string.author), "Author", true, multiLine);
        place(getString(R.string.publication_facts), "PublicationFacts", true, multiLine);
        place(getString(R.string.date), "Date", false, 0); // Here it's not GEDCOM standard, should be in SOUR.DATA.EVEN.DATE
        place(getString(R.string.text), "Text", true, multiLine);
        place(getString(R.string.call_number), "CallNumber", false, 0); // CALN tag should be in the SOURCE_REPOSITORY_CITATION per GEDCOM specs
        place(getString(R.string.italic), "Italic", false, 0); // _italic indicates source title to be in italics
        place(getString(R.string.media_type), "MediaType", false, 0); // MEDI tag, should be in SOURCE_REPOSITORY_CITATION
        place(getString(R.string.parentheses), "Paren", false, 0); // _PAREN indicates source facts are to be enclosed in parentheses
        place(getString(R.string.reference_number), "ReferenceNumber"); // REFN tag
        place(getString(R.string.rin), "Rin", false, 0);
        place(getString(R.string.user_id), "Uid", false, 0);
        placeExtensions(source);

        // Places the citation to the repository
        if (source.getRepositoryRef() != null) {
            View refView = LayoutInflater.from(this).inflate(R.layout.source_citation_layout, box, false);
            box.addView(refView);
            refView.setBackgroundColor(getResources().getColor(R.color.repository_citation));
            final RepositoryRef repositoryRef = source.getRepositoryRef();
            if (repositoryRef.getRepository(gc) != null) {
                ((TextView)refView.findViewById(R.id.source_text)).setText(repositoryRef.getRepository(gc).getName());
                ((CardView)refView.findViewById(R.id.sourceCitation)).setCardBackgroundColor(getResources().getColor(R.color.repository));
            } else refView.findViewById(R.id.sourceCitation).setVisibility(View.GONE);
            String txt = "";
            if (repositoryRef.getValue() != null) txt += repositoryRef.getValue() + "\n";
            if (repositoryRef.getCallNumber() != null) txt += repositoryRef.getCallNumber() + "\n";
            if (repositoryRef.getMediaType() != null) txt += repositoryRef.getMediaType() + "\n";
            TextView textView = refView.findViewById(R.id.sourceCitation_text);
            if (txt.isEmpty()) textView.setVisibility(View.GONE);
            else textView.setText(txt.substring(0, txt.length() - 1));
            NoteUtil.INSTANCE.placeNotes(refView.findViewById(R.id.sourceCitation_box), repositoryRef, false);
            refView.setOnClickListener(v -> {
                Memory.add(repositoryRef);
                startActivity(new Intent(SourceActivity.this, RepositoryRefActivity.class));
            });
            registerForContextMenu(refView);
            refView.setTag(R.id.tag_object, repositoryRef); // For the context menu
        }
        NoteUtil.INSTANCE.placeNotes(box, source);
        U.placeMedia(box, source, true);
        ChangeUtil.INSTANCE.placeChangeDate(box, source.getChange());
        if (!citations.list.isEmpty())
            placeCabinet(citations.getProgenitors(), R.string.cited_by);
    }

    @Override
    public void delete() {
        ChangeUtil.INSTANCE.updateChangeDate(SourcesFragment.deleteSource(source));
    }
}
