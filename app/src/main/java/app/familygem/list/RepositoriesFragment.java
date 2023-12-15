package app.familygem.list;

import static app.familygem.Global.gc;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import org.folg.gedcom.model.Gedcom;
import org.folg.gedcom.model.Repository;
import org.folg.gedcom.model.RepositoryRef;
import org.folg.gedcom.model.Source;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import app.familygem.Global;
import app.familygem.Memory;
import app.familygem.R;
import app.familygem.U;
import app.familygem.constant.Choice;
import app.familygem.detail.RepositoryActivity;
import app.familygem.util.TreeUtils;
import app.familygem.util.Util;

/**
 * Fragment with a list of all repositories of the tree.
 */
public class RepositoriesFragment extends Fragment {

    LinearLayout layout;
    int order; // For sorting

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle bundle) {
        View view = inflater.inflate(R.layout.scrollview, container, false);
        layout = view.findViewById(R.id.scrollview_layout);
        view.findViewById(R.id.fab).setOnClickListener(v -> newRepository(getContext(), null));
        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        if (gc != null) {
            List<Repository> repos = gc.getRepositories();
            ((AppCompatActivity)getActivity()).getSupportActionBar().setTitle(repos.size() + " "
                    + Util.INSTANCE.caseString(repos.size() == 1 ? R.string.repository : R.string.repositories));
            if (repos.size() > 1)
                setHasOptionsMenu(true);
            Collections.sort(repos, (r1, r2) -> {
                switch (order) {
                    case 1: // Ordina per id
                        return U.extractNum(r1.getId()) - U.extractNum(r2.getId());
                    case 2: // Ordine alfabetico
                        return r1.getName().compareToIgnoreCase(r2.getName());
                    case 3: // Ordina per numero di fonti
                        return countSources(gc, r2) - countSources(gc, r1);
                    default:
                        return 0;
                }
            });
            layout.removeAllViews();
            for (Repository repo : repos) {
                View repoView = getLayoutInflater().inflate(R.layout.scrollview_item, layout, false);
                layout.addView(repoView);
                ((TextView)repoView.findViewById(R.id.item_name)).setText(repo.getName());
                ((TextView)repoView.findViewById(R.id.item_num)).setText(String.valueOf(countSources(gc, repo)));
                repoView.setOnClickListener(v -> {
                    if (getActivity().getIntent().getBooleanExtra(Choice.REPOSITORY, false)) {
                        Intent intent = new Intent();
                        intent.putExtra("repoId", repo.getId());
                        getActivity().setResult(Activity.RESULT_OK, intent);
                        getActivity().finish();
                    } else {
                        Memory.setLeader(repo);
                        startActivity(new Intent(getContext(), RepositoryActivity.class));
                    }
                });
                registerForContextMenu(repoView);
                repoView.setTag(repo);

                // Extension "fonti" is removed from version 0.9.1
                if (repo.getExtension("fonti") != null)
                    repo.putExtension("fonti", null);
                if (repo.getExtensions().isEmpty())
                    repo.setExtensions(null);
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        getActivity().getIntent().removeExtra(Choice.REPOSITORY);
    }

    // Count how many sources are present in a repository
    static int countSources(Gedcom gedcom, Repository repo) {
        int num = 0;
        for (Source source : gedcom.getSources()) {
            RepositoryRef repoRef = source.getRepositoryRef();
            if (repoRef != null && repoRef.getRef() != null
                    && repoRef.getRef().equals(repo.getId()))
                num++;
        }
        return num;
    }

    /**
     * Creates a new repository, optionally linking a source to it.
     */
    public static void newRepository(Context context, Source source) {
        Repository repo = new Repository();
        repo.setId(U.newID(gc, Repository.class));
        repo.setName("");
        gc.addRepository(repo);
        if (source != null) {
            RepositoryRef repoRef = new RepositoryRef();
            repoRef.setRef(repo.getId());
            source.setRepositoryRef(repoRef);
        }
        TreeUtils.INSTANCE.save(true, repo);
        Memory.setLeader(repo);
        context.startActivity(new Intent(context, RepositoryActivity.class));
    }

    /* Elimina l'archivio e i ref dalle fonti in cui è citato l'archivio
        Restituisce un array delle Source modificate
    Secondo le specifiche Gedcom 5.5, la libreria FS e Family Historian una SOUR prevede un solo Ref a un REPO
    Invece secondo Gedcom 5.5.1 può avere molteplici Ref ad archivi */
    public static Source[] delete(Repository repo) {
        Set<Source> sources = new HashSet<>();
        for (Source sour : gc.getSources())
            if (sour.getRepositoryRef() != null && sour.getRepositoryRef().getRef().equals(repo.getId())) {
                sour.setRepositoryRef(null);
                sources.add(sour);
            }
        gc.getRepositories().remove(repo);
        Memory.setInstanceAndAllSubsequentToNull(repo);
        return sources.toArray(new Source[0]);
    }

    // overflow menu in toolbar
    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        SubMenu subMenu = menu.addSubMenu(R.string.order_by);
        if (Global.settings.expert)
            subMenu.add(0, 1, 0, R.string.id);
        subMenu.add(0, 2, 0, R.string.name);
        subMenu.add(0, 3, 0, R.string.sources_number);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case 1:
                order = 1;
                break;
            case 2:
                order = 2;
                break;
            case 3:
                order = 3;
                break;
            default:
                return false;
        }
        onStart();
        return true;
    }

    // Menu contestuale
    Repository repository;

    @Override
    public void onCreateContextMenu(ContextMenu menu, View vista, ContextMenu.ContextMenuInfo info) {
        repository = (Repository)vista.getTag();
        menu.add(0, 0, 0, R.string.delete);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        if (item.getItemId() == 0) {
            Source[] fonti = delete(repository);
            TreeUtils.INSTANCE.save(false, (Object[])fonti);
            getActivity().recreate();
            return true;
        }
        return false;
    }
}
