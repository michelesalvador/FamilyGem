package app.familygem.dettaglio;

import android.content.Intent;
import android.view.View;
import org.folg.gedcom.model.ChildRef;
import org.folg.gedcom.model.EventFact;
import org.folg.gedcom.model.Family;
import org.folg.gedcom.model.ParentFamilyRef;
import org.folg.gedcom.model.Person;
import org.folg.gedcom.model.SpouseFamilyRef;
import org.folg.gedcom.model.SpouseRef;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import app.familygem.Chiesa;
import app.familygem.Dettaglio;
import app.familygem.Individuo;
import app.familygem.R;
import app.familygem.U;
import app.familygem.s;
import static app.familygem.Globale.gc;

public class Famiglia extends Dettaglio {

	Family f;

	@Override
	public void impagina() {
		String idFamiglia = getIntent().getStringExtra("idFamiglia");
		f = gc.getFamily( idFamiglia );
		oggetto = f;
		setTitle( R.string.family );
		vistaId.setText( f.getId() );

		for( Person marito : f.getHusbands(gc) )
			membro( marito, getString(R.string.husband) );	// todo se ci sono figli "Padre" "Madre" ??
		for( Person moglie : f.getWives(gc) )
			membro( moglie, getString(R.string.wife) );
		for( EventFact ef : f.getEventsFacts() ) {
			if( ef.getTag().equals("MARR") )
				metti( getString(R.string.marriage), ef );
		}
		for( Person figlio : f.getChildren(gc) )
			membro( figlio, ( U.sesso(figlio)==2 )? getString(R.string.daughter) : getString(R.string.son) );
		for( EventFact ef : f.getEventsFacts() ) {
			if( !ef.getTag().equals( "MARR" ) )
				metti( ef.getDisplayType(), ef );	// todo controlla che sia questo il titolo che voglio
		}
		mettiEstensioni( f );
		U.mettiNote( box, f, true );
		U.mettiMedia( box, f, true );
		U.citaFonti( box, f );
		U.cambiamenti( box, f.getChange() );
		/* ELIMINABILE
		findViewById( R.id.dettaglio_fab ).setOnClickListener( new View.OnClickListener() {
			@Override
			public void onClick( View vista ) {
				PopupMenu popup = new PopupMenu( Famiglia.this, vista );
				Menu menu = popup.getMenu();
				menu.add( 0, 0, 0, R.string.new_relative );
				if( gc.getPeople().size() > 1 )
					menu.add( 0, 1, 0, R.string.link_person );
				popup.show();
				popup.setOnMenuItemClickListener( new PopupMenu.OnMenuItemClickListener() {
					@Override
					public boolean onMenuItemClick( MenuItem item ) {
						CharSequence[] familiari = { getText(R.string.parent), getText(R.string.child) };
						AlertDialog.Builder builder = new AlertDialog.Builder( Famiglia.this );
						switch( item.getItemId() ) {
							case 0:	// Nuovo familiare
								builder.setItems( familiari, new DialogInterface.OnClickListener() {
									@Override
									public void onClick( DialogInterface dialog, int quale ) {
										Intent intento = new Intent( getApplication(), EditaIndividuo.class );
										intento.putExtra( "idIndividuo", "TIZIO_NUOVO" );
										intento.putExtra( "idFamiglia", f.getId() );
										intento.putExtra( "relazione", quale + 1 );
										startActivity( intento );
									}
								});
								builder.show();
								break;
							case 1:	// Collega persona
								builder.setItems( familiari, new DialogInterface.OnClickListener() {
									@Override
									public void onClick( DialogInterface dialog, int quale ) {
										Intent intento = new Intent( getApplication(), Principe.class );
										intento.putExtra( "anagrafeScegliParente", true );
										intento.putExtra( "relazione", quale + 1 );
										startActivityForResult( intento,1401 );
									}
								});
								builder.show();
								break;
							default:
								return false;
						}
						return true;
					}
				} );
			}
		} );*/

		/* ELIMiNABiLE
		findViewById( R.id.botttone_aggiorna ).setOnClickListener( new View.OnClickListener() {
			@Override
			public void onClick( View v ) {
				//scatola.removeAllViews();
				//onCreate( null );
				Intent intento = new Intent( Famiglia.this, Famiglia.class );
				intento.putExtra( "idFamiglia", f.getId() );
				startActivity( intento );
				*//*Person tizio = gc.getPerson( "I1" );
				SpouseRef sr = new SpouseRef();
				sr.setRef( tizio.getId() );
				if( U.sesso(tizio) == 1 ) f.addHusband( sr );
				else f.addWife( sr );
				// il ref della famiglia nell'indi
				SpouseFamilyRef sfr = new SpouseFamilyRef();
				sfr.setRef( f.getId() );
				tizio.getSpouseFamilyRefs().add( sfr );*//*	// UnsupportedOperationException!
			}
		} );*/
	}

