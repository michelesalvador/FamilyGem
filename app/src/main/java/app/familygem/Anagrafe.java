package app.familygem;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.SearchView;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import org.folg.gedcom.model.EventFact;
import org.folg.gedcom.model.Family;
import org.folg.gedcom.model.Name;
import org.folg.gedcom.model.Person;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import app.familygem.dettaglio.Famiglia;
import static app.familygem.Globale.gc;

public class Anagrafe extends Fragment {

	LinearLayout scatola;
	List<Person> listaIndividui;
	
	@Override
	public void onCreate( Bundle stato ) {
		super.onCreate( stato );
	}

	@Override
	public View onCreateView( LayoutInflater inflater, ViewGroup container, Bundle stato ) {
		View vista = inflater.inflate( R.layout.anagrafe, container, false );
		scatola = vista.findViewById( R.id.anagrafe_scatola );
		setHasOptionsMenu(true);
		listaIndividui = gc.getPeople();
		((AppCompatActivity)getActivity()).getSupportActionBar().setTitle( listaIndividui.size() + " " + getText(R.string.persons) );
		if( Globale.ordineAnagrafe > 0 ) {  // 0 ovvero senza ordinamento
			final boolean gliIdsonoNumerici = verificaIdNumerici();
			Collections.sort(listaIndividui, new Comparator<Person>() {
				public int compare(Person p1, Person p2) {
					switch( Globale.ordineAnagrafe ) {
						case 1:    // ordina per ID Gedcom
							if( gliIdsonoNumerici )
								return idNumerico(p1.getId()) - idNumerico(p2.getId());
							else
								return p1.getId().compareToIgnoreCase(p2.getId());
						case 2:    // ordina per cognome
							if (p1.getNames().size() == 0)    // i nomi null vanno in fondo
								return (p2.getNames().size() == 0) ? 0 : 1;
							if (p2.getNames().size() == 0)
								return -1;
							return cognomeNome(p1).compareToIgnoreCase(cognomeNome(p2));
						case 3:    // ordina per anno
							return annoBase(p1) - annoBase(p2);
						case 4:	// Ordina per numero di familiari
							return quantiFamiliari(p2) - quantiFamiliari(p1);
					}
					return 0;
				}
			});
		}
		elencaIndividui( null );
		return vista;
	}

	void elencaIndividui( String ricerca ) {
		for( final Person tizio : listaIndividui ) {
			if( ricerca == null || cercabile(tizio).contains(ricerca) ) {
				String ruolo = null;
				if( Globale.ordineAnagrafe == 1 ) ruolo = tizio.getId();
				else if( Globale.ordineAnagrafe == 4 ) ruolo = String.valueOf(tizio.getExtension("famili"));
				View vistaIndi = U.mettiIndividuo( scatola, tizio, ruolo );
				vistaIndi.setOnClickListener( new View.OnClickListener() {
					public void onClick( View vista ) {
						// Anagrafe per scegliere il parente e restituire l'id a Diagramma
						if( getActivity().getIntent().getBooleanExtra( "anagrafeScegliParente", false ) ) {
							Intent intent = new Intent();
							intent.putExtra( "idParente", tizio.getId() );
							intent.putExtra( "relazione", getActivity().getIntent().getIntExtra( "relazione", 0 ) );
							getActivity().setResult( AppCompatActivity.RESULT_OK, intent );
							getActivity().finish();
						} else {
							Intent intento = new Intent( getContext(), Individuo.class );
							intento.putExtra( "idIndividuo", tizio.getId() );
							startActivity( intento );
						}
					}
				});
				registerForContextMenu( vistaIndi );
			}
		}
	}

	// Restituisce una stringa con tutti i nomi concatenati e minuscoli
	String cercabile( Person tizio ) {
		String str = "";
		for( Name n : tizio.getNames() ) {
			str += n.getDisplayValue();
			if( n.getNickname() != null )
				str += n.getNickname();
		}
		return str.toLowerCase();
	}

	// Verifica se tutti gli id del Gedcom contengono numeri
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

