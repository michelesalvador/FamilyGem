package app.familygem;

import android.content.Intent;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.cardview.widget.CardView;
import org.folg.gedcom.model.Change;
import org.folg.gedcom.model.Family;
import org.folg.gedcom.model.Gedcom;
import org.folg.gedcom.model.Media;
import org.folg.gedcom.model.Note;
import org.folg.gedcom.model.Person;
import org.folg.gedcom.model.Repository;
import org.folg.gedcom.model.Source;
import org.folg.gedcom.model.Submitter;

public class Confrontatore extends AppCompatActivity {

	Class classe; // la classe dominante dell'attività
	int destino;

	@Override
	protected void onCreate( Bundle bandolo ) {
		super.onCreate( bandolo );
		setContentView( R.layout.confronto );

		if( Confronto.getLista().size() > 0 ) {

			ProgressBar barra = findViewById( R.id.confronto_progresso );
			barra.setMax( Confronto.getLista().size() );
			int posizione = Confronto.getInstance().posizione;
			barra.setProgress( posizione );

			((TextView)findViewById( R.id.confronto_stato )).setText( posizione+"/"+Confronto.getLista().size() );

			final Object o = Confronto.getFronte().oggetto;
			final Object o2 = Confronto.getFronte().oggetto2;
			if( o != null ) classe = o.getClass();
			else classe = o2.getClass();
			arredaScheda( Globale.gc, R.id.confronto_vecchio, o );
			arredaScheda( Globale.gc2, R.id.confronto_nuovo, o2 );
			((CardView)findViewById( R.id.confronto_nuovo )).setCardBackgroundColor( getResources().getColor(R.color.evidenzia) );

			destino = 2;

			Button bottoneOk = findViewById(R.id.confronto_bottone_ok);
			bottoneOk.setBackground( AppCompatResources.getDrawable(getApplicationContext(),R.drawable.frecciona) );
			if( o == null ) {
				destino = 1;
				bottoneOk.setText( R.string.add );
				bottoneOk.setBackgroundColor( 0xff00dd00 ); // getResources().getColor(R.color.evidenzia)
				bottoneOk.setHeight( 30 ); // inefficace
			} else if( o2 == null ) {
				destino = 3;
				bottoneOk.setText( R.string.delete );
				bottoneOk.setBackgroundColor( 0xffff0000 );
			} else if( Confronto.getFronte().doppiaOpzione ) {
				// Altro bottone Aggiungi
				Button bottoneAggiungi = new Button( this );
				bottoneAggiungi.setTextSize( TypedValue.COMPLEX_UNIT_SP,16 );
				bottoneAggiungi.setTextColor( 0xFFFFFFFF );
				LinearLayout.LayoutParams params = new LinearLayout.LayoutParams( LinearLayout.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT );
				params.rightMargin = 15;
				params.weight = 3;
				bottoneAggiungi.setLayoutParams( params );
				bottoneAggiungi.setText( R.string.add );
				bottoneAggiungi.setBackgroundColor( 0xff00dd00 );
				bottoneAggiungi.setOnClickListener( new View.OnClickListener() {
					@Override
					public void onClick( View v ) {
						Confronto.getFronte().destino = 1;
						vaiAvanti();
					}
				});
				(( LinearLayout)findViewById( R.id.confronto_bottoni )).addView( bottoneAggiungi, 1 );
			}

			// Accetta la novità
			bottoneOk.setOnClickListener( new View.OnClickListener() {
				@Override
				public void onClick( View vista ) {
					//vista.setEnabled( false );
					Confronto.getFronte().destino = destino;
					vaiAvanti();
				}
			});

			findViewById(R.id.confronto_bottone_ignora ).setOnClickListener( new View.OnClickListener() {
				@Override
				public void onClick( View v ) {
					Confronto.getFronte().destino = 0;
					vaiAvanti();
				}
			});

		} else
			onBackPressed(); // Ritorna a Compara
	}

