package app.familygem;

import android.content.Context;
import android.graphics.Color;
import android.net.Uri;
import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.PopupMenu;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.Toast;
import com.android.installreferrer.api.InstallReferrerClient;
import com.android.installreferrer.api.InstallReferrerStateListener;
import com.android.installreferrer.api.ReferrerDetails;
import org.apache.commons.io.FileUtils;
import org.folg.gedcom.model.ChildRef;
import org.folg.gedcom.model.Family;
import org.folg.gedcom.model.Gedcom;
import org.folg.gedcom.model.Media;
import org.folg.gedcom.model.MediaRef;
import org.folg.gedcom.model.Name;
import org.folg.gedcom.model.ParentFamilyRef;
import org.folg.gedcom.model.Person;
import org.folg.gedcom.model.SpouseFamilyRef;
import org.folg.gedcom.model.SpouseRef;
import org.folg.gedcom.parser.JsonParser;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import app.familygem.visita.ListaMedia;

public class Alberi extends AppCompatActivity {

	List<Map<String,String>> elencoAlberi;
	SimpleAdapter adapter;
	View rotella;
	Fabuloso welcome;
	Esportatore esportatore;

	@Override
	protected void onCreate( Bundle bandolo ) {
		super.onCreate( bandolo );
		setContentView( R.layout.alberi );
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		ListView vistaLista = findViewById( R.id.lista_alberi );
		rotella = findViewById( R.id.alberi_circolo );
		welcome = new Fabuloso( this, R.string.tap_add_tree );
		esportatore = new Esportatore( Alberi.this );

		// Al primissimo avvio
		String referrer = Globale.preferenze.referrer;
		if( referrer != null && referrer.equals("start") )
			recuperaReferrer();
		// Se è stato memorizzato un dataid (che appena usato sarà cancellato)
		else if( referrer != null && referrer.matches("[0-9]{14}") ) {
			new AlertDialog.Builder(this).setTitle( R.string.a_new_tree )
					.setMessage( R.string.you_can_download )
					.setPositiveButton( R.string.download, (dialog, id) -> {
						Facciata.scaricaCondiviso( this, referrer, rotella );
					}).setNeutralButton( R.string.cancel, null ).show();
		} // Se non c'è nessun albero
		else if( Globale.preferenze.alberi.isEmpty() )
			welcome.show();

		if( Globale.preferenze.alberi != null ) {

			// Lista degli alberi genealogici
			elencoAlberi = new ArrayList<>();

			// Dà i dati in pasto all'adattatore
			adapter = new SimpleAdapter( this, elencoAlberi,
					R.layout.pezzo_albero,
					new String[] { "titolo", "dati" },
					new int[] { R.id.albero_titolo, R.id.albero_dati }) {
				// Individua ciascuna vista dell'elenco
				@Override
				public View getView( final int posiz, View convertView, ViewGroup parent ) {
					View vistaAlbero = super.getView( posiz, convertView, parent );
					int idAlbero = Integer.parseInt( elencoAlberi.get(posiz).get("id") );
					Armadio.Cassetto cassetto = Globale.preferenze.getAlbero(idAlbero);
					boolean derivato = cassetto.grado == 20;
					boolean esaurito = cassetto.grado == 30;
					if( derivato ) {
						vistaAlbero.setBackgroundColor( getResources().getColor( R.color.evidenzia ) );
						vistaAlbero.setOnClickListener( v -> {
							if( !AlberoNuovo.confronta(Alberi.this, cassetto, true) ) {
								cassetto.grado = 10; // viene retrocesso
								Globale.preferenze.salva();
								aggiornaLista();
								Toast.makeText(Alberi.this, R.string.something_wrong, Toast.LENGTH_LONG).show();
							}
						});
					} else if ( esaurito ) {
						vistaAlbero.setBackgroundColor( 0xffdddddd );
						vistaAlbero.setOnClickListener( v -> {
							if( !AlberoNuovo.confronta(Alberi.this, cassetto, true) ) {
								cassetto.grado = 10; // viene retrocesso
								Globale.preferenze.salva();
								aggiornaLista();
								Toast.makeText(Alberi.this, R.string.something_wrong, Toast.LENGTH_LONG).show();
							}
						});
					} else {
						vistaAlbero.setBackgroundColor( Color.TRANSPARENT ); // bisogna dirglielo esplicitamente altrimenti colora a caso
						vistaAlbero.setOnClickListener( v -> {
							rotella.setVisibility(View.VISIBLE);
							if( !( Globale.gc != null && idAlbero == Globale.preferenze.idAprendo ) ) // se non è già aperto
								if( !apriGedcom( idAlbero, true ) ) {
									rotella.setVisibility(View.GONE);
									return;
								}
							startActivity( new Intent( Alberi.this, Principe.class ) );
						});
					}
					vistaAlbero.findViewById(R.id.albero_menu).setOnClickListener( vista -> {
						boolean esiste = new File( getFilesDir(), idAlbero + ".json" ).exists();
						PopupMenu popup = new PopupMenu( Alberi.this, vista );
						Menu menu = popup.getMenu();
						if( idAlbero == Globale.preferenze.idAprendo && Globale.daSalvare )
							menu.add(0, -1, 0, R.string.save );
						if( (Globale.preferenze.esperto && derivato) || (Globale.preferenze.esperto && esaurito) )
							menu.add(0, 0, 0, R.string.open );
						if( !esaurito || Globale.preferenze.esperto )
							menu.add(0, 1, 0, R.string.tree_info );
						if( (!derivato && !esaurito) || Globale.preferenze.esperto )
							menu.add(0, 2, 0, R.string.rename );
						if( esiste && (!derivato || Globale.preferenze.esperto) && !esaurito )
							menu.add(0, 3, 0, R.string.media_folders );
						if( !esaurito )
							menu.add(0, 4, 0, R.string.find_errors );
						if( esiste && !derivato && !esaurito ) // non si può ri-condividere un albero ricevuto indietro, anche se sei esperto..
							menu.add(0, 5, 0, R.string.share_tree );
						if( esiste && !derivato && !esaurito && Globale.preferenze.esperto && Globale.preferenze.alberi.size() > 1
								&& cassetto.condivisioni != null && cassetto.grado != 0 ) // cioè dev'essere 9 o 10
							menu.add(0, 6, 0, R.string.compare );
						if( esiste && Globale.preferenze.esperto && !esaurito )
							menu.add(0, 7, 0, R.string.export_gedcom );
						if( esiste && Globale.preferenze.esperto )
							menu.add(0, 8, 0, R.string.make_backup );
						menu.add(0, 9, 0, R.string.delete );
						popup.show();
						popup.setOnMenuItemClickListener( item -> {
							int id = item.getItemId();
							if( id == -1 ) { // Salva
								U.salvaJson( Globale.gc, idAlbero );
								Globale.daSalvare = false;
							} else if( id == 0 ) { // Apre un albero derivato
								apriGedcom( idAlbero, true );
								startActivity( new Intent( Alberi.this, Principe.class ) );
							} else if( id == 1 ) { // Info Gedcom
								Intent intento = new Intent( Alberi.this, InfoAlbero.class );
								intento.putExtra( "idAlbero", idAlbero );
								startActivity(intento);
							} else if( id == 2 ) { // Rinomina albero
								AlertDialog.Builder builder = new AlertDialog.Builder( Alberi.this );
								View vistaMessaggio = getLayoutInflater().inflate(R.layout.albero_nomina, vistaLista, false );
								builder.setView( vistaMessaggio ).setTitle( R.string.title );
								EditText editaNome = vistaMessaggio.findViewById( R.id.nuovo_nome_albero );
								editaNome.setText( elencoAlberi.get(posiz).get("titolo") );
								AlertDialog dialogo = builder.setPositiveButton( R.string.rename, ( dialog, i1 ) -> {
									Globale.preferenze.rinomina( idAlbero, editaNome.getText().toString() );
									aggiornaLista();
								}).setNeutralButton( R.string.cancel, null ).create();
								editaNome.setOnEditorActionListener( (view, action, event) -> {
									if( action == EditorInfo.IME_ACTION_DONE )
										dialogo.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
									return false;
								});
								dialogo.show();
								vistaMessaggio.post( () -> {
									editaNome.requestFocus();
									editaNome.setSelection(editaNome.getText().length());
									InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
									inputMethodManager.showSoftInput(editaNome, InputMethodManager.SHOW_IMPLICIT);
								});
							} else if( id == 3 ) { // Media folders
								startActivity( new Intent( Alberi.this, CartelleMedia.class )
									.putExtra( "idAlbero", idAlbero )
								);
							} else if( id == 4 ) { // Correggi errori
								findErrors(idAlbero, false);
							} else if( id == 5 ) { // Condividi albero
								startActivity( new Intent( Alberi.this, Condivisione.class )
									.putExtra( "idAlbero", idAlbero )
								);
							} else if( id == 6 ) { // Confronta con alberi esistenti
								if( AlberoNuovo.confronta(Alberi.this, cassetto, false) ) {
									cassetto.grado = 20;
									aggiornaLista();
								} else
									Toast.makeText(Alberi.this, R.string.no_results, Toast.LENGTH_LONG).show();
							} else if( id == 7 ) { // Esporta Gedcom
								if( esportatore.apriAlbero(idAlbero) ) {
									String mime = "application/octet-stream";
									String ext = "ged";
									int code = 636;
									if( esportatore.quantiFileMedia() > 0 ) {
										mime = "application/zip";
										ext = "zip";
										code = 6219;
									}
									F.salvaDocumento(Alberi.this, null, idAlbero, mime, ext, code);
								}
							} else if( id == 8 ) { // Fai backup
								if( esportatore.apriAlbero(idAlbero) )
									F.salvaDocumento(Alberi.this, null, idAlbero, "application/zip", "zip", 327);
							} else if( id == 9 ) {	// Elimina albero
								new AlertDialog.Builder( Alberi.this ).setMessage( R.string.really_delete_tree )
										.setPositiveButton( R.string.delete, ( dialog, id1 ) -> {
											eliminaAlbero( Alberi.this, idAlbero );
											aggiornaLista();
										}).setNeutralButton( R.string.cancel, null ).show();
							} else {
								return false;
							}
							return true;
						});
					});
					return vistaAlbero;
				}
			};
			vistaLista.setAdapter(adapter);
			aggiornaLista();
		}

		// Barra personalizzata
		ActionBar barra = getSupportActionBar();
		View barraAlberi = getLayoutInflater().inflate( R.layout.alberi_barra, null );
		barraAlberi.findViewById( R.id.alberi_opzioni ).setOnClickListener( v -> startActivity(
				new Intent( Alberi.this, Opzioni.class) )
		);
		barra.setCustomView( barraAlberi );
		barra.setDisplayShowCustomEnabled( true );

		// FAB
		findViewById(R.id.fab).setOnClickListener( v -> {
			welcome.hide();
			startActivity( new Intent(Alberi.this, AlberoNuovo.class) );
		});

		// Apertura automatica dell'albero
		if( getIntent().getBooleanExtra("apriAlberoAutomaticamente",false) && Globale.preferenze.idAprendo > 0 ) {
			vistaLista.postDelayed(() -> {
				if( Alberi.apriGedcom( Globale.preferenze.idAprendo, false ) ) {
					rotella.setVisibility( View.VISIBLE );
					startActivity( new Intent( Alberi.this, Principe.class ) );
				}
			},100);
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		// Nasconde la rotella, in particolare quando si ritorna indietro a questa activity
		rotella.setVisibility( View.GONE );
	}

	// Essendo Alberi launchMode=singleTask, onRestart viene chiamato anche con startActivity (tranne il primo)
	// però ovviamente solo se Alberi ha chiamato onStop (facendo veloce chiama solo onPause)
	@Override
	protected void onRestart() {
		super.onRestart();
		aggiornaLista();
	}

	// Cerca di recuperare dal Play Store il dataID casomai l'app sia stata installata in seguito ad una condivisione
	// Se trova il dataid propone di scaricare l'albero condiviso
	void recuperaReferrer() {
		InstallReferrerClient irc = InstallReferrerClient.newBuilder(this).build();
		irc.startConnection( new InstallReferrerStateListener() {
			@Override
			public void onInstallReferrerSetupFinished( int risposta ) {
				switch( risposta ) {
					case InstallReferrerClient.InstallReferrerResponse.OK:
						try {
							ReferrerDetails dettagli = irc.getInstallReferrer();
							// Normalmente 'referrer' è una stringa tipo 'utm_source=google-play&utm_medium=organic'
							// Ma se l'app è stata installata dal link nella pagina di condivisione sarà un data-id come '20191003215337'
							String referrer = dettagli.getInstallReferrer();
							if( referrer != null && referrer.matches("[0-9]{14}") ) { // È un data-id
								Globale.preferenze.referrer = referrer;
								new AlertDialog.Builder( Alberi.this ).setTitle( R.string.a_new_tree )
										.setMessage( R.string.you_can_download )
										.setPositiveButton( R.string.download, (dialog, id) -> {
											Facciata.scaricaCondiviso( Alberi.this, referrer, rotella );
										}).setNeutralButton( R.string.cancel, (di, id) -> welcome.show() )
										.setOnCancelListener( d -> welcome.show() ).show();
							} else { // È qualunque altra cosa
								Globale.preferenze.referrer = null; // lo annulla così non lo cercherà più
								welcome.show();
							}
							Globale.preferenze.salva();
							irc.endConnection();
						} catch( Exception e ) {
							U.tosta( Alberi.this, e.getLocalizedMessage() );
						}
						break;
					// App Play Store inesistente sul device o comunque risponde in modo errato
					case InstallReferrerClient.InstallReferrerResponse.FEATURE_NOT_SUPPORTED:
					// Questo non l'ho mai visto comparire
					case InstallReferrerClient.InstallReferrerResponse.SERVICE_UNAVAILABLE:
						Globale.preferenze.referrer = null; // così non torniamo più qui
						Globale.preferenze.salva();
						welcome.show();
				}
			}
			@Override
			public void onInstallReferrerServiceDisconnected() {
				// Mai visto comparire
				U.tosta( Alberi.this, "Install Referrer Service Disconnected" );
			}
		});
	}

	void aggiornaLista() {
		elencoAlberi.clear();
		for( Armadio.Cassetto alb : Globale.preferenze.alberi ) {
			Map<String,String> dato = new HashMap<>(3);
			dato.put( "id", String.valueOf( alb.id ) );
			dato.put( "titolo", alb.nome );
			// Se Gedcom già aperto aggiorna i dati
			if( Globale.gc != null && Globale.preferenze.idAprendo == alb.id && alb.individui < 100 )
				InfoAlbero.aggiornaDati( Globale.gc, alb );
			dato.put( "dati", scriviDati( this, alb ) );
			elencoAlberi.add( dato );
		}
		adapter.notifyDataSetChanged();
	}

	static String scriviDati( Context contesto, Armadio.Cassetto alb ) {
		String dati = alb.individui + " " +
				contesto.getString(alb.individui == 1 ? R.string.person : R.string.persons).toLowerCase();
		if( alb.individui > 1 && alb.generazioni > 0 )
			dati += " - " + alb.generazioni + " " +
					contesto.getString(alb.generazioni == 1 ? R.string.generation : R.string.generations).toLowerCase();
		if( alb.media > 0 )
			dati += " - " + alb.media + " " + contesto.getString(R.string.media).toLowerCase();
		return dati;
	}

	// Apertura del Gedcom temporaneo per estrarne info in Alberi
	static Gedcom apriGedcomTemporaneo( int idAlbero, boolean mettiInGlobale ) {
		Gedcom gc;
		if( Globale.gc != null && Globale.preferenze.idAprendo == idAlbero )
			gc = Globale.gc;
		else {
			gc = leggiJson( idAlbero );
			if( mettiInGlobale ) {
				Globale.gc = gc; // per poter usare ad esempio U.unaFoto()
				Globale.preferenze.idAprendo = idAlbero; // così Globale.gc e Globale.preferenze.idAprendo sono sincronizzati
			}
		}
		return gc;
	}

	// Apertura del Gedcom per editare tutto in Family Gem
	static boolean apriGedcom( int idAlbero, boolean salvaPreferenze ) {
		Globale.gc = leggiJson(idAlbero);
		if( Globale.gc == null )
			return false;
		if( salvaPreferenze ) {
			Globale.preferenze.idAprendo = idAlbero;
			Globale.preferenze.salva();
		}
		Globale.individuo = Globale.preferenze.alberoAperto().radice;
		Globale.numFamiglia = 0; // eventualmente lo resetta se era > 0
		Globale.daSalvare = false; // eventualmente lo resetta se era true
		return true;
	}

	// Legge il Json e restituisce un Gedcom
	static Gedcom leggiJson(int treeId) {
		Gedcom gc;
		try {
			String json = FileUtils.readFileToString(new File(Globale.contesto.getFilesDir(), treeId + ".json"), "UTF-8");
			json = update(json);
			gc = new JsonParser().fromJson(json);
		} catch( Exception e ) {
			Toast.makeText(Globale.contesto, e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
			return null;
		}
		if( gc == null ) {
			Toast.makeText(Globale.contesto, R.string.no_useful_data, Toast.LENGTH_LONG).show();
			return null;
		}
		return gc;
	}

	// Replace Italian with English in Json tree data
	// Introduced in Family Gem 0.8
	static String update(String json) {
		if( json.indexOf("\"zona\":") > 0 ) {
			json = json.replaceAll("\"zona\":", "\"zone\":");
		}
		if( json.indexOf("\"famili\":") > 0 ) {
			json = json.replaceAll("\"famili\":", "\"kin\":");
		}
		return json;
	}

	static void eliminaAlbero( Context contesto, int idAlbero ) {
		File file = new File( contesto.getFilesDir(), idAlbero + ".json");
		file.delete();
		File cartella = contesto.getExternalFilesDir( String.valueOf(idAlbero) );
		eliminaFileCartelle( cartella );
		if( Globale.preferenze.idAprendo == idAlbero ) {
			Globale.gc = null;
		}
		Globale.preferenze.elimina( idAlbero );
	}

	static void eliminaFileCartelle( File fileOrDirectory ) {
		if( fileOrDirectory.isDirectory() )
			for( File child : fileOrDirectory.listFiles() )
				eliminaFileCartelle( child );
		fileOrDirectory.delete();
	}

	@Override
	public void onActivityResult( int requestCode, int resultCode, Intent data ) {
		super.onActivityResult( requestCode, resultCode, data );
		if( resultCode == AppCompatActivity.RESULT_OK ) {
			Uri uri = data.getData();
			boolean result = false;
			if( requestCode == 636 ) { // Esporta il GEDCOM
				result = esportatore.esportaGedcom( uri );
			} else if( requestCode == 6219 ) { // Esporta il GEDCOM zippato coi media
				result = esportatore.esportaGedcomZippato( uri );
			} // Esporta il backup ZIP
			else if( requestCode == 327 ) {
				result = esportatore.esportaBackupZip( null, -1, uri );
			}
			if( result )
				Toast.makeText( Alberi.this, esportatore.messaggioSuccesso, Toast.LENGTH_SHORT ).show();
			else
				Toast.makeText( Alberi.this, esportatore.messaggioErrore, Toast.LENGTH_LONG ).show();
		}
	}

	@Override
	public boolean onOptionsItemSelected( MenuItem i ) {
		onBackPressed();
		return true;
	}

	Gedcom findErrors( final int idAlbero, final boolean correggi ) {
		Gedcom gc = leggiJson( idAlbero );
		if( gc == null ) {
			// todo fai qualcosa per recuperare un file introvabile..?
			return null;
		}
		int errori = 0;
		int num;
		// Radice in preferenze
		Armadio.Cassetto albero = Globale.preferenze.getAlbero( idAlbero );
		Person radica = gc.getPerson( albero.radice );
		// Radice punta ad una persona inesistente
		if( albero.radice != null && radica == null ) {
			if( !gc.getPeople().isEmpty() ) {
				if( correggi ) {
					albero.radice = U.trovaRadice( gc );
					Globale.preferenze.salva();
				} else errori++;
			} else { // albero senza persone
				if( correggi ) {
					albero.radice = null;
					Globale.preferenze.salva();
				} else errori++;
			}
		}
		// Oppure non è indicata una radice in preferenze pur essendoci persone nell'albero
		if( radica == null && !gc.getPeople().isEmpty() ) {
			if( correggi ) {
				albero.radice = U.trovaRadice( gc );
				Globale.preferenze.salva();
			} else errori++;
		}
		// O in preferenze è indicata una radiceCondivisione che non esiste
		Person radicaCondivisa = gc.getPerson( albero.radiceCondivisione );
		if( albero.radiceCondivisione != null && radicaCondivisa == null ) {
			if( correggi ) {
				albero.radiceCondivisione = null; // la elimina e basta
				Globale.preferenze.salva();
			} else errori++;
		}
		// Cerca famiglie vuote o con un solo membro per eliminarle
		for( Family f : gc.getFamilies() ) {
			num = 0;
			for( SpouseRef sr : f.getHusbandRefs() ) {
				num++;
			}
			for( SpouseRef sr : f.getWifeRefs() ) {
				num++;
			}
			for( ChildRef cr : f.getChildRefs() ) {
				num++;
			}
			if( num < 2 ) {
				if( correggi ) {
					gc.getFamilies().remove(f); // così facendo lasci i ref negli individui orfani della famiglia a cui si riferiscono...
					// ma c'è il resto del correttore che li risolve
					break;
				} else errori++;
			}
		}
		// Silently delete empty list of families
		if( gc.getFamilies().isEmpty() && correggi ) {
			gc.setFamilies(null);
		}
		// Riferimenti da una persona alla famiglia dei genitori e dei figli
		for( Person p : gc.getPeople() ) {
			for( ParentFamilyRef pfr : p.getParentFamilyRefs() ) {
				Family fam = gc.getFamily( pfr.getRef() );
				if( fam == null ) {
					if( correggi ) {
						p.getParentFamilyRefs().remove( pfr );
						break;
					} else errori++;
				} else {
					num = 0;
					for( ChildRef cr : fam.getChildRefs() )
						if( cr.getRef() == null ) {
							if( correggi ) {
								fam.getChildRefs().remove(cr);
								break;
							} else errori++;
						} else if( cr.getRef().equals(p.getId()) ) {
							num++;
							if( num > 1 && correggi ) {
								fam.getChildRefs().remove( cr );
								break;
							}
						}
					if( num != 1 ) {
						if( correggi && num == 0 ) {
							p.getParentFamilyRefs().remove( pfr );
							break;
						} else errori++;
					}
				}
			}
			// Remove empty list of parent family refs
			if( p.getParentFamilyRefs().isEmpty() && correggi ) {
				p.setParentFamilyRefs(null);
			}
			for( SpouseFamilyRef sfr : p.getSpouseFamilyRefs() ) {
				Family fam = gc.getFamily(sfr.getRef());
				if( fam == null ) {
					if( correggi ) {
						p.getSpouseFamilyRefs().remove(sfr);
						break;
					} else errori++;
				} else {
					num = 0;
					for( SpouseRef sr : fam.getHusbandRefs() )
						if( sr.getRef() == null ) {
							if( correggi ) {
								fam.getHusbandRefs().remove(sr);
								break;
							} else errori++;
						} else if( sr.getRef().equals(p.getId()) ) {
							num++;
							if( num > 1 && correggi ) {
								fam.getHusbandRefs().remove(sr);
								break;
							}
						}
					for( SpouseRef sr : fam.getWifeRefs() ) {
						if( sr.getRef() == null ) {
							if( correggi ) {
								fam.getWifeRefs().remove(sr);
								break;
							} else errori++;
						} else if( sr.getRef().equals(p.getId()) ) {
							num++;
							if( num > 1 && correggi ) {
								fam.getWifeRefs().remove(sr);
								break;
							}
						}
					}
					if( num != 1 ) {
						if( num == 0 && correggi ) {
							p.getSpouseFamilyRefs().remove(sfr);
							break;
						} else errori++;
					}
				}
			}
			// Remove empty list of spouse family refs
			if( p.getSpouseFamilyRefs().isEmpty() && correggi ) {
				p.setSpouseFamilyRefs(null);
			}
			// Riferimenti a Media inesistenti
			// ok ma SOLO per le persone, forse andrebbe fatto col Visitor per tutti gli altri
			num = 0;
			for( MediaRef mr : p.getMediaRefs() ) {
				Media med = gc.getMedia( mr.getRef() );
				if( med == null ) {
					if( correggi ) {
						p.getMediaRefs().remove( mr );
						break;
					} else errori++;
				} else {
					if( mr.getRef().equals( med.getId() ) ) {
						num++;
						if( num > 1 )
							if( correggi ) {
								p.getMediaRefs().remove( mr );
								break;
							} else errori++;
					}
				}
			}
		}
		// References from each family to the persons belonging to it
		for( Family f : gc.getFamilies() ) {
			// Husbands refs
			for( SpouseRef sr : f.getHusbandRefs() ) {
				Person husband = gc.getPerson(sr.getRef());
				if( husband == null ) {
					if( correggi ) {
						f.getHusbandRefs().remove(sr);
						break;
					} else errori++;
				} else {
					num = 0;
					for( SpouseFamilyRef sfr : husband.getSpouseFamilyRefs() )
						if( sfr.getRef() == null ) {
							if( correggi ) {
								husband.getSpouseFamilyRefs().remove(sfr);
								break;
							} else errori++;
						} else if( sfr.getRef().equals(f.getId()) ) {
							num++;
							if( num > 1 && correggi ) {
								husband.getSpouseFamilyRefs().remove(sfr);
								break;
							}
						}
					if( num != 1 ) {
						if( num == 0 && correggi ) {
							f.getHusbandRefs().remove(sr);
							break;
						} else errori++;
					}

				}
			}
			// Remove empty list of husband refs
			if( f.getHusbandRefs().isEmpty() && correggi ) {
				f.setHusbandRefs(null);
			}
			// Wives refs
			for( SpouseRef sr : f.getWifeRefs() ) {
				Person wife = gc.getPerson(sr.getRef());
				if( wife == null ) {
					if( correggi ) {
						f.getWifeRefs().remove(sr);
						break;
					} else errori++;
				} else {
					num = 0;
					for( SpouseFamilyRef sfr : wife.getSpouseFamilyRefs() )
						if( sfr.getRef() == null ) {
							if( correggi ) {
								wife.getSpouseFamilyRefs().remove(sfr);
								break;
							} else errori++;
						} else if( sfr.getRef().equals(f.getId()) ) {
							num++;
							if( num > 1 && correggi ) {
								wife.getSpouseFamilyRefs().remove(sfr);
								break;
							}
						}
					if( num != 1 ) {
						if( num == 0 && correggi ) {
							f.getWifeRefs().remove(sr);
							break;
						} else errori++;
					}
				}
			}
			// Remove empty list of wife refs
			if( f.getWifeRefs().isEmpty() && correggi ) {
				f.setWifeRefs(null);
			}
			// Children refs
			for( ChildRef cr : f.getChildRefs() ) {
				Person child = gc.getPerson( cr.getRef() );
				if( child == null ) {
					if( correggi ) {
						f.getChildRefs().remove( cr );
						break;
					} else errori++;
				} else {
					num = 0;
					for( ParentFamilyRef pfr : child.getParentFamilyRefs() )
						if( pfr.getRef() == null ) {
							if( correggi ) {
								child.getParentFamilyRefs().remove(pfr);
								break;
							} else errori++;
						} else if( pfr.getRef().equals(f.getId()) ) {
							num++;
							if( num > 1 && correggi ) {
								child.getParentFamilyRefs().remove(pfr);
								break;
							}
						}
					if( num != 1 ) {
						if( num == 0 && correggi ) {
							f.getChildRefs().remove(cr);
							break;
						} else errori++;
					}
				}
			}
			// Remove empty list of child refs
			if( f.getChildRefs().isEmpty() && correggi ) {
				f.setChildRefs(null);
			}
		}

		// Aggiunge un tag 'TYPE' ai name type che non l'hanno
		for( Person person : gc.getPeople() ) {
			for( Name name : person.getNames() ) {
				if( name.getType() != null && name.getTypeTag() == null ) {
					if( correggi ) name.setTypeTag("TYPE");
					else errori++;
				}
			}
		}

		// Aggiunge un tag 'FILE' ai Media che non l'hanno
		ListaMedia visitaMedia = new ListaMedia(gc, 0);
		gc.accept(visitaMedia);
		for( Media med : visitaMedia.lista ) {
			if( med.getFileTag() == null ) {
				if( correggi ) med.setFileTag("FILE");
				else errori++;
			}
		}

		if( !correggi ) {
			AlertDialog.Builder dialog = new AlertDialog.Builder( this );
			dialog.setMessage( errori==0 ? getText(R.string.all_ok) : getString(R.string.errors_found,errori) );
			if( errori > 0 ) {
				dialog.setPositiveButton(R.string.correct, (dialogo, i) -> {
					dialogo.cancel();
					Gedcom gcCorretto = findErrors(idAlbero, true);
					U.salvaJson(gcCorretto, idAlbero);
					Globale.gc = null; // così se era aperto poi lo ricarica corretto
					findErrors(idAlbero, false);    // riapre per ammirere il risultato
					aggiornaLista();
				});
			}
			dialog.setNeutralButton( android.R.string.cancel, null ).show();
		}
		return gc;
	}
}