	void membro( final Person p, String ruolo ) {
		//View vistaPersona = LayoutInflater.from(scatola.getContext()).inflate( R.layout.pezzo_individuo, null ); // più breve ma ignora i margin -4
		/*View vistaPersona = LayoutInflater.from(scatola.getContext()).inflate( R.layout.pezzo_individuo, scatola, false);
		scatola.addView( vistaPersona );
		U.arredaIndi( vistaPersona, p, ruolo, U.dueAnni(p), Anagrafe.dueLuoghi(p) );
		vistaPersona.setTag( p.getId() );*/
		View vistaPersona = U.mettiIndividuo( box, p, ruolo );
		vistaPersona.setTag( R.id.tag_oggetto, p ); // per il menu contestuale in Dettaglio
		registerForContextMenu( vistaPersona );
		vistaPersona.setOnClickListener( new View.OnClickListener() {
			public void onClick( View v ) {
				if( !p.getParentFamilies(gc).isEmpty() )
					if( !p.getParentFamilies(gc).get(0).equals( f ) ) {
						Intent intento = new Intent( Famiglia.this, Famiglia.class );
						intento.putExtra( "idFamiglia", p.getParentFamilies(gc).get(0).getId() );
						startActivity( intento );
						return;
					}
				if( !p.getSpouseFamilies(gc).isEmpty() )
					if( !p.getSpouseFamilies(gc).get(0).equals( f ) ) {
						Intent intento = new Intent( Famiglia.this, Famiglia.class );
						intento.putExtra( "idFamiglia", p.getSpouseFamilies(gc).get(0).getId() );
						startActivity( intento );
						return;
					}
				//Globale.individuo = p.getId();
				//startActivity( new Intent( Famiglia.this, Individuo.class) );
				Intent intento = new Intent( Famiglia.this, Individuo.class );
				intento.putExtra( "idIndividuo", p.getId() );
				startActivity( intento );
			}
		} );
		if( unRappresentanteDellaFamiglia == null )
			unRappresentanteDellaFamiglia = p;
	}

	/* ELIMINABILE
	void mettiEvento( String titolo, final EventFact ef ) {
		View vistaPezzo = LayoutInflater.from( box.getContext()).inflate( R.layout.pezzo_fatto, box, false );
		box.addView( vistaPezzo );
		((TextView)vistaPezzo.findViewById( R.id.fatto_titolo )).setText( titolo );
		TextView vistaTesto = vistaPezzo.findViewById( R.id.fatto_testo );
		String tst = "";
		if( ef.getValue() != null )
			tst += ef.getValue() + "\n";
		if( ef.getDate() != null )
			tst += ef.getDate() + "\n";
		if( ef.getPlace() != null )
			tst += ef.getPlace() + "\n";
		if( tst.isEmpty() )
			vistaTesto.setVisibility( View.GONE );
		else {
			tst = tst.substring( 0, tst.length() - 1 );
			vistaTesto.setText( tst );
		}
		// Note
		LinearLayout scatolaNote = vistaPezzo.findViewById( R.id.fatto_note );
		U.mettiNote( scatolaNote, ef, false );
		vistaPezzo.setOnClickListener( new View.OnClickListener() {
			public void onClick( View vista ) {
				//Globale.contenitore = f;
				//Globale.oggetto = ef;
				Ponte.manda( ef, "oggetto" );
				Ponte.manda( f, "contenitore" );
				startActivity( new Intent( Famiglia.this, Evento.class) );
			}
		} );
	}
	// Aggiunge il familiare che è stata scelto in Anagrafe
	@Override
	public void onActivityResult( int requestCode, int resultCode, Intent data ) {
		if( requestCode == 1401  ) {
			if( resultCode == AppCompatActivity.RESULT_OK ) {
				aggrega( gc.getPerson(data.getStringExtra("idParente")), f, data.getIntExtra("relazione",0) );
				U.salvaJson();
			}
		}
	}*/

