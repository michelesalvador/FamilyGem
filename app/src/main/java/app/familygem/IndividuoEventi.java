package app.familygem;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.util.Pair;
import android.support.v7.app.AlertDialog;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.folg.gedcom.model.Address;
import org.folg.gedcom.model.EventFact;
import org.folg.gedcom.model.GedcomTag;
import org.folg.gedcom.model.Name;
import org.folg.gedcom.model.Note;
import org.folg.gedcom.model.NoteContainer;
import org.folg.gedcom.model.Person;
import org.folg.gedcom.model.SourceCitation;
import org.folg.gedcom.model.SourceCitationContainer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import app.familygem.dettaglio.Archivio;
import app.familygem.dettaglio.Evento;
import app.familygem.dettaglio.Nome;

import static app.familygem.Globale.gc;

public class IndividuoEventi extends Fragment {

	Person uno;

	@Override
	public View onCreateView( LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View vistaEventi = inflater.inflate( R.layout.individuo_scheda, container, false);
		LinearLayout scatola = vistaEventi.findViewById( R.id.contenuto_scheda );
		uno = gc.getPerson( Globale.individuo );
		for( Name nome : uno.getNames())
			piazzaEvento( scatola, getString(R.string.name), U.nomeCognome( nome ), nome );
		for (EventFact fatto : uno.getEventsFacts() ) {
			/*String tit;
			if (fatto.getType() != null)
				tit = fatto.getType();    // Custom event/attribute
			else
				tit = fatto.getDisplayType();    // Standard event/attribute*/
			String tst = "";
			if( fatto.getValue() != null )	tst = fatto.getValue() + "\n";
			if( fatto.getType() != null )	tst += fatto.getType() + "\n";
			if( fatto.getDate() != null ) {
				tst += fatto.getDate() + "\n";
				/*LocalDate data = U.data( fatto.getDate() );
				if( data != null )
					tst += " > " + data +" > "+ data.toString("d MMMM Y") +"\n";
				else tst += "--------\n";*/
			}
			Address indirizzo = fatto.getAddress();
			if( indirizzo != null )	tst += Archivio.indirizzo(indirizzo) + "\n";
			if( fatto.getPlace() != null )	tst += fatto.getPlace() + "\n";
			if( fatto.getCause() != null )	tst += fatto.getCause() + "\n";
			if( tst.endsWith("\n") )	tst = tst.substring( 0, tst.length()-1 );	// Rimuove l'ultimo acapo
			piazzaEvento( scatola, fatto.getDisplayType(), tst, fatto );
		}
		//if( uno.getExtension("folg.more_tags") != null )
		//	piazzaEvento( scatola, "Altro", U.trovaEstensioni(uno.getExtensions()), null, null, null );
		//U.mettiEstensioni( scatola, uno );
		for( Estensione est : U.trovaEstensioni( uno ) ) {
			piazzaEvento( scatola, est.nome, est.testo, est.gedcomTag );
		}
		U.mettiNote( scatola, uno, true );
		U.citaFonti( scatola, uno );
		U.cambiamenti( scatola, uno.getChange() );

		return vistaEventi;
	}

