package app.familygem;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import org.folg.gedcom.model.Family;
import org.folg.gedcom.model.Person;
import java.util.List;

import app.familygem.dettaglio.Famiglia;

import static app.familygem.Globale.gc;

public class IndividuoFamiliari extends Fragment {

	View vistaFamiglia;

	@Override
	public View onCreateView( LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState ) {
		//View vistaFamiglia = inflater.inflate(R.layout.individuo_famiglia_pezzo, container, false);
		//vistaFamiglia = new ScrollView( getContext() );
		//vistaFamiglia.setPadding( 4, 4, 4, 4 );
		//vistaFamiglia.setLayoutManager( new LinearLayoutManager(getContext()) );
		//vistaFamiglia.setAdapter( new adattatoreTessera() );*/
		//vistaFamiglia.setOrientation( LinearLayout.VERTICAL );
		vistaFamiglia = inflater.inflate(R.layout.individuo_scheda, container, false);
		Person uno = gc.getPerson( Globale.individuo );
		// Famiglie di origine: genitori e fratelli
		List<Family> listaFamiglie = uno.getParentFamilies(gc);
		for( Family famiglia : listaFamiglie  ) {
			for( Person padre : famiglia.getHusbands(gc) )
				creaTessera( padre, getString(R.string.father), 0, famiglia );
			for( Person madre : famiglia.getWives(gc) )
				creaTessera( madre, getString(R.string.mother), 0, famiglia );
			for( Person fratello : famiglia.getChildren(gc) )	// solo i figli degli stessi due genitori, non i fratellastri
				if( !fratello.equals(uno) )
					creaTessera( fratello, null, 1, famiglia );
		}
		// Fratellastri e sorellastre
		for( Family famiglia : uno.getParentFamilies(gc) ) {
			for( Person padre : famiglia.getHusbands(gc) ) {
				List<Family> famigliePadre = padre.getSpouseFamilies(gc);
				famigliePadre.removeAll( listaFamiglie );
				for( Family fam : famigliePadre )
					for( Person fratellastro : fam.getChildren(gc) )
						creaTessera( fratellastro, null, 2, fam );
			}
			for( Person madre : famiglia.getWives(gc) ) {
				List<Family> famiglieMadre = madre.getSpouseFamilies(gc);
				famiglieMadre.removeAll( listaFamiglie );
				for( Family fam : famiglieMadre )
					for( Person fratellastro : fam.getChildren(gc) )
						creaTessera( fratellastro, null, 2, fam );
			}
		}
		// Coniugi e figli
		for( Family famiglia : uno.getSpouseFamilies(gc) ) {
			if( U.sesso(uno) == 1 )
				for( Person moglie : famiglia.getWives(gc) )
					creaTessera( moglie, getString(R.string.wife), 0, famiglia );
			else
				for( Person marito : famiglia.getHusbands(gc) )
					creaTessera( marito, getString(R.string.husband), 0, famiglia );
			for( Person figlio : famiglia.getChildren(gc) ) {
				creaTessera( figlio, null, 3, famiglia );
			}
		}
		return vistaFamiglia;
	}


	void creaTessera( final Person p, String ruolo, int relazione, Family fam ) {
		LinearLayout scatola = vistaFamiglia.findViewById( R.id.contenuto_scheda );
		if( ruolo == null ) {
			switch( relazione ) {
				case 1:
					ruolo = ( U.sesso(p)==2 )? getString(R.string.sister) : getString(R.string.brother);
					break;
				case 2:
					ruolo = ( U.sesso(p)==2 )? getString(R.string.step_sister) : getString(R.string.step_brother);
					break;
				case 3:
					ruolo = ( U.sesso(p)==2 )? getString(R.string.daughter) : getString(R.string.son);
			}
		}
		View vistaPersona = U.mettiIndividuo( scatola, p, ruolo );
		vistaPersona.setOnClickListener( new View.OnClickListener() {
			public void onClick( View v ) {
				//Globale.individuo = p.getId();
				Intent intento = new Intent( getContext(), Individuo.class);
				intento.putExtra( "idIndividuo", p.getId() );
				intento.putExtra( "scheda", 2 );	// apre la scheda famiglia
				//intento.setFlags( Intent.FLAG_ACTIVITY_NEW_TASK );
				startActivity( intento );
			}
		} );
		registerForContextMenu( vistaPersona );
		vistaPersona.setTag( R.id.tag_famiglia, fam ); // L'unico scopo di questo tag è poter scollegare l'individuo dalla famiglia
	}

	// Menu contestuale
	View vistaScheda;
	String idIndividuo;
	Person pers;
	@Override
	public void onCreateContextMenu( ContextMenu menu, View vista, ContextMenu.ContextMenuInfo info ) {
		vistaScheda = vista;
		idIndividuo = (String)vista.getTag();
		pers = gc.getPerson(idIndividuo);
		// Meglio usare numeri che non confliggano con i menu contestuali delle altre schede individuo
		menu.add(0, 300, 0, R.string.diagram );
		if( !pers.getParentFamilies(gc).isEmpty() )
			menu.add(0, 301, 0, R.string.family_as_child );
		if( !pers.getSpouseFamilies(gc).isEmpty() )
			menu.add(0, 302, 0, R.string.family_as_spouse );
		menu.add(0, 303, 0, R.string.modify );
		menu.add(0, 304, 0, R.string.unlink );
		menu.add(0, 305, 0, R.string.delete );
	}

	@Override
	public boolean onContextItemSelected( MenuItem item ) {
		int id = item.getItemId();
		if( id == 300 ) {    // Diagramma
			s.l( idIndividuo );
			Globale.individuo = idIndividuo;
			startActivity( new Intent( getContext(), Principe.class ) );
		} else if( id == 301 ) {	// Famiglia come figlio
			Intent intento = new Intent( getContext(), Famiglia.class );
			intento.putExtra( "idFamiglia", pers.getParentFamilies(gc).get(0).getId() );
			startActivity( intento );
		} else if( id == 302 ) {	// Famiglia come coniuge
			Intent intento = new Intent( getContext(), Famiglia.class );
			intento.putExtra( "idFamiglia", pers.getSpouseFamilies(gc).get(0).getId() );
			startActivity( intento );
		} else if( id == 303 ) {	// Modifica
			Intent intento = new Intent( getContext(), EditaIndividuo.class );
			intento.putExtra( "idIndividuo", idIndividuo );
			startActivity( intento );
		} else if( id == 304 ) { // Scollega da questa famiglia
			Famiglia.scollega( idIndividuo, (Family)vistaScheda.getTag(R.id.tag_famiglia) );
			vistaScheda.setVisibility( View.GONE );
			U.salvaJson();
		} else if( id == 305 ) {	// Elimina
			Anagrafe.elimina( idIndividuo, getContext(), vistaScheda );
		} else {
			return false;
		}
		return true;
	}
	/*class dopoEliminaFamiliare implements Anagrafe.dopoEliminazione {
		public void esegui( String id ) {
			*//*Intent intento = new Intent( getContext(), Individuo.class );
			intento.putExtra( "scheda", 2 );
			startActivity( intento );*//*
			vistaScheda.setVisibility( View.GONE );
		}
	}*/
}
