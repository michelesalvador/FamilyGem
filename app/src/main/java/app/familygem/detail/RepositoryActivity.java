package app.familygem.detail;

import android.text.InputType;

import org.folg.gedcom.model.Repository;
import org.folg.gedcom.model.Source;

import java.util.ArrayList;
import java.util.List;

import app.familygem.DetailActivity;
import app.familygem.Global;
import app.familygem.R;
import app.familygem.main.RepositoriesFragment;
import app.familygem.util.ChangeUtil;
import app.familygem.util.NoteUtil;

public class RepositoryActivity extends DetailActivity {

    Repository repository;

    @Override
    protected void format() {
        setTitle(R.string.repository);
        repository = (Repository)cast(Repository.class);
        placeSlug("REPO", repository.getId());
        place(getString(R.string.value), "Value", false, 0); // Not really GEDCOM standard
        place(getString(R.string.name), "Name");
        place(getString(R.string.address), repository.getAddress());
        place(getString(R.string.www), "Www", true, InputType.TYPE_CLASS_TEXT);
        place(getString(R.string.email), "Email", true, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        place(getString(R.string.telephone), "Phone", true, InputType.TYPE_CLASS_PHONE);
        place(getString(R.string.fax), "Fax", true, InputType.TYPE_CLASS_PHONE);
        place(getString(R.string.rin), "Rin", false, 0);
        placeExtensions(repository);
        NoteUtil.INSTANCE.placeNotes(box, repository);
        ChangeUtil.INSTANCE.placeChangeDate(box, repository.getChange());

        // Collects and displays the sources citing this Repository
        List<Source> citingSources = new ArrayList<>();
        for (Source source : Global.gc.getSources())
            if (source.getRepositoryRef() != null && source.getRepositoryRef().getRef() != null
                    && source.getRepositoryRef().getRef().equals(repository.getId()))
                citingSources.add(source);
        if (!citingSources.isEmpty())
            placeCabinet(citingSources.toArray(), R.string.sources);
    }

    @Override
    public void delete() {
        ChangeUtil.INSTANCE.updateChangeDate((Object[])RepositoriesFragment.delete(repository));
    }
}