	int sessoCapitato;
	void piazzaEvento( LinearLayout scatola, String titolo, String testo, final Object oggetto ) {
		View vistaFatto = LayoutInflater.from(scatola.getContext()).inflate( R.layout.individuo_eventi_pezzo, scatola, false);
		scatola.addView( vistaFatto );
		((TextView)vistaFatto.findViewById( R.id.evento_titolo )).setText( titolo );
		TextView vistaTesto = vistaFatto.findViewById( R.id.evento_testo );
		if( testo.isEmpty() ) vistaTesto.setVisibility( View.GONE );
		else vistaTesto.setText( testo );
		/*if( note != null ) {
			LinearLayout scatolaNote = vistaFatto.findViewById( R.id.evento_note );
			for( Note nota : note )
				U.mettiNota( scatolaNote, nota, false );
		}*/
		if( oggetto instanceof SourceCitationContainer ) {
			List<SourceCitation> citaFonti = ((SourceCitationContainer)oggetto).getSourceCitations();
			TextView vistaCitaFonti = vistaFatto.findViewById( R.id.evento_fonti );
			if( !citaFonti.isEmpty() ) {
				vistaCitaFonti.setText( String.valueOf(citaFonti.size()) );
				vistaCitaFonti.setVisibility( View.VISIBLE );
			}
		}
		LinearLayout scatolaAltro = vistaFatto.findViewById( R.id.evento_altro );
		if( oggetto instanceof NoteContainer )
			U.mettiNote( scatolaAltro, oggetto, false );
		vistaFatto.setTag( R.id.tag_oggetto, oggetto );
		registerForContextMenu( vistaFatto );
		if( oggetto instanceof Name ) {
			vistaFatto.setOnClickListener( new View.OnClickListener() {
				public void onClick( View v ) {
					//Globale.contenitore = uno;
					//Globale.oggetto = nome;
					Ponte.manda( oggetto, "oggetto" );
					// non serve mandare "contenitore"
					startActivity( new Intent( getContext(), Nome.class ) );
				}
			} );
		} else if( oggetto instanceof EventFact ) {
			if( ((EventFact)oggetto).getTag()!=null && ((EventFact)oggetto).getTag().equals("SEX") ) {
				final Map<String,String> sessi = new LinkedHashMap<>();
				sessi.put( "M", getString(R.string.male) );
				sessi.put( "F", getString(R.string.female) );
				sessi.put( "U", getString(R.string.unknown) );
				vistaTesto.setText( testo );
				sessoCapitato = 0;
				for( Map.Entry<String,String> sex : sessi.entrySet() ) {
					if( testo.equals( sex.getKey() ) ) {
						vistaTesto.setText( sex.getValue() );
						break;
					}
					sessoCapitato++;
				}
				if( sessoCapitato > 2 ) sessoCapitato = -1;
				vistaFatto.setOnClickListener( new View.OnClickListener() {
					public void onClick( View vista ) {
						AlertDialog.Builder dialogo = new AlertDialog.Builder(vista.getContext());
						//dialogo.setTitle();
						final CharSequence[] sessi2 = { getText(R.string.male), getText(R.string.female), getText(R.string.unknown) };
						// todo è sconcertante che non riesco a ricavare da Map sessi un array di stringhe...
						dialogo.setSingleChoiceItems( sessi2, sessoCapitato, new DialogInterface.OnClickListener() {
							public void onClick( DialogInterface dialog, int item) {
								//s.l( item + "  " + sessi.get(item).first );
								((EventFact)oggetto).setValue( new ArrayList<>(sessi.keySet()).get(item) );
								Globale.editato = true;
								getActivity().recreate();
								dialog.dismiss();
							}
						});
						dialogo.create().show();
					}
				});
			} else {
				U.mettiMedia( scatolaAltro, oggetto, false );
				//vistaFatto.setTag( fatto );
				vistaFatto.setOnClickListener( new View.OnClickListener() {
					public void onClick( View vista ) {
						//Globale.contenitore = uno;
						//Globale.oggetto = fatto;
						//intento.putExtra( "fatto", (Serializable)fatto ); // EventFact cannot be cast to java.io.Serializable
						/*intento.putExtra( "oggetto", "EventFact" );
						startActivity( intento );*/
						Ponte.manda( oggetto, "oggetto" );
						Ponte.manda( uno, "contenitore" );
						startActivity( new Intent( getContext(), Evento.class ) );
					}
				});
			}
		} else if( oggetto instanceof GedcomTag ) {
			vistaFatto.setOnClickListener( new View.OnClickListener() {
				public void onClick( View vista ) {
					Ponte.manda( oggetto, "oggetto" );
					Ponte.manda( uno, "contenitore" );
					startActivity( new Intent( getContext(), app.familygem.dettaglio.Estensione.class ) );
				}
			} );
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
		//contenitorePezzo = vista.getTag( R.id.tag_contenitore );
		if( oggettoPezzo instanceof Name ) {
			// Todo sposta nome su e giù
			menu.add( 0, 200, 0, R.string.move_up );
			menu.add( 0, 201, 0, R.string.move_down );
			menu.add( 0, 202, 0, R.string.delete );
		} else if( oggettoPezzo instanceof EventFact ) {
			if( uno.getEventsFacts().indexOf(oggettoPezzo) > 0 )
				menu.add( 0, 203, 0, R.string.move_up );
			if( uno.getEventsFacts().indexOf(oggettoPezzo) < uno.getEventsFacts().size()-1 )
				menu.add( 0, 204, 0, R.string.move_down );
			menu.add( 0, 205, 0, R.string.delete );
		} else if( oggettoPezzo instanceof GedcomTag ) {
			menu.add( 0, 206, 0, R.string.delete );
		} else if( oggettoPezzo instanceof Note ) {
			if( ((Note)oggettoPezzo).getId() != null )
				menu.add( 0, 210, 0, R.string.unlink );
			menu.add( 0, 211, 0, R.string.delete );
		} else if( oggettoPezzo instanceof SourceCitation )
			menu.add( 0, 220, 0, R.string.delete );
	}
	@Override
	public boolean onContextItemSelected( MenuItem item ) {
		List<EventFact> fatti = uno.getEventsFacts();
		switch( item.getItemId() ) {
			// Nome
			case 200:
			case 201: 	// TODO sposta nome
				break;
			case 202: // Elimina
				if( U.preserva(oggettoPezzo) ) return false;
				uno.getNames().remove( oggettoPezzo );
				vistaPezzo.setVisibility( View.GONE );
				Globale.editato = true;
				break;
			// Evento generico
			case 203: // Sposta su
				fatti.add( fatti.indexOf(oggettoPezzo)-1, (EventFact) oggettoPezzo );
				fatti.remove( fatti.lastIndexOf(oggettoPezzo) );
				getActivity().recreate(); // ok ricarica tutta l'activity, anche la testata
				/* Tentativo di ricaricare solo il fragment: non funziona
				FragmentTransaction tr = getFragmentManager().beginTransaction();
				tr.replace( R.id.contenuto_scheda, new IndividuoEventi() ).commit();*/
				break;
			case 204: // Sposta giu
				fatti.add( fatti.indexOf(oggettoPezzo)+2, (EventFact) oggettoPezzo );
				fatti.remove( fatti.indexOf(oggettoPezzo) );
				getActivity().recreate();
				break;
			case 205:
				// todo Conferma elimina
				uno.getEventsFacts().remove( oggettoPezzo );
				vistaPezzo.setVisibility( View.GONE );
				break;
			case 206:
				U.eliminaEstensione( (GedcomTag)oggettoPezzo, uno, vistaPezzo );
				break;
			case 210: 	// Nota
				U.scollegaNota( (Note)oggettoPezzo, uno, vistaPezzo );
				break;
			case 211:
				U.eliminaNota( (Note)oggettoPezzo, uno, vistaPezzo );
				break;
			case 220: 	// Citazione fonte
				// todo conferma : Vuoi eliminare questa citazione della fonte? La fonte continuerà ad esistere.
				uno.getSourceCitations().remove( oggettoPezzo );
				vistaPezzo.setVisibility( View.GONE );
				break;
			default:
				return false;
		}
		U.salvaJson();
		return true;
	}
}