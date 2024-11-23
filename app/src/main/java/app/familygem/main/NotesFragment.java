package app.familygem.main;

import static app.familygem.Global.gc;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.folg.gedcom.model.Note;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import app.familygem.Memory;
import app.familygem.R;
import app.familygem.U;
import app.familygem.constant.Choice;
import app.familygem.constant.Extra;
import app.familygem.detail.NoteActivity;
import app.familygem.util.NoteUtil;
import app.familygem.util.TreeUtil;
import app.familygem.util.Util;
import app.familygem.visitor.FindStack;
import app.familygem.visitor.NoteList;
import kotlin.Unit;

public class NotesFragment extends BaseFragment implements NotesAdapter.ItemClickListener {

    private List<Note> allNotes = Collections.emptyList(); // Initialized to avoid crash in case Global.gc is null
    private NotesAdapter adapter;
    private SearchView searchView;

    public static List<Note> getAllNotes(boolean sharedOnly) {
        // Shared notes
        List<Note> sharedNotes = gc.getNotes();
        ArrayList<Note> noteList = new ArrayList<>(sharedNotes);
        // Inline notes
        if (!sharedOnly) {
            NoteList noteVisitor = new NoteList();
            gc.accept(noteVisitor);
            noteList.addAll(noteVisitor.noteList);
        }
        return noteList;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle bundle) {
        super.onCreateView(inflater, container, bundle);
        View view = inflater.inflate(R.layout.recyclerview, container, false);
        RecyclerView recyclerView = view.findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        boolean sharedOnly = getActivity().getIntent().getBooleanExtra(Choice.NOTE, false);
        adapter = new NotesAdapter(getContext(), allNotes, sharedOnly);
        adapter.setClickListener(this);
        recyclerView.setAdapter(adapter);
        registerForContextMenu(recyclerView);
        setupFastScroller(recyclerView);
        view.findViewById(R.id.fab).setOnClickListener(v -> NoteUtil.INSTANCE.createSharedNote(getContext(), null));
        return view;
    }

    @Override
    public void showContent() {
        boolean sharedOnly = getActivity().getIntent().getBooleanExtra(Choice.NOTE, false);
        allNotes = getAllNotes(sharedOnly);
        adapter.noteList = allNotes;
        adapter.getFilter().filter(searchView != null ? searchView.getQuery() : "");
        adapter.notifyDataSetChanged();
    }

    @Override
    public boolean isSearching() {
        return searchView != null && searchView.getQuery().length() > 0;
    }

    @Override
    public void onPause() {
        super.onPause();
        // Resets the extra on leaving the activity without choosing a shared note
        requireActivity().getIntent().removeExtra(Choice.NOTE);
    }

    @Override
    public void onItemClick(View view, int position) {
        Note note = adapter.getItem(position);
        // Returns the ID of a note to ProfileActivity and DetailActivity
        if (getActivity().getIntent().getBooleanExtra(Choice.NOTE, false)) {
            Intent intent = new Intent();
            intent.putExtra(Extra.NOTE_ID, note.getId());
            getActivity().setResult(AppCompatActivity.RESULT_OK, intent);
            getActivity().finish();
        } else { // Opens the note detail
            Intent intent = new Intent(getContext(), NoteActivity.class);
            if (note.getId() != null) { // Shared note
                Memory.setLeader(note);
            } else { // Simple note
                new FindStack(gc, note, true);
                intent.putExtra("fromNotes", true);
            }
            getContext().startActivity(intent);
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        if (item.getGroupId() == 4 && item.getItemId() == 0) { // Delete
            Util.INSTANCE.confirmDelete(requireContext(), () -> {
                Object[] leaders = U.deleteNote(adapter.selectedNote);
                TreeUtil.INSTANCE.save(false, leaders);
                showContent();
                ((MainActivity)requireActivity()).refreshInterface();
                return Unit.INSTANCE;
            });
            return true;
        }
        return false;
    }

    @Override
    public void updateToolbar(ActionBar bar, Menu menu, MenuInflater inflater) {
        bar.setTitle(allNotes.size() + " " + Util.INSTANCE.caseString(allNotes.size() == 1 ? R.string.note : R.string.notes));
        if (allNotes.size() > 1) {  // Search inside notes
            inflater.inflate(R.menu.search, menu);
            searchView = (SearchView)menu.findItem(R.id.search_item).getActionView();
            searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                @Override
                public boolean onQueryTextChange(String query) {
                    adapter.getFilter().filter(query);
                    return true;
                }

                @Override
                public boolean onQueryTextSubmit(String q) {
                    searchView.clearFocus();
                    return false;
                }
            });
        }
    }
}
