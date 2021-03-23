package app.familygem;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import org.folg.gedcom.model.Family;
import org.folg.gedcom.model.Person;
import org.folg.gedcom.model.SpouseFamilyRef;
import java.util.Collections;
import java.util.List;
import app.familygem.constants.Gender;
import app.familygem.dettaglio.Famiglia;
import static app.familygem.Globale.gc;

public class IndividuoFamiliari extends Fragment {

	private View vistaFamiglia;
	Person uno;

	@Override
	public View onCreateView( LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState ) {
		vistaFamiglia = inflater.inflate(R.layout.individuo_scheda, container, false);
		if( gc != null ) {
			uno = gc.getPerson( Globale.individuo );
			if( uno != null ) {
				/* ToDo Mostrare/poter settare nelle famiglie geniotriali il pedigree, in particolare 'adopted'
				LinearLayout scatola = vistaFamiglia.findViewById( R.id.contenuto_scheda );
				for( ParentFamilyRef pfr : uno.getParentFamilyRefs() ) {
					U.metti( scatola, "Ref", pfr.getRef() );
					U.metti( scatola, "Primary", pfr.getPrimary() ); // Custom tag _PRIM _PRIMARY
					U.metti( scatola, "Relationship Type", pfr.getRelationshipType() ); // Tag PEDI (pedigree)
					for( Estensione altroTag : U.trovaEstensioni( pfr ) )
						U.metti( scatola, altroTag.nome, altroTag.testo );
				} */
				// Famiglie di origine: genitori e fratelli
				List<Family> listaFamiglie = uno.getParentFamilies(gc);
				for( Family famiglia : listaFamiglie  ) {
					for( Person padre : famiglia.getHusbands(gc) )
						creaTessera( padre, 1, famiglia );
					for( Person madre : famiglia.getWives(gc) )
						creaTessera( madre, 1, famiglia );
					for( Person fratello : famiglia.getChildren(gc) )	// solo i figli degli stessi due genitori, non i fratellastri
						if( !fratello.equals(uno) )
							creaTessera( fratello, 3, famiglia );
				}
				// Fratellastri e sorellastre
				for( Family famiglia : uno.getParentFamilies(gc) ) {
					for( Person padre : famiglia.getHusbands(gc) ) {
						List<Family> famigliePadre = padre.getSpouseFamilies(gc);
						famigliePadre.removeAll( listaFamiglie );
						for( Family fam : famigliePadre )
							for( Person fratellastro : fam.getChildren(gc) )
								creaTessera( fratellastro, 4, fam );
					}
					for( Person madre : famiglia.getWives(gc) ) {
						List<Family> famiglieMadre = madre.getSpouseFamilies(gc);
						famiglieMadre.removeAll( listaFamiglie );
						for( Family fam : famiglieMadre )
							for( Person fratellastro : fam.getChildren(gc) )
								creaTessera( fratellastro, 4, fam );
					}
				}
				// Coniugi e figli
				for( Family famiglia : uno.getSpouseFamilies(gc) ) {
					for( Person marito : famiglia.getHusbands(gc) )
						if( !marito.equals(uno) )
							creaTessera( marito, 2, famiglia );
					for( Person moglie : famiglia.getWives(gc) )
						if( !moglie.equals(uno) )
							creaTessera( moglie, 2, famiglia );
					for( Person figlio : famiglia.getChildren(gc) ) {
						creaTessera( figlio, 5, famiglia );
					}
				}
			}
		}
		return vistaFamiglia;
	}

	void creaTessera(final Person p, int relation, Family fam) {
		LinearLayout scatola = vistaFamiglia.findViewById( R.id.contenuto_scheda );
		int role = 0;
		if( Gender.isMale(p) ) {
			switch( relation ) {
				case 1: role = R.string.father; break;
				case 2: role = R.string.husband; break;
				case 3: role = R.string.brother; break;
				case 4: role = R.string.half_brother; break;
				case 5: role = R.string.son;
			}
		} else if( Gender.isFemale(p) ) {
			switch( relation ) {
				case 1: role = R.string.mother; break;
				case 2: role = R.string.wife; break;
				case 3:	role = R.string.sister; break;
				case 4: role = R.string.half_sister; break;
				case 5: role = R.string.daughter;
			}
		} else {
			switch( relation ) {
				case 1: role = R.string.parent; break;
				case 2: role = R.string.spouse; break;
				case 3:	role = R.string.sibling; break;
				case 4: role = R.string.half_sibling; break;
				case 5: role = R.string.child;
			}
		}
		View vistaPersona = U.mettiIndividuo( scatola, p, getString(role) );
		vistaPersona.setOnClickListener( v -> {
			getActivity().finish(); // Rimuove l'attività attale dallo stack
			Memoria.replacePrimo( p );
			Intent intento = new Intent(getContext(), Individuo.class);
			intento.putExtra( "scheda", 2 );	// apre la scheda famiglia
			startActivity( intento );
		});
		registerForContextMenu( vistaPersona );
		vistaPersona.setTag( R.id.tag_famiglia, fam ); // Il principale scopo di questo tag è poter scollegare l'individuo dalla famiglia
		                                               // ma è usato anche qui sotto per spostare i molteplici matrimoni
	}

