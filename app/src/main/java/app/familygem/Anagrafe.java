package app.familygem;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.recyclerview.widget.RecyclerView;
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
import android.widget.Toast;
import org.folg.gedcom.model.EventFact;
import org.folg.gedcom.model.Family;
import org.folg.gedcom.model.Name;
import org.folg.gedcom.model.Person;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import app.familygem.constants.Gender;
import static app.familygem.Globale.gc;

public class Anagrafe extends Fragment {

	private List<Person> listaIndividui;
	public AdattatoreAnagrafe adattatore;
	private Order order;
	private boolean gliIdsonoNumerici;

	private enum Order {
		NONE, ID_ASC, ID_DESC, SURNAME_ASC, SURNAME_DESC, YEAR_ASC, YEAR_DESC, KIN_ASC, KIN_DESC;
		public Order next() {
			return values()[ordinal() + 1];
		}
		public Order prev() {
			return values()[ordinal() - 1];
		}
	};

	@Override
	public View onCreateView( LayoutInflater inflater, ViewGroup container, Bundle bandolo ) {
		View vista = inflater.inflate( R.layout.ricicla_vista, container, false );
		if( gc != null ) {
			listaIndividui = gc.getPeople();
			arredaBarra();
			RecyclerView vistaLista = vista.findViewById( R.id.riciclatore );
			vistaLista.setPadding( 12, 12, 12, vistaLista.getPaddingBottom() );
			adattatore = new AdattatoreAnagrafe();
			vistaLista.setAdapter( adattatore );
			gliIdsonoNumerici = verificaIdNumerici();
			vista.findViewById( R.id.fab ).setOnClickListener( v -> {
				Intent intento = new Intent( getContext(), EditaIndividuo.class );
				intento.putExtra( "idIndividuo", "TIZIO_NUOVO" );
				startActivity( intento );
			});
		}
		return vista;
	}

	void arredaBarra() {
		((AppCompatActivity)getActivity()).getSupportActionBar().setTitle( listaIndividui.size() + " "
				+ getString(listaIndividui.size()==1 ? R.string.person : R.string.persons).toLowerCase() );
		if( listaIndividui.size() > 1 )
			setHasOptionsMenu(true);
		else
			setHasOptionsMenu(false);
	}

