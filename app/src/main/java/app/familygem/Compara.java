// Attività di introduzione all'importazione delle novità in un albero già esistente

package app.familygem;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
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
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class Compara extends AppCompatActivity {

	Date sharingDate;
	SimpleDateFormat changeDateFormat;

	@Override
	protected void onCreate( Bundle bandolo ) {
		super.onCreate( bandolo );
		setContentView( R.layout.compara );
		int idAlbero = getIntent().getIntExtra("idAlbero",1); // Albero vecchio
		int idAlbero2 = getIntent().getIntExtra("idAlbero2",1); // Albero nuovo ricevuto in condivisione
		Global.treeId2 = idAlbero2; // servirà alle immagini di Confrontatore e a Conferma
		Global.gc = Alberi.apriGedcomTemporaneo( idAlbero, true );
		Global.gc2 = Alberi.apriGedcomTemporaneo( idAlbero2, false );
		if( Global.gc == null || Global.gc2 == null ) {
			Toast.makeText( this, R.string.no_useful_data, Toast.LENGTH_LONG ).show();
			onBackPressed();
			return;
		}

		TimeZone.setDefault( TimeZone.getTimeZone("Europe/Rome") ); // riconduce tutte le date al fuso orario di Aruba
		try {
			SimpleDateFormat formatoDataId = new SimpleDateFormat("yyyyMMddHHmmss", Locale.ENGLISH);
			sharingDate = formatoDataId.parse(getIntent().getStringExtra("idData"));
		} catch( ParseException e ) {
			e.printStackTrace();
		}

		changeDateFormat = new SimpleDateFormat( "d MMM yyyyHH:mm:ss", Locale.ENGLISH );
		Confronto.reset(); // Necessario svuotarlo, ad esempio dopo un cambio di configurazione

		// Confronta tutti i record dei due Gedcom
		for( Family o2 : Global.gc2.getFamilies() )
			confronta( Global.gc.getFamily(o2.getId()), o2, 7 );
		for( Family o : Global.gc.getFamilies() )
			riconfronta( o, Global.gc2.getFamily(o.getId()), 7 );

		for( Person o2 : Global.gc2.getPeople() )
			confronta( Global.gc.getPerson(o2.getId()), o2, 6 );
		for( Person o : Global.gc.getPeople() )
			riconfronta( o, Global.gc2.getPerson(o.getId()), 6 );

		for( Source o2 : Global.gc2.getSources() )
			confronta( Global.gc.getSource(o2.getId()), o2, 5 );
		for( Source o : Global.gc.getSources() )
			riconfronta( o, Global.gc2.getSource(o.getId()), 5 );

		for( Media o2 : Global.gc2.getMedia() )
			confronta( Global.gc.getMedia(o2.getId()), o2, 4 );
		for( Media o : Global.gc.getMedia() )
			riconfronta( o, Global.gc2.getMedia(o.getId()), 4 );

		for( Repository o2 : Global.gc2.getRepositories() )
			confronta( Global.gc.getRepository(o2.getId()), o2, 3 );
		for( Repository o : Global.gc.getRepositories() )
			riconfronta( o, Global.gc2.getRepository(o.getId()), 3 );

		for( Submitter o2 : Global.gc2.getSubmitters() )
			confronta( Global.gc.getSubmitter(o2.getId()), o2, 2 );
		for( Submitter o : Global.gc.getSubmitters() )
			riconfronta( o, Global.gc2.getSubmitter(o.getId()), 2 );

		for( Note o2 : Global.gc2.getNotes() )
			confronta( Global.gc.getNote(o2.getId()), o2, 1 );
		for( Note o : Global.gc.getNotes() )
			riconfronta( o, Global.gc2.getNote(o.getId()), 1 );

		Settings.Tree tree2 = Global.settings.getTree(idAlbero2);
		if( Confronto.getLista().isEmpty() ) {
			setTitle(R.string.tree_without_news);
			if( tree2.grade != 30 ) {
				tree2.grade = 30;
				Global.settings.save();
			}
		} else if( tree2.grade != 20 ) {
			tree2.grade = 20;
			Global.settings.save();
		}

		arredaScheda(Global.gc, idAlbero, R.id.compara_vecchio);
		arredaScheda(Global.gc2, idAlbero2, R.id.compara_nuovo);

		((TextView)findViewById(R.id.compara_testo)).setText(getString(R.string.tree_news_imported, Confronto.getLista().size()));

		Button botton1 = findViewById(R.id.compara_bottone1);
		Button botton2 = findViewById(R.id.compara_bottone2);
		if( Confronto.getLista().size() > 0 ) {
			// Rivedi singolarmente
			botton1.setOnClickListener(v -> {
				startActivity(new Intent(Compara.this, Confrontatore.class).putExtra("posizione", 1));
			});
			// Accetta tutto
			botton2.setOnClickListener(v -> {
				v.setEnabled(false);
				Confronto.get().quanteScelte = 0;
				for( Confronto.Fronte fronte : Confronto.getLista() ) {
					if( fronte.doppiaOpzione )
						Confronto.get().quanteScelte++;
				}
				Intent intent = new Intent(Compara.this, Confrontatore.class);
				intent.putExtra("posizione", 1);
				if( Confronto.get().quanteScelte > 0 ) { // Dialogo di richiesta revisione
					new AlertDialog.Builder(this)
							.setTitle( Confronto.get().quanteScelte == 1 ? getString(R.string.one_update_choice)
									: getString(R.string.many_updates_choice, Confronto.get().quanteScelte) )
							.setMessage(R.string.updates_replace_add)
							.setPositiveButton( android.R.string.ok, (dialog,id) -> {
								Confronto.get().autoProsegui = true;
								Confronto.get().scelteFatte = 1;
								startActivity( intent );
							}).setNeutralButton( android.R.string.cancel, (dialog,id) -> botton2.setEnabled(true) )
							.setOnCancelListener( dialog -> botton2.setEnabled(true) ).show();
				} else { // Avvio in automatico
					Confronto.get().autoProsegui = true;
					startActivity( intent );
				}
			});
		} else {
			botton1.setText(R.string.delete_imported_tree);
			botton1.setOnClickListener(v -> {
				Alberi.deleteTree(Compara.this, idAlbero2);
				onBackPressed();
			});
			botton2.setVisibility(View.GONE);
		}
	}

	@Override
	protected void onRestart() {
		super.onRestart();
		findViewById(R.id.compara_bottone2 ).setEnabled( true ); // se eventualmente
		Confronto.get().autoProsegui = false; // Lo resetta se eventualmente era stato scelto l'automatismo
	}

	// Vede se aggiungere i due oggetti alla lista di quelli da valutare
	private void confronta( Object o, Object o2, int tipo ) {
		Change c = getCambi(o);
		Change c2 = getCambi(o2);
		int modifica = 0;
		if( o == null && isRecent(c2) ) // o2 è stata aggiunto nel nuovo albero --> AGGIUNGI
			modifica = 1;
		else {
			if( c == null && c2 != null )
				modifica = 1;
			else if( c != null && c2 != null &&
					!(c.getDateTime().getValue().equals(c2.getDateTime().getValue()) // le due date devono essere diverse
					&& c.getDateTime().getTime().equals(c2.getDateTime().getTime())) ) {
				if(	isRecent(c) && isRecent(c2) ) { // entrambi modificati dopo la condivisione --> AGGIUNGI/SOSTITUISCI
					modifica = 2;
				} else if( isRecent(c2) ) // solo o2 è stato modificato --> SOSTITUISCI
					modifica = 1;
			}
		}
		if( modifica > 0 ) {
			Confronto.Fronte fronte = Confronto.addFronte( o, o2, tipo );
			if( modifica == 2 )
				fronte.doppiaOpzione = true;
		}
	}

	// Idem per i rimanenti oggetti eliminati nell'albero vecchio
	private void riconfronta( Object o, Object o2, int tipo ) {
		if( o2 == null && !isRecent(getCambi(o)) )
			Confronto.addFronte( o, null, tipo );
	}

	/** Find if a top-level record has been modified after the date of sharing
	 * @param change Actual change date of the top-level record
	 * @return true if the record is more recent than the date of sharing
	 */
	private boolean isRecent(Change change) {
		boolean itIs = false;
		if( change != null && change.getDateTime() != null ) {
			try { // todo con time null
				String zoneId = U.castaJsonString(change.getExtension("zone"));
				if( zoneId == null )
					zoneId = "UTC";
				TimeZone timeZone = TimeZone.getTimeZone(zoneId);
				changeDateFormat.setTimeZone(timeZone);
				Date recordDate = changeDateFormat.parse(change.getDateTime().getValue() + change.getDateTime().getTime());
				itIs = recordDate.after(sharingDate);
				//long oreSfaso = TimeUnit.MILLISECONDS.toMinutes( timeZone.getOffset(dataOggetto.getTime()) );
				//s.l( dataOggetto+"\t"+ ok +"\t"+ (oreSfaso>0?"+":"")+oreSfaso +"\t"+ timeZone.getID() );
			} catch( ParseException e ) {}
		}
		return itIs;
	}

	Change getCambi( Object ogg ) {
		Change cambio = null;
		try {
			cambio = (Change) ogg.getClass().getMethod( "getChange" ).invoke( ogg );
		} catch( Exception e ) {}
		return cambio;
	}

	void arredaScheda( Gedcom gc, int idAlbero, int idScheda ) {
		CardView carta = findViewById(idScheda);
		Settings.Tree tree = Global.settings.getTree(idAlbero);
		((TextView)carta.findViewById(R.id.confronto_titolo)).setText(tree.title);
		((TextView)carta.findViewById(R.id.confronto_testo)).setText(Alberi.scriviDati(this, tree));
		if( idScheda == R.id.compara_nuovo ) {
			if( tree.grade == 30 )
				carta.setCardBackgroundColor(0xffdddddd);
			else
				carta.setCardBackgroundColor(getResources().getColor(R.color.evidenzia));
			Submitter autore = gc.getSubmitter(tree.shares.get(tree.shares.size() - 1).submitter);
			String txt = "";
			if( autore != null ) {
				String nome = autore.getName();
				if( nome == null || nome.isEmpty() )
					nome = getString(android.R.string.unknownName);
				txt += getString(R.string.sent_by, nome) + "\n";
			}
			//if( Confronto.getLista().size() > 0 )
			//	txt += "Updates:\t";
			for( int i = 7; i > 0; i-- ) {
				txt += scriviDifferenze( i );
			}
			if( txt.endsWith("\n") ) txt = txt.substring( 0, txt.length()-1 );
			((TextView)carta.findViewById( R.id.confronto_sottotesto )).setText( txt );
			carta.findViewById( R.id.confronto_sottotesto ).setVisibility( View.VISIBLE );
		}
		carta.findViewById( R.id.confronto_data ).setVisibility( View.GONE );
	}

	int[] singolari = { R.string.shared_note, R.string.submitter, R.string.repository, R.string.shared_media, R.string.source, R.string.person, R.string.family };
	int[] plurali = { R.string.shared_notes, R.string.submitters, R.string.repositories, R.string.shared_medias, R.string.sources, R.string.persons, R.string.families };
	String scriviDifferenze( int tipo ) {
		int modifiche = 0;
		for( Confronto.Fronte fronte : Confronto.getLista() ) {
			if( fronte.tipo == tipo ) {
				modifiche++;
			}
		}
		String testo = "";
		if( modifiche > 0 ) {
			tipo--;
			int definizione = modifiche==1 ? singolari[tipo] : plurali[tipo];
			testo = "\t\t+" + modifiche + " " + getString( definizione ).toLowerCase() + "\n";
		}
		return testo;
	}

	@Override
	public boolean onOptionsItemSelected( MenuItem i ) {
		onBackPressed();
		return true;
	}

	@Override
	public void onBackPressed() {
		super.onBackPressed();
		Confronto.reset(); // resetta il singleton Confronto
	}
}