	// Collega una persona ad una famiglia come genitore o figlio
	public static void aggrega( Person tizio, Family fam, int ruolo ) {
		switch( ruolo ) {
			case 1:	// Genitore
				// il ref dell'indi nella famiglia
				SpouseRef sr = new SpouseRef();
				sr.setRef( tizio.getId() );
				if( U.sesso(tizio) == 1 ) fam.addHusband( sr );
				else fam.addWife( sr );

				// il ref della famiglia nell'indi
				SpouseFamilyRef sfr = new SpouseFamilyRef();
				sfr.setRef( fam.getId() );
				//tizio.getSpouseFamilyRefs().add( sfr );	// no: con lista vuota UnsupportedOperationException
				//List<SpouseFamilyRef> listaSfr = tizio.getSpouseFamilyRefs();	// Non va bene:
				// quando la lista è inesistente, anzichè restituire una ArrayList restituisce una Collections$EmptyList che è IMMUTABILE cioè non ammette add()
				List<SpouseFamilyRef> listaSfr = new ArrayList<>( tizio.getSpouseFamilyRefs() );	// ok
				s.l( listaSfr + " "+ listaSfr.size() +" "+ listaSfr.isEmpty() +" "+ listaSfr.getClass() );
				listaSfr.add( sfr );	// ok
				tizio.setSpouseFamilyRefs( listaSfr );
				break;
			case 2:	// Figlio
				ChildRef cr = new ChildRef();
				cr.setRef( tizio.getId() );
				fam.addChild( cr );
				ParentFamilyRef pfr = new ParentFamilyRef();
				pfr.setRef( fam.getId() );
				//tizio.getParentFamilyRefs().add( pfr );	// UnsupportedOperationException
				List<ParentFamilyRef> listaPfr = new ArrayList<>( tizio.getParentFamilyRefs() );
				listaPfr.add( pfr );
				tizio.setParentFamilyRefs( listaPfr );
		}
	}


	/* ELIMINABILE
	@Override
	public boolean onCreateOptionsMenu( Menu menu ) {
		menu.add( 0, 0, 0, R.string.delete );
		menu.add( 0, 1, 0, "Cippaplipppa" );
		return true;
	}
	@Override
	public boolean onOptionsItemSelected( MenuItem item ) {
		switch( item.getItemId() ) {
			case 0:	// Elimina famiglia
				Chiesa.elimina( f.getId() );
				onBackPressed();
				U.salvaJson();
				return true;
			default:
				return false;
		}
	}*/
	@Override
	public void elimina() {
		// Elimina famiglia
		Chiesa.elimina( f.getId() );
	}

	/* TUTTO SPOSTATO in Dettaglio
	// Menu contestuale
	View vistaScelta;
	String idIndividuo;
	Person pers;
	@Override
	public void onCreateContextMenu( ContextMenu menu, View vista, ContextMenu.ContextMenuInfo info ) {
		vistaScelta = vista;
		idIndividuo = (String)vista.getTag();
		pers = gc.getPerson(idIndividuo);
		menu.add(0, 0, 0, R.string.diagram );
		menu.add(0, 1, 0, R.string.card );
		if( !pers.getParentFamilies(gc).isEmpty() )
			if( !pers.getParentFamilies(gc).get(0).equals( f ) )
				menu.add(0, 2, 0, R.string.family_as_child );
		if( !pers.getSpouseFamilies(gc).isEmpty() )
			if( !pers.getSpouseFamilies(gc).get(0).equals( f ) )
				menu.add(0, 3, 0, R.string.family_as_spouse );
		if( f.getChildren(gc).indexOf(pers) > 0 )
			menu.add( 0, 4, 0, R.string.move_up );
		s.l( f.getChildren(gc).indexOf( pers ) );
		if( f.getChildren(gc).indexOf(pers) < f.getChildren(gc).size()-1 && f.getChildren(gc).indexOf(pers) >= 0 )
																			// così esclude i genitori il cui indice è -1
			menu.add( 0, 5, 0, R.string.move_down );
		menu.add( 0, 6, 0, R.string.modify );
		menu.add( 0, 7, 0, R.string.unlink );
		menu.add( 0, 8, 0, R.string.delete );
	}
	@Override
	public boolean onContextItemSelected( MenuItem item ) {
		int id = item.getItemId();
		if( id == 0 ) {    // Diagramma
			Globale.individuo = idIndividuo;
			startActivity( new Intent( this, Principe.class ) );
		} else if( id == 1 ) {    // Scheda persona
			//Globale.individuo = idIndividuo;
			//startActivity( new Intent( this, Individuo.class ) );
			Intent intento = new Intent( this, Individuo.class );
			intento.putExtra( "idIndividuo", idIndividuo );
			startActivity( intento );
		} else if( id == 2 ) {	// Famiglia come figlio
			Intent intento = new Intent( this, Famiglia.class );
			intento.putExtra( "idFamiglia", pers.getParentFamilies(gc).get(0).getId() );
			startActivity( intento );
		} else if( id == 3 ) {	// Famiglia come coniuge
			Intent intento = new Intent( this, Famiglia.class );
			intento.putExtra( "idFamiglia", pers.getSpouseFamilies(gc).get(0).getId() );
			startActivity( intento );
		} else if( id == 4 ) {	// Figlio sposta su
			ChildRef refBimbo = f.getChildRefs().get( f.getChildren(gc).indexOf(pers) );
			f.getChildRefs().add( f.getChildRefs().indexOf(refBimbo)-1, refBimbo );
			f.getChildRefs().remove( f.getChildRefs().lastIndexOf(refBimbo) );
			U.salvaJson();
			recreate();
		} else if( id == 5 ) {	// Figlio sposta giù
			ChildRef refBimbo = f.getChildRefs().get( f.getChildren(gc).indexOf(pers) );
			f.getChildRefs().add( f.getChildRefs().indexOf(refBimbo)+2, refBimbo );
			f.getChildRefs().remove( f.getChildRefs().indexOf(refBimbo) );
			U.salvaJson();
			recreate();
		} else if( id == 6 ) {	// Modifica
			Intent intento = new Intent( this, EditaIndividuo.class );
			intento.putExtra( "idIndividuo", idIndividuo );
			startActivity( intento );
		} else if( id == 7 ) {	// Scollega
			scollega( idIndividuo, f );
			box.removeView( vistaScelta );
			U.salvaJson();
		} else if( id == 8 ) {	// Elimina
			Anagrafe.elimina( idIndividuo, this, vistaScelta );
		} else {
			return false;
		}
		return true;
	}*/
	/*class dopoEliminaFamiliare implements Anagrafe.dopoEliminazione {
		public void esegui( String id ) {
			// tentativi per far comparire lo SnackBar "Eliminato"
			//onCreate( null ); // IllegalStateException: super.onCreate() already attached
			//try { wait( 1000 ); } catch( InterruptedException e ) {}    //  non funziona
			Intent intento = new Intent( Famiglia.this, Famiglia.class );
			intento.putExtra( "idFamiglia", f.getId() );
			startActivity( intento );
		}
	}*/