	// Estrae i soli numeri da un id che può contenere anche lettere
	static int idNumerico( String id ) {
		//return Integer.parseInt( id.replaceAll("\\D+","") );	// sintetico ma lento
		int num = 0;
		int x = 1;
		for( int i = id.length()-1; i >= 0; --i ){
			int c = id.charAt( i );
			if( c > 47 && c < 58 ){
				num += (c-48) * x;
				x *= 10;
			}
		}
		return num;
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

	// riceve una Person e restituisce un annno base della sua esistenza
	private int annoBase( Person p ) {
		int anno = 9999;
		for( EventFact unFatto : p.getEventsFacts() ) {
			if( unFatto.getDate() != null ) {
				String data = unFatto.getDate();
				data = data.substring( data.lastIndexOf(" ")+1 );	// prende l'anno che sta in fondo alla data
				if( data.contains("/") )	// gli anni tipo '1711/12' vengono semplificati in '1712'
					data = data.substring(0,2).concat( data.substring(data.length()-2,data.length()) );
				if( data.matches("[0-9]+") ) {
					anno = Integer.parseInt(data);
					break;
				}
			}
		}
		return anno;
	}

	// riceve una persona e restitusce i due luoghi: iniziale – finale
	public static String dueLuoghi( Person p ) {
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
		Menu subOrdina = menu.addSubMenu(0,0,0, R.string.order_by );
		subOrdina.add( 0,1,0, R.string.none );
		subOrdina.add( 0,2,0, R.string.id );
		subOrdina.add( 0,3,0, R.string.surname );
		subOrdina.add( 0,4,0, R.string.date );
		subOrdina.add( 0,5,0, R.string.number_relatives );
		menu.add( 0,6,0, R.string.new_m );

		// Ricerca nell'Anagrafe
		inflater.inflate( R.menu.cerca, menu );	// già questo basta a far comparire la lente con il campo di ricerca
		MenuItem ricerca = menu.findItem(R.id.ricerca);
		SearchView vistaCerca = (SearchView) ricerca.getActionView();
		vistaCerca.setOnQueryTextListener( new SearchView.OnQueryTextListener() {
			@Override
			public boolean onQueryTextChange( String cercato ) {
				//System.out.println( "tap: " + cercato );
				if( cercato.length() > 1 && listaIndividui.size() < 100 ) {
					scatola.removeAllViews();
					elencaIndividui( cercato );
				} else if( cercato.isEmpty() ) {
					scatola.removeAllViews();
					elencaIndividui( null );
				}
				return false;
			}
			@Override
			public boolean onQueryTextSubmit( String cercato ) {
				//System.out.println("search query submit: " + cercato );
				scatola.removeAllViews();
				elencaIndividui( cercato );
				return false;
			}

		});
		ricerca.setOnActionExpandListener( new MenuItem.OnActionExpandListener() {
			@Override
			public boolean onMenuItemActionExpand( MenuItem i ) {
				return true;
			}
			@Override
			public boolean onMenuItemActionCollapse( MenuItem i ) {
				scatola.removeAllViews();
				elencaIndividui( null );
				return true;
			}
		});
	}
	@Override
	public boolean onOptionsItemSelected( MenuItem item ) {
		switch( item.getItemId() ) {
			case 0:
				return true;
			case 1:
			case 2:
			case 3:
			case 4:
			case 5:
				Globale.ordineAnagrafe = item.getItemId() - 1;
				break;
			case 6:
				Intent intento = new Intent( getContext(), EditaIndividuo.class );
				intento.putExtra( "idIndividuo", "TIZIO_NUOVO" );
				startActivity( intento );
				return true;
			default:
				return false;
		}
		FragmentTransaction ft = getFragmentManager().beginTransaction();
		ft.replace( R.id.contenitore_fragment, new Anagrafe() ).addToBackStack(null).commit();
		return true;
	}

	// Menu contestuale
	View vistaScelta;
	String idIndi;
	@Override
	public void onCreateContextMenu( ContextMenu menu, View vista, ContextMenu.ContextMenuInfo info) {
		vistaScelta = vista;
		idIndi = (String) vista.getTag();
		menu.add(0, 0, 0, R.string.diagram );
		if( !gc.getPerson(idIndi).getParentFamilies(gc).isEmpty() )
			menu.add(0, 1, 0, R.string.family_as_child );
		if( !gc.getPerson(idIndi).getSpouseFamilies(gc).isEmpty() )
			menu.add(0, 2, 0, R.string.family_as_spouse );
		menu.add(0, 3, 0, R.string.modify );
		menu.add(0, 4, 0, R.string.delete );
	}
	@Override
	public boolean onContextItemSelected( MenuItem item ) {
		int id = item.getItemId();
		if( id == 0 ) {	// Apri Diagramma
			Globale.individuo = idIndi;
			getFragmentManager().beginTransaction().replace( R.id.contenitore_fragment, new Diagramma() )
					.addToBackStack(null).commit();
		} else if( id == 1) {	// Famiglia come figlio
			Intent intento = new Intent( getContext(), Famiglia.class );
			intento.putExtra( "idFamiglia", gc.getPerson(idIndi).getParentFamilies(gc).get(0).getId() );
			startActivity( intento );
		} else if( id == 2 ) {	// Famiglia come coniuge
			Intent intento = new Intent( getContext(), Famiglia.class );
			intento.putExtra( "idFamiglia", gc.getPerson(idIndi).getSpouseFamilies(gc).get(0).getId() );
			startActivity( intento );
		} else if( id == 3 ) {	// Modifica
			Intent intento = new Intent( getContext(), EditaIndividuo.class );
			intento.putExtra( "idIndividuo", idIndi );
			startActivity( intento );
		} else if( id == 4 ) {	// Elimina
			elimina( idIndi, getContext(), vistaScelta );
		} else {
			return false;
		}
		return true;
	}

	// Cancella tutti i ref nelle famiglie della tal persona
	static void scollega( String idScollegando ){
		Person egli = gc.getPerson( idScollegando );
		for( Family f : egli.getParentFamilies(gc) )	// scollega i suoi ref nelle famiglie

			f.getChildRefs().remove( f.getChildren(gc).indexOf(egli) );
		for( Family f : egli.getSpouseFamilies(gc) ) {
			if( f.getHusbands(gc).indexOf(egli) >= 0 )
				f.getHusbandRefs().remove( f.getHusbands(gc).indexOf(egli) );
			if( f.getWives(gc).indexOf(egli) >= 0 )
				f.getWifeRefs().remove( f.getWives(gc).indexOf(egli) );
		}
		egli.setParentFamilyRefs( null );	// nell'indi scollega i ref delle famiglie a cui appartiene
		egli.setSpouseFamilyRefs( null );
	}

	// Metodo per eliminare una persona dal gedcom	TODO: ERRORE GRAVISSIMO ELIMINANDO TORNANDO INDIETRO...
	public static void elimina( final String idEliminando, final Context contesto, final View vista ) {
		AlertDialog.Builder alert = new AlertDialog.Builder( contesto );
		alert.setMessage( contesto.getText(R.string.really_delete) + " " + U.epiteto( gc.getPerson(idEliminando) ) + "?");
		alert.setPositiveButton( R.string.delete, new DialogInterface.OnClickListener() {
			public void onClick( DialogInterface dialog, int id ) {
				scollega( idEliminando );
				gc.getPeople().remove( gc.getPerson( idEliminando ) );
				gc.createIndexes();	// necessario
				if( Globale.individuo != null )
					if( Globale.individuo.equals(idEliminando) )
						Globale.individuo = U.trovaRadice(gc);	// TODO dovrebbe essere: trovaParentePiuProssimo
				U.salvaJson();
				if( vista == null )
					contesto.startActivity( new Intent( contesto, Principe.class ) );
				else {
					vista.setVisibility( View.GONE );
					Snackbar.make( vista, R.string.person_deleted, Snackbar.LENGTH_SHORT ).show();
				}
			}
		}).setNeutralButton( R.string.cancel, null )
				.create().show();
	}
}