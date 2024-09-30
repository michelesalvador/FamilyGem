package app.familygem.main;

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
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.TextView;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.SearchView;
import androidx.recyclerview.widget.RecyclerView;

import org.folg.gedcom.model.EventFact;
import org.folg.gedcom.model.Family;
import org.folg.gedcom.model.Name;
import org.folg.gedcom.model.Note;
import org.folg.gedcom.model.NoteContainer;
import org.folg.gedcom.model.Person;
import org.folg.gedcom.model.Source;
import org.folg.gedcom.model.SourceCitation;
import org.folg.gedcom.model.SourceCitationContainer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import app.familygem.Global;
import app.familygem.Memory;
import app.familygem.R;
import app.familygem.U;
import app.familygem.constant.Choice;
import app.familygem.constant.Extra;
import app.familygem.detail.SourceActivity;
import app.familygem.util.TreeUtil;
import app.familygem.util.Util;
import app.familygem.visitor.ListOfSourceCitations;

/**
 * List of all sources of the tree.
 */
public class SourcesFragment extends BaseFragment {

    private List<Source> sourceList = Collections.emptyList();
    private SourcesAdapter adapter;
    private SearchView searchView;
    private int order;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle bundle) {
        super.onCreateView(inflater, container, bundle);
        View view = inflater.inflate(R.layout.recyclerview, container, false);
        RecyclerView recyclerView = view.findViewById(R.id.recycler_view);
        adapter = new SourcesAdapter();
        recyclerView.setAdapter(adapter);
        setupFastScroller(recyclerView);
        view.findViewById(R.id.fab).setOnClickListener(v -> newSource(getContext(), null));
        return view;
    }

    @Override
    public void showContent() {
        sourceList = gc.getSources();
        adapter.getFilter().filter(searchView != null ? searchView.getQuery() : "");
        adapter.notifyDataSetChanged();
    }

    @Override
    public boolean isSearching() {
        return searchView != null && searchView.getQuery().length() > 0;
    }

    public class SourcesAdapter extends RecyclerView.Adapter<SourceHolder> implements Filterable {
        @Override
        public SourceHolder onCreateViewHolder(ViewGroup parent, int type) {
            View sourceView = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.source_item, parent, false);
            registerForContextMenu(sourceView);
            return new SourceHolder(sourceView);
        }

        @Override
        public void onBindViewHolder(SourceHolder holder, int position) {
            Source source = sourceList.get(position);
            holder.idView.setText(source.getId());
            holder.idView.setVisibility(order == 1 || order == 2 ? View.VISIBLE : View.GONE);
            holder.titleView.setText(titoloFonte(source));
            Object volte = source.getExtension("citaz");
            // Conta delle citazioni con il mio metodo
            if (volte == null) {
                volte = quanteCitazioni(source);
                source.putExtension("citaz", volte);
            }
            holder.numView.setText(String.valueOf(volte));
        }

        // Filtra i titoli delle fonti in base alle parole cercate
        @Override
        public Filter getFilter() {
            return new Filter() {
                @Override
                protected FilterResults performFiltering(CharSequence charSequence) {
                    String query = charSequence.toString();
                    if (query.isEmpty()) {
                        sourceList = gc.getSources();
                    } else {
                        List<Source> filteredList = new ArrayList<>();
                        for (Source source : gc.getSources()) {
                            if (titoloFonte(source).toLowerCase().contains(query.toLowerCase())) {
                                filteredList.add(source);
                            }
                        }
                        sourceList = filteredList;
                    }
                    ordinaFonti(); // Riducendo la query riordina quelli che appaiono
                    FilterResults filterResults = new FilterResults();
                    filterResults.values = sourceList;
                    return filterResults;
                }

                @Override
                protected void publishResults(CharSequence cs, FilterResults fr) {
                    notifyDataSetChanged();
                }
            };
        }

        @Override
        public int getItemCount() {
            return sourceList.size();
        }
    }

    class SourceHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        TextView idView;
        TextView titleView;
        TextView numView;

        SourceHolder(View view) {
            super(view);
            idView = view.findViewById(R.id.source_id);
            titleView = view.findViewById(R.id.source_title);
            numView = view.findViewById(R.id.source_num);
            view.setOnClickListener(this);
        }

        @Override
        public void onClick(View v) {
            // Returns a source ID to ProfileActivity or to DetailActivity
            if (getActivity().getIntent().getBooleanExtra(Choice.SOURCE, false)) {
                Intent intent = new Intent();
                intent.putExtra(Extra.SOURCE_ID, idView.getText().toString());
                getActivity().setResult(Activity.RESULT_OK, intent);
                getActivity().finish();
            } else { // Regular source opening
                Source source = gc.getSource(idView.getText().toString());
                Memory.setLeader(source);
                startActivity(new Intent(getContext(), SourceActivity.class));
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        getActivity().getIntent().removeExtra(Choice.SOURCE);
    }

    // Mette in ordine le fonti secondo uno dei criteri
    // L'ordine poi diventa permanente nel Json
    private void ordinaFonti() {
        if (order > 0) {
            if (order == 5 || order == 6) {
                for (Source fonte : sourceList) {
                    if (fonte.getExtension("citaz") == null)
                        fonte.putExtension("citaz", quanteCitazioni(fonte));
                }
            }
            Collections.sort(sourceList, (f1, f2) -> {
                switch (order) {
                    case 1:    // Ordina per id numerico
                        return U.extractNum(f1.getId()) - U.extractNum(f2.getId());
                    case 2:
                        return U.extractNum(f2.getId()) - U.extractNum(f1.getId());
                    case 3:    // Ordine alfabeto dei titoli
                        return titoloFonte(f1).compareToIgnoreCase(titoloFonte(f2));
                    case 4:
                        return titoloFonte(f2).compareToIgnoreCase(titoloFonte(f1));
                    case 5:    // Ordina per numero di citazioni
                        return U.castJsonInt(f1.getExtension("citaz")) - U.castJsonInt(f2.getExtension("citaz"));
                    case 6:
                        return U.castJsonInt(f2.getExtension("citaz")) - U.castJsonInt(f1.getExtension("citaz"));
                }
                return 0;
            });
        }
    }

    // Restituisce il titolo della fonte
    public static String titoloFonte(Source fon) {
        String tit = "";
        if (fon != null)
            if (fon.getAbbreviation() != null)
                tit = fon.getAbbreviation();
            else if (fon.getTitle() != null)
                tit = fon.getTitle();
            else if (fon.getText() != null) {
                tit = fon.getText().replaceAll("\n", " ");
                //tit = tit.length() > 35 ? tit.substring(0,35)+"…" : tit;
            } else if (fon.getPublicationFacts() != null) {
                tit = fon.getPublicationFacts().replaceAll("\n", " ");
            }
        return tit;
    }

    // Restituisce quante volte una fonte viene citata nel Gedcom
    // Ho provato a riscriverlo come Visitor, che però è molto più lento
    private int quante;

    private int quanteCitazioni(Source fon) {
        quante = 0;
        for (Person p : Global.gc.getPeople()) {
            cita(p, fon);
            for (Name n : p.getNames())
                cita(n, fon);
            for (EventFact ef : p.getEventsFacts())
                cita(ef, fon);
        }
        for (Family f : Global.gc.getFamilies()) {
            cita(f, fon);
            for (EventFact ef : f.getEventsFacts())
                cita(ef, fon);
        }
        for (Note n : Global.gc.getNotes())
            cita(n, fon);
        return quante;
    }

    // riceve un Object (Person, Name, EventFact...) e conta quante volte è citata la fonte
    private void cita(Object ogg, Source fonte) {
        List<SourceCitation> listaSc;
        if (ogg instanceof Note)    // se è una Nota
            listaSc = ((Note)ogg).getSourceCitations();
        else {
            for (Note n : ((NoteContainer)ogg).getNotes())
                cita(n, fonte);
            listaSc = ((SourceCitationContainer)ogg).getSourceCitations();
        }
        for (SourceCitation sc : listaSc) {
            if (sc.getRef() != null)
                if (sc.getRef().equals(fonte.getId()))
                    quante++;
        }
    }

    public static void newSource(Context context, Object container) {
        Source source = new Source();
        source.setId(U.newID(gc, Source.class));
        source.setTitle("");
        gc.addSource(source);
        if (container != null) {
            SourceCitation sourceCitation = new SourceCitation();
            sourceCitation.setRef(source.getId());
            if (container instanceof Note) ((Note)container).addSourceCitation(sourceCitation);
            else ((SourceCitationContainer)container).addSourceCitation(sourceCitation);
        }
        TreeUtil.INSTANCE.save(true, source);
        Memory.setLeader(source);
        context.startActivity(new Intent(context, SourceActivity.class));
    }

    // Elimina la fonte, i Ref in tutte le SourceCitation che puntano ad essa, e le SourceCitation vuote
    // Restituisce un array dei capostipiti modificati
    // Todo le citazioni alla Source eliminata diventano Fonte-nota a cui bisognerebbe poter riattaccare una Source
    public static Object[] deleteSource(Source source) {
        ListOfSourceCitations citazioni = new ListOfSourceCitations(gc, source.getId());
        for (ListOfSourceCitations.Triplet cita : citazioni.list) {
            SourceCitation cit = cita.citation;
            cit.setRef(null);
            // Se la SourceCitation non contiene altro si può eliminare
            boolean eliminabile = true;
            if (cit.getPage() != null || cit.getDate() != null || cit.getText() != null || cit.getQuality() != null
                    || !cit.getAllNotes(gc).isEmpty() || !cit.getAllMedia(gc).isEmpty() || !cit.getExtensions().isEmpty())
                eliminabile = false;
            if (eliminabile) {
                Object contenitore = cita.container;
                List<SourceCitation> lista;
                if (contenitore instanceof Note)
                    lista = ((Note)contenitore).getSourceCitations();
                else
                    lista = ((SourceCitationContainer)contenitore).getSourceCitations();
                lista.remove(cit);
                if (lista.isEmpty()) {
                    if (contenitore instanceof Note)
                        ((Note)contenitore).setSourceCitations(null);
                    else
                        ((SourceCitationContainer)contenitore).setSourceCitations(null);
                }
            }
        }
        gc.getSources().remove(source);
        if (gc.getSources().isEmpty())
            gc.setSources(null);
        gc.createIndexes(); // necessario
        Memory.setInstanceAndAllSubsequentToNull(source);
        return citazioni.getProgenitors();
    }

    @Override
    public void updateToolbar(ActionBar bar, Menu menu, MenuInflater inflater) {
        bar.setTitle(sourceList.size() + " " + Util.INSTANCE.caseString(sourceList.size() == 1 ? R.string.source : R.string.sources));
        if (sourceList.size() > 1) {
            // Search in SourcesFragment
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
            // Sort by menu
            inflater.inflate(R.menu.sort_by, menu);
            SubMenu subMenu = menu.findItem(R.id.sortBy).getSubMenu();
            if (Global.settings.expert)
                subMenu.add(0, 1, 0, R.string.id);
            subMenu.add(0, 2, 0, R.string.title);
            subMenu.add(0, 3, 0, R.string.citations);
        }
    }

    @Override
    public void selectItem(int id) {
        if (id > 0 && id <= 3) {
            if (order == id * 2 - 1)
                order++;
            else if (order == id * 2)
                order--;
            else
                order = id * 2 - 1;
            ordinaFonti();
            adapter.notifyDataSetChanged();
        }
    }

    private Source source;

    @Override
    public void onCreateContextMenu(ContextMenu menu, View vista, ContextMenu.ContextMenuInfo info) {
        source = gc.getSource(((TextView)vista.findViewById(R.id.source_id)).getText().toString());
        if (Global.settings.expert)
            menu.add(5, 0, 0, R.string.edit_id);
        menu.add(5, 1, 0, R.string.delete);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        if (item.getGroupId() == 5) {
            if (item.getItemId() == 0) { // Edit source ID
                U.editId(getContext(), source, this::showContent);
            } else if (item.getItemId() == 1) { // Delete source
                Object[] objects = deleteSource(source);
                TreeUtil.INSTANCE.save(false, objects);
                showContent();
                ((MainActivity)requireActivity()).refreshInterface();
            }
            return true;
        }
        return false;
    }
}
