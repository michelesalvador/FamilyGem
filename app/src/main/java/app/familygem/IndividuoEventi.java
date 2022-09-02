package app.familygem;

import android.content.Intent;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentManager;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.google.android.material.appbar.CollapsingToolbarLayout;
import org.folg.gedcom.model.Address;
import org.folg.gedcom.model.EventFact;
import org.folg.gedcom.model.Family;
import org.folg.gedcom.model.GedcomTag;
import org.folg.gedcom.model.Name;
import org.folg.gedcom.model.Note;
import org.folg.gedcom.model.NoteContainer;
import org.folg.gedcom.model.Person;
import org.folg.gedcom.model.SourceCitation;
import org.folg.gedcom.model.SourceCitationContainer;
import org.folg.gedcom.model.SpouseRef;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import app.familygem.constant.Gender;
import app.familygem.detail.Evento;
import app.familygem.detail.Nome;
import static app.familygem.Global.gc;

public class IndividuoEventi extends Fragment {

	Person one;
	private View changeView;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View vistaEventi = inflater.inflate(R.layout.individuo_scheda, container, false);
		if( gc != null ) {
			LinearLayout layout = vistaEventi.findViewById(R.id.contenuto_scheda);
			one = gc.getPerson(Global.indi);
			if( one != null ) {
				for( Name nome : one.getNames()) {
					String tit = getString(R.string.name);
					if( nome.getType() != null && !nome.getType().isEmpty() ) {
						tit += " (" + TypeView.getTranslatedType(nome.getType(), TypeView.Combo.NAME) + ")";
					}
					placeEvent(layout, tit, U.nomeCognome(nome, " "), nome);
				}
				for (EventFact fatto : one.getEventsFacts() ) {
					String txt = "";
					if( fatto.getValue() != null ) {
						if( fatto.getValue().equals("Y") && fatto.getTag()!=null &&
								( fatto.getTag().equals("BIRT") || fatto.getTag().equals("CHR") || fatto.getTag().equals("DEAT") ) )
							txt = getString(R.string.yes);
						else txt = fatto.getValue();
						txt += "\n";
					}
					//if( fatto.getType() != null ) txt += fatto.getType() + "\n"; // Included in event title
					if( fatto.getDate() != null ) txt += new Datatore(fatto.getDate()).writeDateLong() + "\n";
					if( fatto.getPlace() != null ) txt += fatto.getPlace() + "\n";
					Address indirizzo = fatto.getAddress();
					if( indirizzo != null ) txt += Dettaglio.writeAddress(indirizzo, true) + "\n";
					if( fatto.getCause() != null ) txt += fatto.getCause();
					if( txt.endsWith("\n") ) txt = txt.substring(0, txt.length() - 1); // Rimuove l'ultimo acapo
					placeEvent(layout, writeEventTitle(fatto), txt, fatto);
				}
				for( Estensione est : U.trovaEstensioni(one) ) {
					placeEvent(layout, est.nome, est.testo, est.gedcomTag);
				}
				U.placeNotes(layout, one, true);
				U.placeSourceCitations(layout, one);
				changeView = U.placeChangeDate(layout, one.getChange());
			}
		}
		return vistaEventi;
	}

	// Scopre se è un nome con name pieces o un suffisso nel value
	boolean nomeComplesso( Name n ) {
		// Name pieces
		boolean ricco = n.getGiven() != null || n.getSurname() != null
				|| n.getPrefix() != null || n.getSurnamePrefix() != null || n.getSuffix() != null
				|| n.getFone() != null || n.getRomn() != null;
		// Qualcosa dopo il cognome
		String nome = n.getValue();
		boolean suffisso = false;
		if( nome != null ) {
			nome = nome.trim();
			if( nome.lastIndexOf('/') < nome.length()-1 )
				suffisso = true;
		}
		return ricco || suffisso;
	}

	// Compose the title of an event of the person
	public static String writeEventTitle(EventFact event) {
		int str = 0;
		switch( event.getTag() ) {
			case "SEX": str = R.string.sex; break;
			case "BIRT": str = R.string.birth; break;
			case "BAPM": str = R.string.baptism; break;
			case "BURI": str = R.string.burial; break;
			case "DEAT": str = R.string.death; break;
			case "EVEN": str = R.string.event; break;
			case "OCCU": str = R.string.occupation; break;
			case "RESI": str = R.string.residence;
		}
		String txt;
		if( str != 0 )
			txt = Global.context.getString(str);
		else
			txt = event.getDisplayType();
		if( event.getType() != null )
			txt += " (" + event.getType() + ")";
		return txt;
	}

	private int chosenSex;
	private void placeEvent(LinearLayout layout, String title, String text, Object object) {
		View eventView = LayoutInflater.from(layout.getContext()).inflate(R.layout.individuo_eventi_pezzo, layout, false);
		layout.addView(eventView);
		((TextView)eventView.findViewById(R.id.evento_titolo)).setText(title);
		TextView textView = eventView.findViewById(R.id.evento_testo);
		if( text.isEmpty() ) textView.setVisibility(View.GONE);
		else textView.setText(text);
		if( Global.settings.expert && object instanceof SourceCitationContainer ) {
			List<SourceCitation> sourceCitations = ((SourceCitationContainer)object).getSourceCitations();
			TextView sourceView = eventView.findViewById(R.id.evento_fonti);
			if( !sourceCitations.isEmpty() ) {
				sourceView.setText(String.valueOf(sourceCitations.size()));
				sourceView.setVisibility(View.VISIBLE);
			}
		}
		LinearLayout otherLayout = eventView.findViewById(R.id.evento_altro);
		if( object instanceof NoteContainer )
			U.placeNotes(otherLayout, object, false);
		eventView.setTag(R.id.tag_oggetto, object);
		registerForContextMenu(eventView);
		if( object instanceof Name ) {
			U.placeMedia(otherLayout, object, false);
			eventView.setOnClickListener(v -> {
				// Se è un nome complesso propone la modalità esperto
				if( !Global.settings.expert && nomeComplesso((Name)object) ) {
					new AlertDialog.Builder(getContext()).setMessage(R.string.complex_tree_advanced_tools)
							.setPositiveButton(android.R.string.ok, (dialog, i) -> {
								Global.settings.expert = true;
								Global.settings.save();
								Memoria.aggiungi(object);
								startActivity(new Intent(getContext(), Nome.class));
							}).setNegativeButton(android.R.string.cancel, (dialog, i) -> {
								Memoria.aggiungi(object);
								startActivity(new Intent(getContext(), Nome.class));
							}).show();
				} else {
					Memoria.aggiungi(object);
					startActivity(new Intent(getContext(), Nome.class));
				}
			});
		} else if( object instanceof EventFact ) {
			// Sex fact
			if( ((EventFact)object).getTag() != null && ((EventFact)object).getTag().equals("SEX") ) {
				Map<String, String> sexes = new LinkedHashMap<>();
				sexes.put("M", getString(R.string.male));
				sexes.put("F", getString(R.string.female));
				sexes.put("U", getString(R.string.unknown));
				textView.setText(text);
				chosenSex = 0;
				for( Map.Entry<String, String> sex : sexes.entrySet() ) {
					if( text.equals(sex.getKey()) ) {
						textView.setText(sex.getValue());
						break;
					}
					chosenSex++;
				}
				if( chosenSex > 2 ) chosenSex = -1;
				eventView.setOnClickListener(view -> new AlertDialog.Builder(view.getContext())
						.setSingleChoiceItems(sexes.values().toArray(new String[0]), chosenSex, (dialog, item) -> {
							((EventFact)object).setValue(new ArrayList<>(sexes.keySet()).get(item));
							aggiornaRuoliConiugali(one);
							dialog.dismiss();
							refresh(1);
							U.save(true, one);
						}).show());
			} else { // All other events
				U.placeMedia(otherLayout, object, false);
				eventView.setOnClickListener(v -> {
					Memoria.aggiungi(object);
					startActivity(new Intent(getContext(), Evento.class));
				});
			}
		} else if( object instanceof GedcomTag ) {
			eventView.setOnClickListener(v -> {
				Memoria.aggiungi(object);
				startActivity(new Intent(getContext(), app.familygem.detail.Estensione.class));
			});
		}
	}

	// In tutte le famiglie coniugali rimuove gli spouse ref di 'person' e ne aggiunge uno corrispondente al sesso
	// Serve soprattutto in caso di esportazione del Gedcom per avere allineati gli HUSB e WIFE con il sesso
	static void aggiornaRuoliConiugali(Person person) {
		SpouseRef spouseRef = new SpouseRef();
		spouseRef.setRef(person.getId());
		boolean removed = false;
		for( Family fam : person.getSpouseFamilies(gc) ) {
			if( Gender.isFemale(person) ) { // Female 'person' will become a wife
				Iterator<SpouseRef> husbandRefs = fam.getHusbandRefs().iterator();
				while( husbandRefs.hasNext() ) {
					String hr = husbandRefs.next().getRef();
					if( hr != null && hr.equals(person.getId()) ) {
						husbandRefs.remove();
						removed = true;
					}
				}
				if( removed ) {
					fam.addWife(spouseRef);
					removed = false;
				}
			} else { // For all other sexs 'person' will become husband
				Iterator<SpouseRef> wifeRefs = fam.getWifeRefs().iterator();
				while( wifeRefs.hasNext() ) {
					String wr = wifeRefs.next().getRef();
					if( wr != null && wr.equals(person.getId()) ) {
						wifeRefs.remove();
						removed = true;
					}
				}
				if( removed ) {
					fam.addHusband(spouseRef);
					removed = false;
				}
			}
		}
	}

	// Menu contestuale
	View vistaPezzo;
	Object oggettoPezzo;
	@Override
	public void onCreateContextMenu( ContextMenu menu, View vista, ContextMenu.ContextMenuInfo info ) {
		// menuInfo come al solito è null
		vistaPezzo = vista;
		oggettoPezzo = vista.getTag( R.id.tag_oggetto );
		if( oggettoPezzo instanceof Name ) {
			menu.add( 0, 200, 0, R.string.copy );
			if( one.getNames().indexOf(oggettoPezzo) > 0 )
				menu.add( 0, 201, 0, R.string.move_up );
			if( one.getNames().indexOf(oggettoPezzo) < one.getNames().size()-1 )
				menu.add( 0, 202, 0, R.string.move_down );
			menu.add( 0, 203, 0, R.string.delete );
		} else if( oggettoPezzo instanceof EventFact ) {
			menu.add( 0, 210, 0, R.string.copy );
			if( one.getEventsFacts().indexOf(oggettoPezzo) > 0 )
				menu.add( 0, 211, 0, R.string.move_up );
			if( one.getEventsFacts().indexOf(oggettoPezzo) < one.getEventsFacts().size()-1 )
				menu.add( 0, 212, 0, R.string.move_down );
			menu.add( 0, 213, 0, R.string.delete );
		} else if( oggettoPezzo instanceof GedcomTag ) {
			menu.add( 0, 220, 0, R.string.copy );
			menu.add( 0, 221, 0, R.string.delete );
		} else if( oggettoPezzo instanceof Note ) {
			menu.add( 0, 225, 0, R.string.copy );
			if( ((Note)oggettoPezzo).getId() != null )
				menu.add( 0, 226, 0, R.string.unlink );
			menu.add( 0, 227, 0, R.string.delete );
		} else if( oggettoPezzo instanceof SourceCitation ) {
			menu.add( 0, 230, 0, R.string.copy );
			menu.add( 0, 231, 0, R.string.delete );
		}
	}
	@Override
	public boolean onContextItemSelected( MenuItem item ) {
		List<Name> nomi = one.getNames();
		List<EventFact> fatti = one.getEventsFacts();
		int cosa = 0; // cosa aggiornare dopo la modifica
		switch( item.getItemId() ) {
			// Nome
			case 200: // Copia nome
			case 210: // Copia evento
			case 220: // Copia estensione
				U.copiaNegliAppunti(((TextView)vistaPezzo.findViewById(R.id.evento_titolo)).getText(),
						((TextView)vistaPezzo.findViewById(R.id.evento_testo)).getText());
				return true;
			case 201: // Sposta su
				nomi.add(nomi.indexOf(oggettoPezzo) - 1, (Name)oggettoPezzo);
				nomi.remove(nomi.lastIndexOf(oggettoPezzo));
				cosa = 2;
				break;
			case 202: // Sposta giù
				nomi.add(nomi.indexOf(oggettoPezzo) + 2, (Name)oggettoPezzo);
				nomi.remove(nomi.indexOf(oggettoPezzo));
				cosa = 2;
				break;
			case 203: // Elimina
				if( U.preserva(oggettoPezzo) ) return false;
				one.getNames().remove(oggettoPezzo);
				Memoria.annullaIstanze(oggettoPezzo);
				vistaPezzo.setVisibility(View.GONE);
				cosa = 2;
				break;
			// Evento generico
			case 211: // Sposta su
				fatti.add(fatti.indexOf(oggettoPezzo) - 1, (EventFact)oggettoPezzo);
				fatti.remove(fatti.lastIndexOf(oggettoPezzo));
				cosa = 1;
				break;
			case 212: // Sposta giu
				fatti.add(fatti.indexOf(oggettoPezzo) + 2, (EventFact)oggettoPezzo);
				fatti.remove(fatti.indexOf(oggettoPezzo));
				cosa = 1;
				break;
			case 213:
				// todo Conferma elimina
				one.getEventsFacts().remove(oggettoPezzo);
				Memoria.annullaIstanze(oggettoPezzo);
				vistaPezzo.setVisibility(View.GONE);
				break;
			// Estensione
			case 221: // Elimina
				U.eliminaEstensione((GedcomTag)oggettoPezzo, one, vistaPezzo);
				break;
			// Nota
			case 225: // Copia
				U.copiaNegliAppunti(getText(R.string.note), ((TextView)vistaPezzo.findViewById(R.id.nota_testo)).getText());
				return true;
			case 226: // Scollega
				U.scollegaNota((Note)oggettoPezzo, one, vistaPezzo);
				break;
			case 227:
				Object[] capi = U.eliminaNota((Note)oggettoPezzo, vistaPezzo);
				U.save(true, capi);
				refresh(0);
				return true;
			// Citazione fonte
			case 230: // Copia
				U.copiaNegliAppunti(getText(R.string.source_citation),
						((TextView)vistaPezzo.findViewById(R.id.fonte_testo)).getText() + "\n"
								+ ((TextView)vistaPezzo.findViewById(R.id.citazione_testo)).getText());
				return true;
			case 231: // Elimina
				// todo conferma : Vuoi eliminare questa citazione della fonte? La fonte continuerà ad esistere.
				one.getSourceCitations().remove(oggettoPezzo);
				Memoria.annullaIstanze(oggettoPezzo);
				vistaPezzo.setVisibility(View.GONE);
				break;
			default:
				return false;
		}
		U.save(true, one);
		refresh(cosa);
		return true;
	}

	// Rinfresca il contenuto del frammento Eventi
	void refresh(int what) {
		if( what == 0 ) { // sostituisce solo la data di cambiamento
			LinearLayout scatola = getActivity().findViewById(R.id.contenuto_scheda);
			if( changeView != null )
				scatola.removeView(changeView);
			changeView = U.placeChangeDate(scatola, one.getChange());
		} else { // ricarica il fragment
			FragmentManager fragmentManager = requireActivity().getSupportFragmentManager();
			fragmentManager.beginTransaction().detach(this).commit();
			fragmentManager.beginTransaction().attach(this).commit();
			if( what == 2 ) { // aggiorna anche il titolo dell'activity
				CollapsingToolbarLayout barraCollasso = requireActivity().findViewById(R.id.toolbar_layout);
				barraCollasso.setTitle(U.epiteto(one));
			}
		}
	}
}
