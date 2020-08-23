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
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.TextView;
import android.widget.Toast;
import org.folg.gedcom.model.EventFact;
import org.folg.gedcom.model.Family;
import org.folg.gedcom.model.Person;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import app.familygem.dettaglio.Famiglia;
import static app.familygem.Globale.gc;

public class Anagrafe extends Fragment {

	private List<Person> listaIndividui;
	private AdattatoreAnagrafe adattatore;
	private int ordine;
	private boolean gliIdsonoNumerici;

	@Override
	public View onCreateView( LayoutInflater inflater, ViewGroup container, Bundle bandolo ) {
		View vista = inflater.inflate( R.layout.ricicla_vista, container, false );
		if( gc != null ) {
			listaIndividui = gc.getPeople();
			((AppCompatActivity)getActivity()).getSupportActionBar().setTitle( listaIndividui.size() + " "
					+ getString(listaIndividui.size()==1 ? R.string.person : R.string.persons).toLowerCase() );
			if( listaIndividui.size() > 1 )
				setHasOptionsMenu(true);
			RecyclerView vistaLista = vista.findViewById( R.id.riciclatore );
			vistaLista.setPadding( 12, 12, 12, vistaLista.getPaddingBottom() );
			adattatore = new AdattatoreAnagrafe();
			vistaLista.setAdapter( adattatore );
			gliIdsonoNumerici = verificaIdNumerici();
			vista.findViewById( R.id.fab ).setOnClickListener( v -> {
				Intent intento = new Intent( getContext(), EditaIndividuo.class );
				intento.putExtra( "idIndividuo", "TIZIO_NUOVO" );
				startActivity( intento );
			} );
		}
		return vista;
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
			Person persona = listaIndividui.get(posizione);
			View vistaIndi = gestore.vista;

			String ruolo = null;
			if( ordine == 1 || ordine == 2 ) ruolo = persona.getId();
			else if( ordine == 7 || ordine == 8 ) ruolo = String.valueOf(persona.getExtension("famili"));
			TextView vistaRuolo = vistaIndi.findViewById( R.id.indi_ruolo );
			if( ruolo == null )
				vistaRuolo.setVisibility( View.GONE );
			else {
				vistaRuolo.setText( ruolo );
				vistaRuolo.setVisibility( View.VISIBLE );
			}

			TextView vistaNome = vistaIndi.findViewById( R.id.indi_nome );
			String nome = U.epiteto(persona);
			vistaNome.setText( nome );
			vistaNome.setVisibility( ( nome.isEmpty() && ruolo != null ) ? View.GONE : View.VISIBLE );

			TextView vistaTitolo = vistaIndi.findViewById(R.id.indi_titolo);
			String titolo = U.titolo( persona );
			if( titolo.isEmpty() )
				vistaTitolo.setVisibility( View.GONE );
			else {
				vistaTitolo.setText( titolo );
				vistaTitolo.setVisibility( View.VISIBLE );
			}

			int sfondo;
			switch( U.sesso(persona) ) {
				case 1: sfondo = R.drawable.casella_maschio; break;
				case 2: sfondo = R.drawable.casella_femmina; break;
				default: sfondo = R.drawable.casella_neutro;
			}
			vistaIndi.findViewById(R.id.indi_carta).setBackgroundResource( sfondo );

			U.dettagli( persona, vistaIndi.findViewById( R.id.indi_dettagli ) );
			U.unaFoto( Globale.gc, persona, vistaIndi.findViewById(R.id.indi_foto) );
			vistaIndi.findViewById( R.id.indi_lutto ).setVisibility( U.morto(persona) ? View.VISIBLE : View.GONE );
			vistaIndi.setTag( persona.getId() );
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
					ordinaIndividui();
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
			// Anagrafe per scegliere il parente e restituire l'id a Diagramma a Individuo o a Famiglia
			if( getActivity().getIntent().getBooleanExtra( "anagrafeScegliParente", false ) ) {
				Intent intent = new Intent();
				intent.putExtra( "idParente", (String) vista.getTag() );
				intent.putExtra( "relazione", getActivity().getIntent().getIntExtra( "relazione", 0 ) );
				intent.putExtra( "famigliaNum", getActivity().getIntent().getIntExtra( "famigliaNum", 0 ) );
				intent.putExtra( "idFamiglia", getActivity().getIntent().getStringExtra( "idFamiglia" ) );
				getActivity().setResult( AppCompatActivity.RESULT_OK, intent );
				getActivity().finish();
			} else { // Normale collegamento alla scheda individuo
				Intent intento = new Intent( getContext(), Individuo.class );
				intento.putExtra( "idIndividuo", (String) vista.getTag() );
				// todo Click sulla foto apre la scheda media..
				// intento.putExtra( "scheda", 0 );
				startActivity( intento );
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

	void ordinaIndividui() {
		if( ordine > 0 ) {  // 0 ovvero rimane l'ordinamento già esistente
			Collections.sort(listaIndividui, ( p1, p2 ) -> {
				switch( ordine ) {
					case 1: // Ordina per ID Gedcom
						if( gliIdsonoNumerici )
							return U.soloNumeri(p1.getId()) - U.soloNumeri(p2.getId());
						else
							return p1.getId().compareToIgnoreCase(p2.getId());
					case 2:
						if( gliIdsonoNumerici )
							return U.soloNumeri(p2.getId()) - U.soloNumeri(p1.getId());
						else
							return p2.getId().compareToIgnoreCase(p1.getId());
					case 3: // Ordina per cognome
						if (p1.getNames().size() == 0) // i nomi null vanno in fondo
							return (p2.getNames().size() == 0) ? 0 : 1;
						if (p2.getNames().size() == 0)
							return -1;
						if (p1.getNames().get(0).getValue() == null) // anche i nomi con value null vanno in fondo
							return (p2.getNames().get(0).getValue() == null) ? 0 : 1;
						if (p2.getNames().get(0).getValue() == null)
							return -1;
						return cognomeNome(p1).compareToIgnoreCase(cognomeNome(p2));
					case 4:
						if (p1.getNames().size() == 0)
							return p2.getNames().size() == 0 ? 0 : 1;
						if (p2.getNames().size() == 0)
							return -1;
						if (p1.getNames().get(0).getValue() == null)
							return (p2.getNames().get(0).getValue() == null) ? 0 : 1;
						if (p2.getNames().get(0).getValue() == null)
							return -1;
						return cognomeNome(p2).compareToIgnoreCase(cognomeNome(p1));
					case 5: // Ordina per anno
						return annoBase(p1) - annoBase(p2);
					case 6:
						if( annoBase(p2) == 9999 ) // Quelli senza anno vanno in fondo
							return -1;
						if( annoBase(p1) == 9999 )
							return annoBase(p2) == 9999 ? 0 : 1;
						return annoBase(p2) - annoBase(p1);
					case 7:	// Ordina per numero di familiari
						return quantiFamiliari(p1) - quantiFamiliari(p2);
					case 8:
						return quantiFamiliari(p2) - quantiFamiliari(p1);
				}
				return 0;
			});
		}
	}

	// Restituisce una stringa con cognome e nome attaccati: 'dalla ValleFrancesco Maria '
	private String cognomeNome(Person tizio) {
		String tutto = tizio.getNames().get(0).getValue();
		String cognome = " ";
		if( tutto.lastIndexOf("/") - tutto.indexOf("/") > 1 )	// se c'è un cognome tra i due '/'
			cognome = tutto.substring( tutto.indexOf("/")+1, tutto.lastIndexOf("/") );
		tutto = cognome.concat( tutto );
		if( tutto.indexOf("/") > 0 )
			tutto = tutto.substring( 0, tutto.indexOf("/") );	// rimuove il cognome tra //
		return tutto;
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

	static int quantiFamiliari( Person uno ) {
		int quanti = 0;
		if( uno != null ) {
			// Famiglie di origine: genitori e fratelli
			List<Family> listaFamiglie = uno.getParentFamilies(gc);
			for( Family famiglia : listaFamiglie  ) {
				quanti += famiglia.getHusbands(gc).size();
				quanti += famiglia.getWives(gc).size();
				for( Person fratello : famiglia.getChildren(gc) )	// solo i figli degli stessi due genitori, non i fratellastri
					if( !fratello.equals(uno) )
						quanti++;
			}
			// Fratellastri e sorellastre
			for( Family famiglia : uno.getParentFamilies(gc) ) {
				for( Person padre : famiglia.getHusbands(gc) ) {
					List<Family> famigliePadre = padre.getSpouseFamilies(gc);
					famigliePadre.removeAll( listaFamiglie );
					for( Family fam : famigliePadre )
						quanti += fam.getChildren(gc).size();
				}
				for( Person madre : famiglia.getWives(gc) ) {
					List<Family> famiglieMadre = madre.getSpouseFamilies(gc);
					famiglieMadre.removeAll( listaFamiglie );
					for( Family fam : famiglieMadre )
						quanti += fam.getChildren(gc).size();
				}
			}
			// Coniugi e figli
			for( Family famiglia : uno.getSpouseFamilies(gc) ) {
				if( U.sesso(uno) == 1 )
					quanti += famiglia.getWives(gc).size();
				else
					quanti += famiglia.getHusbands(gc).size();
				quanti += famiglia.getChildren(gc).size();
			}
			uno.putExtension( "famili", quanti );
		}
		return quanti;
	}

	// menu opzioni nella toolbar
	@Override
	public void onCreateOptionsMenu( Menu menu, MenuInflater inflater ) {
		menu.add( R.string.order_by ).setEnabled(false); // todo forse si otterrebbe un vero titolo con un menu xml
		menu.add( 0,1,0, R.string.id );
		menu.add( 0,2,0, R.string.surname );
		menu.add( 0,3,0, R.string.year );
		menu.add( 0,4,0, R.string.number_relatives );

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
			if( ordine == id*2-1 )
				ordine++;
			else if( ordine == id*2 )
				ordine--;
			else
				ordine = id*2-1;
			ordinaIndividui();
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
			Globale.individuo = idIndi;
			getFragmentManager().beginTransaction().replace( R.id.contenitore_fragment, new Diagram() )
					.addToBackStack(null).commit();
		} else if( id == 1) {	// Famiglia come figlio
			U.qualiGenitoriMostrare( getContext(), gc.getPerson(idIndi), Famiglia.class );
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
						if( !U.controllaFamiglieVuote(getContext(), getActivity()::recreate, true, famiglie) )
							getActivity().recreate();
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
			if( f.getHusbands(gc).indexOf(egli) >= 0 ) {
				f.getHusbandRefs().remove( f.getHusbands(gc).indexOf(egli) );
				famiglie.add( f );
			}
			if( f.getWives(gc).indexOf(egli) >= 0 ) {
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