	private void spostaRiferimentoFamiglia( int direzione ) {
		Collections.swap( uno.getSpouseFamilyRefs(), posFam, posFam + direzione );
		getActivity().getSupportFragmentManager().beginTransaction().detach( this ).attach( this ).commit();
		U.salvaJson( true, uno );
	}

	// Menu contestuale
	private String idIndividuo;
	private Person pers;
	private Family familia;
	private int posFam;
	@Override
	public void onCreateContextMenu( ContextMenu menu, View vista, ContextMenu.ContextMenuInfo info ) {
		idIndividuo = (String)vista.getTag();
		pers = gc.getPerson(idIndividuo);
		familia = (Family)vista.getTag( R.id.tag_famiglia );
		// posizione della famiglia coniugale per chi ne ha più di una
		posFam = -1;
		if( uno.getSpouseFamilyRefs().size() > 1 && !familia.getChildren(gc).contains(pers) ) { // solo i coniugi, non i figli
			List<SpouseFamilyRef> refi = uno.getSpouseFamilyRefs();
			for( SpouseFamilyRef sfr : refi )
				if( sfr.getRef().equals( familia.getId() ) )
					posFam = refi.indexOf( sfr );
		}
		// Meglio usare numeri che non confliggano con i menu contestuali delle altre schede individuo
		menu.add( 0, 300, 0, R.string.diagram );
		String[] familyLabels = Diagram.getFamilyLabels( getContext(), pers );
		if( familyLabels[0] != null )
			menu.add( 0, 301, 0, familyLabels[0] );
		if( familyLabels[1] != null )
			menu.add( 0, 302, 0, familyLabels[1] );
		if( posFam > 0 )
			menu.add( 0, 303, 0, R.string.move_before );
		if( posFam >= 0 && posFam < uno.getSpouseFamilyRefs().size()-1 )
			menu.add( 0, 304, 0, R.string.move_after );
		menu.add( 0, 305, 0, R.string.modify );
		menu.add( 0, 306, 0, R.string.unlink );
		if( !pers.equals(uno) ) // Qui non può eliminare sè stesso
			menu.add( 0, 307, 0, R.string.delete );
	}

	@Override
	public boolean onContextItemSelected( MenuItem item ) {
		int id = item.getItemId();
		if( id == 300 ) {	// Diagramma
			U.qualiGenitoriMostrare( getContext(), pers, 1 );
		} else if( id == 301 ) {	// Famiglia come figlio
			U.qualiGenitoriMostrare( getContext(), pers, 2 );
		} else if( id == 302 ) {	// Famiglia come coniuge
			U.qualiConiugiMostrare( getContext(), pers, familia );
		} else if( id == 303 ) {	// Sposta su
			spostaRiferimentoFamiglia( -1 );
		} else if( id == 304 ) {	// Sposta giù
			spostaRiferimentoFamiglia( 1 );
		} else if( id == 305 ) {	// Modifica
			Intent intento = new Intent( getContext(), EditaIndividuo.class );
			intento.putExtra( "idIndividuo", idIndividuo );
			startActivity( intento );
		} else if( id == 306 ) { // Scollega da questa famiglia
			Famiglia.scollega( idIndividuo, familia );
			aggiorna();
			U.controllaFamiglieVuote(getContext(), this::aggiorna, false, familia);
			U.salvaJson( true, familia, pers );
		} else if( id == 307 ) {	// Elimina
			new AlertDialog.Builder(getContext()).setMessage(R.string.really_delete_person)
					.setPositiveButton(R.string.delete, (dialog, i) -> {
						Anagrafe.eliminaPersona(getContext(), idIndividuo);
						aggiorna();
						U.controllaFamiglieVuote(getContext(), this::aggiorna, false, familia);
					}).setNeutralButton(R.string.cancel, null).show();
		} else {
			return false;
		}
		return true;
	}

	// Rinfresca il contenuto del frammento Familiari
	void aggiorna() {
		getActivity().getSupportFragmentManager().beginTransaction().detach(this).attach(this).commit();
		getActivity().invalidateOptionsMenu();
		// todo aggiorna la data cambiamento nella scheda Fatti
	}
}