	void arredaScheda( Gedcom gc, int idScheda, Object o ) {
		String tit = "";
		String txt = "";
		String data = "";
		View carta = findViewById(idScheda);
		ImageView vistaFoto = carta.findViewById( R.id.confronto_foto );
		if( o instanceof Note ) {
			tipoRecord( R.string.shared_note );
			Note n = (Note) o;
			txt = n.getValue();
			data = dataOra( n.getChange() );
		}
		else if( o instanceof Submitter ) {
			tipoRecord( R.string.submitter );
			Submitter s = (Submitter) o;
			tit = s.getName();
			if(s.getEmail()!=null) txt += s.getEmail() + "\n";
			if(s.getAddress()!=null) txt += Dettaglio.indirizzo( s.getAddress() );
			data = dataOra( s.getChange() );
		}
		else if( o instanceof Repository ) {
			tipoRecord( R.string.repository );
			Repository r = (Repository) o;
			tit = r.getName();
			if(r.getAddress()!=null) txt += Dettaglio.indirizzo( r.getAddress() ) + "\n";
			if(r.getEmail()!=null) txt += r.getEmail();
			data = dataOra( r.getChange() );
		}
		else if( o instanceof Media ) {
			tipoRecord( R.string.shared_media );
			Media m = (Media) o;
			if(m.getTitle()!=null) tit = m.getTitle();
			txt = m.getFile();
			data = dataOra( m.getChange() );
			vistaFoto.setVisibility( View.VISIBLE );
			U.dipingiMedia( m, vistaFoto, null );
		}
		else if( o instanceof Source ) {
			tipoRecord( R.string.source );
			Source f = (Source) o;
			if(f.getTitle()!=null) tit = f.getTitle();
			else if(f.getAbbreviation()!=null) tit = f.getAbbreviation();
			if(f.getAuthor()!=null) txt = f.getAuthor()+"\n";
			if(f.getPublicationFacts()!=null) txt += f.getPublicationFacts()+"\n";
			if(f.getText()!=null) txt += f.getText();
			data = dataOra( f.getChange() );
		}
		else if( o instanceof Person ) {
			tipoRecord( R.string.person );
			Person p = (Person) o;
			tit = U.epiteto( p );
			txt = U.dettagli( p, null );
			data = dataOra( p.getChange() );
			vistaFoto.setVisibility( View.VISIBLE );
			U.unaFoto( gc, p, vistaFoto );
		}
		else if( o instanceof Family ) {
			tipoRecord( R.string.family );
			Family f = (Family) o;
			txt = Chiesa.testoFamiglia( this, gc, f );
			data = dataOra( f.getChange() );
		}
		TextView testoTitolo = carta.findViewById( R.id.confronto_titolo );
		if( tit == null || tit.isEmpty() )
			testoTitolo.setVisibility( View.GONE );
		else
			testoTitolo.setText( tit );

		TextView testoTesto = carta.findViewById( R.id.confronto_testo );
		if( txt.isEmpty() )
			testoTesto.setVisibility( View.GONE );
		else {
			if( txt.endsWith( "\n" ) )
				txt = txt.substring( 0, txt.length() - 1 );
			testoTesto.setText( txt );
		}

		View vistaCambi = carta.findViewById( R.id.confronto_data );
		if( data.isEmpty() )
			vistaCambi.setVisibility( View.GONE );
		else
			((TextView)vistaCambi.findViewById( R.id.cambi_testo )).setText( data );

		if( tit.isEmpty() && txt.isEmpty() && data.isEmpty() ) // todo intendi oggetto null?
			carta.setVisibility( View.GONE );
	}

	// Titolo della pagina
	void tipoRecord( int string ) {
		TextView testoTipo = findViewById( R.id.confronto_tipo );
		if( testoTipo.getText().equals( "Persona" ) )
			testoTipo.setText( getString(string) );
	}

	String dataOra( Change cambi ) {
		String dataOra = "";
		if( cambi != null )
			dataOra = cambi.getDateTime().getValue() + " - " + cambi.getDateTime().getTime();
		return dataOra;
	}

	void vaiAvanti() {
		if( Confronto.getInstance().posizione == Confronto.getLista().size() ) {
			startActivity( new Intent( Confrontatore.this, Conferma.class ) );
		} else {
			Confronto.next();
			startActivity( new Intent( Confrontatore.this, Confrontatore.class ) );
		}
	}

	@Override
	public boolean onOptionsItemSelected( MenuItem i ) {
		onBackPressed();
		return true;
	}

	@Override
	public void onBackPressed() {
		super.onBackPressed();
		Confronto.prev();
	}
}