	// Rimuove i ref reciproci individuo-famiglia
	public static void scollega( String idIndi, Family fam ) {
		// Rimuove i ref dell'indi nella famiglia
		//if( fam.getHusbands(gc).indexOf(egli) >= 0 )
			//fam.getHusbandRefs().remove( fam.getHusbands(gc).indexOf(egli) );	ok ma rimuove solo il primo
		Iterator<SpouseRef> refiSposo = fam.getHusbandRefs().iterator();
		while( refiSposo.hasNext() )
			//SpouseRef sr = refiSposo.next();
			if( refiSposo.next().getRef().equals(idIndi) )
				refiSposo.remove();
		// poi nella famiglia rimane   "husbandRefs":[]   vuoto, todo forse bisognerebbe eliminarlo?

		//if( fam.getWives(gc).indexOf(egli) >= 0 )
		//	fam.getWifeRefs().remove( fam.getWives(gc).indexOf(egli) );
		refiSposo = fam.getWifeRefs().iterator();
		while( refiSposo.hasNext() )
			if( refiSposo.next().getRef().equals(idIndi) )
				refiSposo.remove();

		//if( fam.getChildren(gc).indexOf(egli) >= 0 )
		//	fam.getChildRefs().remove( fam.getChildren(gc).indexOf(egli) );
		Iterator<ChildRef> refiFiglio = fam.getChildRefs().iterator();
		while( refiFiglio.hasNext() )
			if( refiFiglio.next().getRef().equals(idIndi) )
				refiFiglio.remove();

		// Rimuove i ref della famiglia nell'indi
		/*Person egli = gc.getPerson( idIndi );
		for( SpouseFamilyRef sfr : egli.getSpouseFamilyRefs() )
			if( sfr.getRef().equals(fam.getId()) ) {
				egli.getSpouseFamilyRefs().remove( sfr );
				break;	// purtroppo così elimina solo il primo, non altri eventuali ref alla stessa famiglia
			}*/
		Iterator<SpouseFamilyRef> iterSfr = gc.getPerson(idIndi).getSpouseFamilyRefs().iterator();
		while( iterSfr.hasNext() )
			if( iterSfr.next().getRef().equals(fam.getId()) )
				iterSfr.remove();
		// anche qui nell'indi rimane un  "fams":[]  vuoto	todo eliminarlo?

		/*for( ParentFamilyRef pfr : egli.getParentFamilyRefs() )
			if( pfr.getRef().equals(fam.getId()) ) {
				egli.getParentFamilyRefs().remove( pfr );
				break;
			}*/
		Iterator<ParentFamilyRef> iterPfr = gc.getPerson(idIndi).getParentFamilyRefs().iterator();
		while( iterPfr.hasNext() )
			if( iterPfr.next().getRef().equals(fam.getId()) )
				iterPfr.remove();
	}
}