	public class AdattatoreAnagrafe extends RecyclerView.Adapter<GestoreIndividuo> implements Filterable {
		@Override
		public GestoreIndividuo onCreateViewHolder( ViewGroup parent, int tipo ) {
			View vistaIndividuo = LayoutInflater.from( parent.getContext() )
					.inflate(R.layout.pezzo_individuo, parent, false);
			registerForContextMenu( vistaIndividuo );
			return new GestoreIndividuo( vistaIndividuo );
		}
		@Override
		public void onBindViewHolder( GestoreIndividuo gestore, int posizione ) {
			Person person = listaIndividui.get(posizione);
			View vistaIndi = gestore.vista;

			String ruolo = null;
			if( order == Order.ID_ASC || order == Order.ID_DESC ) ruolo = person.getId();
			else if( order == Order.KIN_ASC || order == Order.KIN_DESC ) ruolo = String.valueOf(person.getExtension("kin"));
			TextView vistaRuolo = vistaIndi.findViewById( R.id.indi_ruolo );
			if( ruolo == null )
				vistaRuolo.setVisibility( View.GONE );
			else {
				vistaRuolo.setText( ruolo );
				vistaRuolo.setVisibility( View.VISIBLE );
			}

			TextView vistaNome = vistaIndi.findViewById( R.id.indi_nome );
			String nome = U.epiteto(person);
			vistaNome.setText( nome );
			vistaNome.setVisibility( ( nome.isEmpty() && ruolo != null ) ? View.GONE : View.VISIBLE );

			TextView vistaTitolo = vistaIndi.findViewById(R.id.indi_titolo);
			String titolo = U.titolo(person);
			if( titolo.isEmpty() )
				vistaTitolo.setVisibility(View.GONE);
			else {
				vistaTitolo.setText(titolo);
				vistaTitolo.setVisibility(View.VISIBLE);
			}

			int bordo;
			switch( Gender.getGender(person) ) {
				case MALE: bordo = R.drawable.casella_bordo_maschio; break;
				case FEMALE: bordo = R.drawable.casella_bordo_femmina; break;
				default: bordo = R.drawable.casella_bordo_neutro;
			}
			vistaIndi.findViewById(R.id.indi_bordo).setBackgroundResource(bordo);

			U.details(person, vistaIndi.findViewById(R.id.indi_dettagli));
			F.unaFoto(Globale.gc, person, vistaIndi.findViewById(R.id.indi_foto));
			vistaIndi.findViewById(R.id.indi_lutto).setVisibility(U.morto(person) ? View.VISIBLE : View.GONE);
			vistaIndi.setTag(person.getId());
		}
		@Override
		public Filter getFilter() {
			return new Filter() {
				@Override
				protected FilterResults performFiltering(CharSequence charSequence) {
					String query = charSequence.toString();
					if (query.isEmpty()) {
						listaIndividui = gc.getPeople();
					} else {
						List<Person> filteredList = new ArrayList<>();
						for (Person pers : gc.getPeople()) {
							if( U.epiteto(pers).toLowerCase().contains(query.toLowerCase()) ) {
								filteredList.add(pers);
							}
						}
						listaIndividui = filteredList;
					}
					sortPeople();
					FilterResults filterResults = new FilterResults();
					filterResults.values = listaIndividui;
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
			return listaIndividui.size();
		}
	}

	class GestoreIndividuo extends RecyclerView.ViewHolder implements View.OnClickListener {
		View vista;
		GestoreIndividuo( View vista ) {
			super( vista );
			this.vista = vista;
			vista.setOnClickListener(this);
		}
		@Override
		public void onClick( View vista ) {
			// Anagrafe per scegliere il parente e restituire i valori a Diagramma, Individuo, Famiglia o Condivisione
			Person parente = gc.getPerson((String)vista.getTag());
			Intent intent = getActivity().getIntent();
			if( intent.getBooleanExtra("anagrafeScegliParente", false) ) {
				intent.putExtra( "idParente", parente.getId() );
				// Cerca una eventuale famiglia esistente che possa ospitare perno
				String collocazione = intent.getStringExtra("collocazione");
				if( collocazione != null && collocazione.equals("FAMIGLIA_ESISTENTE") ) {
					String idFamiglia = null;
					switch( intent.getIntExtra("relazione",0) ) {
						case 1: // Genitore
							if( parente.getSpouseFamilyRefs().size() > 0 )
								idFamiglia = parente.getSpouseFamilyRefs().get(0).getRef();
							break;
						case 2:
							if( parente.getParentFamilyRefs().size() > 0 )
								idFamiglia = parente.getParentFamilyRefs().get(0).getRef();
							break;
						case 3:
							for( Family fam : parente.getSpouseFamilies(gc) ) {
								if( fam.getHusbandRefs().isEmpty() || fam.getWifeRefs().isEmpty() ) {
									idFamiglia = fam.getId();
									break;
								}
							}
							break;
						case 4:
							for( Family fam : parente.getParentFamilies(gc) ) {
								if( fam.getHusbandRefs().isEmpty() || fam.getWifeRefs().isEmpty() ) {
									idFamiglia = fam.getId();
									break;
								}
							}
							break;
					}
					if( idFamiglia != null ) // aggiungiParente() userà la famiglia trovata
						intent.putExtra( "idFamiglia", idFamiglia );
					else // aggiungiParente() creerà una nuova famiglia
						intent.removeExtra( "collocazione" );
				}
				getActivity().setResult( AppCompatActivity.RESULT_OK, intent );
				getActivity().finish();
			} else { // Normale collegamento alla scheda individuo
				// todo Click sulla foto apre la scheda media..
				// intento.putExtra( "scheda", 0 );
				Memoria.setPrimo( parente );
				startActivity( new Intent(getContext(), Individuo.class) );
			}
		}
	}

	// Andandosene dall'attività senza aver scelto un parente resetta l'extra
	@Override
	public void onPause() {
		super.onPause();
		getActivity().getIntent().removeExtra("anagrafeScegliParente");
	}

	// Verifica se tutti gli id delle persone contengono numeri
	// Appena un id contiene solo lettere restituisce falso
	boolean verificaIdNumerici() {
		esterno:
		for( Person p : gc.getPeople() ) {
			for( char c : p.getId().toCharArray() ) {
				if (Character.isDigit(c))
					continue esterno;
			}
			return false;
		}
		return true;
	}

	private void sortPeople() {
		Collections.sort(listaIndividui, (p1, p2) -> {
			switch( order ) {
				case ID_ASC: // Sort for GEDCOM ID
					if( gliIdsonoNumerici )
						return U.soloNumeri(p1.getId()) - U.soloNumeri(p2.getId());
					else
						return p1.getId().compareToIgnoreCase(p2.getId());
				case ID_DESC:
					if( gliIdsonoNumerici )
						return U.soloNumeri(p2.getId()) - U.soloNumeri(p1.getId());
					else
						return p2.getId().compareToIgnoreCase(p1.getId());
				case SURNAME_ASC: // Sort for surname
					if (p1.getNames().size() == 0) // i nomi null vanno in fondo
						return (p2.getNames().size() == 0) ? 0 : 1;
					if (p2.getNames().size() == 0)
						return -1;
					Name n1 = p1.getNames().get(0);
					Name n2 = p2.getNames().get(0);
					// anche i nomi con value, given e surname null vanno in fondo
					if (n1.getValue() == null && n1.getGiven() == null && n1.getSurname() == null)
						return (n2.getValue() == null) ? 0 : 1;
					if (n2.getValue() == null && n2.getGiven() == null && n2.getSurname() == null)
						return -1;
					return cognomeNome(p1).compareToIgnoreCase(cognomeNome(p2));
				case SURNAME_DESC:
					if (p1.getNames().size() == 0)
						return p2.getNames().size() == 0 ? 0 : 1;
					if (p2.getNames().size() == 0)
						return -1;
					n1 = p1.getNames().get(0);
					n2 = p2.getNames().get(0);
					if (n1.getValue() == null && n1.getGiven() == null && n1.getSurname() == null)
						return (n2.getValue() == null) ? 0 : 1;
					if (n2.getValue() == null && n2.getGiven() == null && n2.getSurname() == null)
						return -1;
					return cognomeNome(p2).compareToIgnoreCase(cognomeNome(p1));
				case YEAR_ASC: // Sort for main person's year
					return annoBase(p1) - annoBase(p2);
				case YEAR_DESC:
					if( annoBase(p2) == 9999 ) // Quelli senza anno vanno in fondo
						return -1;
					if( annoBase(p1) == 9999 )
						return annoBase(p2) == 9999 ? 0 : 1;
					return annoBase(p2) - annoBase(p1);
				case KIN_ASC: // Sort for number of relatives
					return countRelatives(p1) - countRelatives(p2);
				case KIN_DESC:
					return countRelatives(p2) - countRelatives(p1);
			}
			return 0;
		});
	}

	// Restituisce una stringa con cognome e nome attaccati:
	// 'SalvadorMichele ' oppure 'ValleFrancesco Maria ' oppure ' Donatella '
	static String cognomeNome( Person tizio ) {
		Name name = tizio.getNames().get(0);
		String epiteto = name.getValue();
		String nomeDato = "";
		String cognome = " "; // deve esserci uno spazio per ordinare i nomi senza cognome
		if( epiteto != null ) {
			if( epiteto.indexOf('/') > 0 )
				nomeDato = epiteto.substring( 0, epiteto.indexOf('/') );	// prende il nome prima di '/'
			if( epiteto.lastIndexOf('/') - epiteto.indexOf('/') > 1 )	// se c'è un cognome tra i due '/'
				cognome = epiteto.substring( epiteto.indexOf('/')+1, epiteto.lastIndexOf("/") );
			String prefix = name.getPrefix(); // Solo il nomeDato proveniente dal value potrebbe avere un prefisso, dal given no perché già di suo è solo il nomeDato
			if( prefix != null && nomeDato.startsWith(prefix) )
				nomeDato = nomeDato.substring( prefix.length() ).trim();
		} else {
			if( name.getGiven() != null )
				nomeDato = name.getGiven();
			if( name.getSurname() != null )
				cognome = name.getSurname();
		}
		String surPrefix = name.getSurnamePrefix();
		if( surPrefix != null && cognome.startsWith(surPrefix) )
			cognome = cognome.substring( surPrefix.length() ).trim();
		return cognome.concat( nomeDato );
	}

	// riceve una Person e restituisce un anno base della sua esistenza
	private int annoBase( Person p ) {
		int min = 9999;
		for( EventFact unFatto : p.getEventsFacts() ) {
			if( unFatto.getDate() != null ) {
				int anno = new Datatore( unFatto.getDate() ).soloAnno();
				if( anno < min )
					min = anno;
			}
		}
		return min;
	}

	// riceve una persona e restitusce i due luoghi: iniziale – finale
	static String dueLuoghi( Person p ) {
		String luoghi = "";
		List<EventFact> fatti = p.getEventsFacts();
		for( EventFact unFatto : fatti ) {
			if( unFatto.getPlace() != null ) {
				luoghi = togliVirgole( unFatto.getPlace() );
				break;
			}
		}
		for(int i=fatti.size()-1; i>=0; i-- ) {
			String secondoLuogo = fatti.get(i).getPlace();
			if( secondoLuogo != null ) {
				secondoLuogo = togliVirgole(secondoLuogo);
				if( !secondoLuogo.equals(luoghi) )
					luoghi = luoghi.concat(" – ").concat(secondoLuogo);
				break;
			}
		}
		return luoghi;
	}

	// riceve un luogo stile Gedcom e restituisce il primo nome tra le virgole
	private static String togliVirgole( String luogo ) {
		// salta le virgole iniziali per luoghi tipo ',,,England'
		int iniz = 0;
		for( char c : luogo.toCharArray() ) {
			if( c!=',' && c!=' ' )
				break;
			iniz++;
		}
		luogo = luogo.substring(iniz);
		if( luogo.indexOf(",") > 0 )
			luogo = luogo.substring( 0, luogo.indexOf(",") );
		return luogo;
	}

	/** Count how many near relatives a person has: parents, siblings, step-siblings, spouses and children.
	 * Save also the result in the 'kin' extension.
	 * @param person The person to start from
	 * @return Number of near relatives (person excluded)
	 */
	static int countRelatives(Person person) {
		int count = 0;
		if( person != null ) {
			// Famiglie di origine: genitori e fratelli
			List<Family> listaFamiglie = person.getParentFamilies(gc);
			for( Family famiglia : listaFamiglie ) {
				count += famiglia.getHusbands(gc).size();
				count += famiglia.getWives(gc).size();
				for( Person fratello : famiglia.getChildren(gc) ) // solo i figli degli stessi due genitori, non i fratellastri
					if( !fratello.equals(person) )
						count++;
			}
			// Fratellastri e sorellastre
			for( Family famiglia : person.getParentFamilies(gc) ) {
				for( Person padre : famiglia.getHusbands(gc) ) {
					List<Family> famigliePadre = padre.getSpouseFamilies(gc);
					famigliePadre.removeAll(listaFamiglie);
					for( Family fam : famigliePadre )
						count += fam.getChildren(gc).size();
				}
				for( Person madre : famiglia.getWives(gc) ) {
					List<Family> famiglieMadre = madre.getSpouseFamilies(gc);
					famiglieMadre.removeAll(listaFamiglie);
					for( Family fam : famiglieMadre )
						count += fam.getChildren(gc).size();
				}
			}
			// Coniugi e figli
			for( Family famiglia : person.getSpouseFamilies(gc) ) {
				if( Gender.isMale(person) )
					count += famiglia.getWives(gc).size();
				else
					count += famiglia.getHusbands(gc).size();
				count += famiglia.getChildren(gc).size();
			}
			person.putExtension("kin", count);
		}
		return count;
	}

	// menu opzioni nella toolbar
	@Override
	public void onCreateOptionsMenu( Menu menu, MenuInflater inflater ) {

		SubMenu subMenu = menu.addSubMenu(R.string.order_by);
		subMenu.add(0, 1, 0, R.string.id);
		subMenu.add(0, 2, 0, R.string.surname);
		subMenu.add(0, 3, 0, R.string.year);
		subMenu.add(0, 4, 0, R.string.number_relatives);

		// Ricerca nell'Anagrafe
		inflater.inflate( R.menu.cerca, menu );	// già questo basta a far comparire la lente con il campo di ricerca
		final SearchView vistaCerca = (SearchView) menu.findItem(R.id.ricerca).getActionView();
		vistaCerca.setOnQueryTextListener( new SearchView.OnQueryTextListener() {
			@Override
			public boolean onQueryTextChange( String query ) {
				adattatore.getFilter().filter(query);
				return true;
			}
			@Override
			public boolean onQueryTextSubmit( String q ) {
				vistaCerca.clearFocus();
				return false;
			}
		});
	}
	@Override
	public boolean onOptionsItemSelected( MenuItem item ) {
		int id = item.getItemId();
		if( id > 0 && id <= 4 ) {
			// Clicking twice the same menu item switchs sorting ASC and DESC
			if( order == Order.values()[id * 2 - 1] )
				order = order.next();
			else if( order == Order.values()[id * 2] )
				order = order.prev();
			else
				order = Order.values()[id * 2 - 1];
			sortPeople();
			adattatore.notifyDataSetChanged();
			//U.salvaJson( false ); // dubbio se metterlo per salvare subito il riordino delle persone...
			return true;
		}
		return false;
	}

	// Menu contestuale
	private String idIndi;
	@Override
	public void onCreateContextMenu( ContextMenu menu, View vista, ContextMenu.ContextMenuInfo info) {
		idIndi = (String) vista.getTag();
		menu.add(0, 0, 0, R.string.diagram );
		if( !gc.getPerson(idIndi).getParentFamilies(gc).isEmpty() )
			menu.add(0, 1, 0, gc.getPerson(idIndi).getSpouseFamilies(gc).isEmpty() ? R.string.family : R.string.family_as_child );
		if( !gc.getPerson(idIndi).getSpouseFamilies(gc).isEmpty() )
			menu.add(0, 2, 0, gc.getPerson(idIndi).getParentFamilies(gc).isEmpty() ? R.string.family : R.string.family_as_spouse );
		menu.add(0, 3, 0, R.string.modify );
		menu.add(0, 4, 0, R.string.delete );
	}
	@Override
	public boolean onContextItemSelected( MenuItem item ) {
		int id = item.getItemId();
		if( id == 0 ) {	// Apri Diagramma
			U.qualiGenitoriMostrare( getContext(), gc.getPerson(idIndi), 1 );
		} else if( id == 1) {	// Famiglia come figlio
			U.qualiGenitoriMostrare( getContext(), gc.getPerson(idIndi), 2 );
		} else if( id == 2 ) {	// Famiglia come coniuge
			U.qualiConiugiMostrare( getContext(), gc.getPerson(idIndi), null );
		} else if( id == 3 ) {	// Modifica
			Intent intento = new Intent( getContext(), EditaIndividuo.class );
			intento.putExtra( "idIndividuo", idIndi );
			startActivity( intento );
		} else if( id == 4 ) {	// Elimina
			new AlertDialog.Builder(getContext()).setMessage( R.string.really_delete_person )
					.setPositiveButton( R.string.delete, (dialog, i) -> {
						Family[] famiglie = eliminaPersona(getContext(), idIndi);
						adattatore.notifyDataSetChanged();
						arredaBarra();
						U.controllaFamiglieVuote(getContext(), null, false, famiglie);
					}).setNeutralButton( R.string.cancel, null ).show();
		} else {
			return false;
		}
		return true;
	}

	// Cancella tutti i ref nelle famiglie della tal persona
	// Restituisce l'elenco delle famiglie affette
	static Family[] scollega( String idScollegando ) {
		Person egli = gc.getPerson( idScollegando );
		Set<Family> famiglie = new HashSet<>();
		for( Family f : egli.getParentFamilies(gc) ) {	// scollega i suoi ref nelle famiglie
			f.getChildRefs().remove( f.getChildren(gc).indexOf(egli) );
			famiglie.add( f );
		}
		for( Family f : egli.getSpouseFamilies(gc) ) {
			if( f.getHusbands(gc).contains(egli) ) {
				f.getHusbandRefs().remove( f.getHusbands(gc).indexOf(egli) );
				famiglie.add( f );
			}
			if( f.getWives(gc).contains(egli) ) {
				f.getWifeRefs().remove( f.getWives(gc).indexOf(egli) );
				famiglie.add( f );
			}
		}
		egli.setParentFamilyRefs( null );	// nell'indi scollega i ref delle famiglie a cui appartiene
		egli.setSpouseFamilyRefs( null );
		return famiglie.toArray( new Family[0] );
	}

	// Elimina una persona dal gedcom, trova eventuale nuova radice, restituisce famiglie modificate
	static Family[] eliminaPersona(Context contesto, String idEliminando) {
		Family[] famiglie = scollega( idEliminando );
		Person eliminando = gc.getPerson( idEliminando );
		Memoria.annullaIstanze( eliminando );
		gc.getPeople().remove( eliminando );
		gc.createIndexes();	// necessario
		String idNuovaRadice = U.trovaRadice(gc);	// Todo dovrebbe essere: trovaParentePiuProssimo
		if( Globale.preferenze.alberoAperto().radice!=null && Globale.preferenze.alberoAperto().radice.equals(idEliminando) ) {
			Globale.preferenze.alberoAperto().radice = idNuovaRadice;
		}
		Globale.preferenze.salva();
		if( Globale.individuo != null && Globale.individuo.equals(idEliminando) )
			Globale.individuo = idNuovaRadice;
		Toast.makeText( contesto, R.string.person_deleted, Toast.LENGTH_SHORT ).show();
		U.salvaJson( true, (Object[])famiglie );
		return famiglie;
	}
}