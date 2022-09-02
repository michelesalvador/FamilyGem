package app.familygem;

import android.content.Context;
import android.net.Uri;
import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.PopupMenu;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.work.WorkManager;
import android.os.Handler;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;
import com.android.installreferrer.api.InstallReferrerClient;
import com.android.installreferrer.api.InstallReferrerStateListener;
import com.android.installreferrer.api.ReferrerDetails;
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
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import app.familygem.visitor.ListaMedia;

public class Alberi extends AppCompatActivity {

	List<Map<String,String>> elencoAlberi;
	SimpleAdapter adapter;
	View rotella;
	Fabuloso welcome;
	Esportatore esportatore;
	private boolean autoOpenedTree; // To open automatically the tree at startup only once
	// The birthday notification IDs are stored to display the relative person only once
	private ArrayList<Integer> consumedNotifications = new ArrayList<>();

	@Override
	protected void onCreate(Bundle savedState) {
		super.onCreate(savedState);
		setContentView(R.layout.alberi);
		ListView vistaLista = findViewById(R.id.lista_alberi);
		rotella = findViewById(R.id.alberi_circolo);
		welcome = new Fabuloso(this, R.string.tap_add_tree);
		esportatore = new Esportatore(Alberi.this);

		// Al primissimo avvio
		String referrer = Global.settings.referrer;
		if( referrer != null && referrer.equals("start") )
			recuperaReferrer();
		// Se è stato memorizzato un dataid (che appena usato sarà cancellato)
		else if( referrer != null && referrer.matches("[0-9]{14}") ) {
			new AlertDialog.Builder(this).setTitle(R.string.a_new_tree)
					.setMessage(R.string.you_can_download)
					.setPositiveButton(R.string.download, (dialog, id) -> {
						Facciata.scaricaCondiviso(this, referrer, rotella);
					}).setNeutralButton(R.string.cancel, null).show();
		} // Se non c'è nessun albero
		else if( Global.settings.trees.isEmpty() )
			welcome.show();

		if( savedState != null ) {
			autoOpenedTree = savedState.getBoolean("autoOpenedTree");
			consumedNotifications = savedState.getIntegerArrayList("consumedNotifications");
		}

		if( Global.settings.trees != null ) {

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
					int treeId = Integer.parseInt(elencoAlberi.get(posiz).get("id"));
					Settings.Tree tree = Global.settings.getTree(treeId);
					boolean derivato = tree.grade == 20;
					boolean esaurito = tree.grade == 30;
					if( derivato ) {
						vistaAlbero.setBackgroundColor(getResources().getColor(R.color.evidenziaMedio));
						((TextView)vistaAlbero.findViewById(R.id.albero_dati)).setTextColor(getResources().getColor(R.color.text));
						vistaAlbero.setOnClickListener(v -> {
							if( !AlberoNuovo.confronta(Alberi.this, tree, true) ) {
								tree.grade = 10; // viene retrocesso
								Global.settings.save();
								aggiornaLista();
								Toast.makeText(Alberi.this, R.string.something_wrong, Toast.LENGTH_LONG).show();
							}
						});
					} else if( esaurito ) {
						vistaAlbero.setBackgroundColor(getResources().getColor(R.color.consumed));
						((TextView)vistaAlbero.findViewById(R.id.albero_titolo)).setTextColor(getResources().getColor(R.color.grayText));
						vistaAlbero.setOnClickListener(v -> {
							if( !AlberoNuovo.confronta(Alberi.this, tree, true) ) {
								tree.grade = 10; // viene retrocesso
								Global.settings.save();
								aggiornaLista();
								Toast.makeText(Alberi.this, R.string.something_wrong, Toast.LENGTH_LONG).show();
							}
						});
					} else {
						vistaAlbero.setBackgroundColor(getResources().getColor(R.color.back_element));
						vistaAlbero.setOnClickListener(v -> {
							rotella.setVisibility(View.VISIBLE);
							if( !(Global.gc != null && treeId == Global.settings.openTree) ) { // se non è già aperto
								if( !apriGedcom(treeId, true) ) {
									rotella.setVisibility(View.GONE);
									return;
								}
							}
							startActivity(new Intent(Alberi.this, Principal.class));
						});
					}
					vistaAlbero.findViewById(R.id.albero_menu).setOnClickListener( vista -> {
						boolean esiste = new File( getFilesDir(), treeId + ".json" ).exists();
						PopupMenu popup = new PopupMenu( Alberi.this, vista );
						Menu menu = popup.getMenu();
						if( treeId == Global.settings.openTree && Global.daSalvare )
							menu.add(0, -1, 0, R.string.save);
						if( (Global.settings.expert && derivato) || (Global.settings.expert && esaurito) )
							menu.add(0, 0, 0, R.string.open);
						if( !esaurito || Global.settings.expert )
							menu.add(0, 1, 0, R.string.tree_info);
						if( (!derivato && !esaurito) || Global.settings.expert )
							menu.add(0, 2, 0, R.string.rename);
						if( esiste && (!derivato || Global.settings.expert) && !esaurito )
							menu.add(0, 3, 0, R.string.media_folders);
						if( !esaurito )
							menu.add(0, 4, 0, R.string.find_errors);
						if( esiste && !derivato && !esaurito ) // non si può ri-condividere un albero ricevuto indietro, anche se sei esperto..
							menu.add(0, 5, 0, R.string.share_tree);
						if( esiste && !derivato && !esaurito && Global.settings.expert && Global.settings.trees.size() > 1
								&& tree.shares != null && tree.grade != 0 ) // cioè dev'essere 9 o 10
							menu.add(0, 6, 0, R.string.compare);
						if( esiste && Global.settings.expert && !esaurito )
							menu.add(0, 7, 0, R.string.export_gedcom);
						if( esiste && Global.settings.expert )
							menu.add(0, 8, 0, R.string.make_backup);
						menu.add(0, 9, 0, R.string.delete);
						popup.show();
						popup.setOnMenuItemClickListener(item -> {
							int id = item.getItemId();
							if( id == -1 ) { // Salva
								U.saveJson(Global.gc, treeId);
								Global.daSalvare = false;
							} else if( id == 0 ) { // Apre un albero derivato
								apriGedcom(treeId, true);
								startActivity(new Intent(Alberi.this, Principal.class));
							} else if( id == 1 ) { // Info Gedcom
								Intent intento = new Intent(Alberi.this, InfoAlbero.class);
								intento.putExtra("idAlbero", treeId);
								startActivity(intento);
							} else if( id == 2 ) { // Rinomina albero
								AlertDialog.Builder builder = new AlertDialog.Builder(Alberi.this);
								View vistaMessaggio = getLayoutInflater().inflate(R.layout.albero_nomina, vistaLista, false);
								builder.setView(vistaMessaggio).setTitle(R.string.title);
								EditText editaNome = vistaMessaggio.findViewById(R.id.nuovo_nome_albero);
								editaNome.setText(elencoAlberi.get(posiz).get("titolo"));
								AlertDialog dialogo = builder.setPositiveButton(R.string.rename, (dialog, i1) -> {
									Global.settings.rinomina(treeId, editaNome.getText().toString());
									aggiornaLista();
								}).setNeutralButton(R.string.cancel, null).create();
								editaNome.setOnEditorActionListener((view, action, event) -> {
									if( action == EditorInfo.IME_ACTION_DONE )
										dialogo.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
									return false;
								});
								dialogo.show();
								vistaMessaggio.postDelayed( () -> {
									editaNome.requestFocus();
									editaNome.setSelection(editaNome.getText().length());
									InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
									inputMethodManager.showSoftInput(editaNome, InputMethodManager.SHOW_IMPLICIT);
								}, 300);
							} else if( id == 3 ) { // Media folders
								startActivity(new Intent(Alberi.this, CartelleMedia.class)
										.putExtra("idAlbero", treeId)
								);
							} else if( id == 4 ) { // Correggi errori
								findErrors(treeId, false);
							} else if( id == 5 ) { // Condividi albero
								startActivity(new Intent(Alberi.this, Condivisione.class)
										.putExtra("idAlbero", treeId)
								);
							} else if( id == 6 ) { // Confronta con alberi esistenti
								if( AlberoNuovo.confronta(Alberi.this, tree, false) ) {
									tree.grade = 20;
									aggiornaLista();
								} else
									Toast.makeText(Alberi.this, R.string.no_results, Toast.LENGTH_LONG).show();
							} else if( id == 7 ) { // Esporta Gedcom
								if( esportatore.apriAlbero(treeId) ) {
									String mime = "application/octet-stream";
									String ext = "ged";
									int code = 636;
									if( esportatore.quantiFileMedia() > 0 ) {
										mime = "application/zip";
										ext = "zip";
										code = 6219;
									}
									F.salvaDocumento(Alberi.this, null, treeId, mime, ext, code);
								}
							} else if( id == 8 ) { // Fai backup
								if( esportatore.apriAlbero(treeId) )
									F.salvaDocumento(Alberi.this, null, treeId, "application/zip", "zip", 327);
							} else if( id == 9 ) {	// Elimina albero
								new AlertDialog.Builder(Alberi.this).setMessage(R.string.really_delete_tree)
										.setPositiveButton(R.string.delete, (dialog, id1) -> {
											deleteTree(Alberi.this, treeId);
											aggiornaLista();
										}).setNeutralButton(R.string.cancel, null).show();
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
		View barraAlberi = getLayoutInflater().inflate(R.layout.alberi_barra, null);
		barraAlberi.findViewById(R.id.alberi_opzioni).setOnClickListener(v -> startActivity(
				new Intent(Alberi.this, Opzioni.class))
		);
		barra.setCustomView(barraAlberi);
		barra.setDisplayShowCustomEnabled(true);

		// FAB
		findViewById(R.id.fab).setOnClickListener(v -> {
			welcome.hide();
			startActivity(new Intent(Alberi.this, AlberoNuovo.class));
		});

		// Automatic load of last opened tree of previous session
		if( !birthdayNotifyTapped(getIntent()) && !autoOpenedTree
				&& getIntent().getBooleanExtra("apriAlberoAutomaticamente", false) && Global.settings.openTree > 0 ) {
			vistaLista.post(() -> {
				if( Alberi.apriGedcom(Global.settings.openTree, false) ) {
					rotella.setVisibility(View.VISIBLE);
					autoOpenedTree = true;
					startActivity(new Intent(this, Principal.class));
				}
			});
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		// Nasconde la rotella, in particolare quando si ritorna indietro a questa activity
		rotella.setVisibility(View.GONE);
	}

	// Essendo Alberi launchMode=singleTask, onRestart viene chiamato anche con startActivity (tranne il primo)
	// però ovviamente solo se Alberi ha chiamato onStop (facendo veloce chiama solo onPause)
	@Override
	protected void onRestart() {
		super.onRestart();
		aggiornaLista();
	}

	// New intent coming from a tapped notification
	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);
		birthdayNotifyTapped(intent);
	}

	@Override
	protected void onSaveInstanceState(@NonNull Bundle outState) {
		outState.putBoolean("autoOpenedTree", autoOpenedTree);
		outState.putIntegerArrayList("consumedNotifications", consumedNotifications);
		super.onSaveInstanceState(outState);
	}

	// If a birthday notification was tapped loads the relative tree and returns true
	private boolean birthdayNotifyTapped(Intent intent) {
		int treeId = intent.getIntExtra(Notifier.TREE_ID_KEY, 0);
		int notifyId = intent.getIntExtra(Notifier.NOTIFY_ID_KEY, 0);
		if( treeId > 0 && !consumedNotifications.contains(notifyId)) {
			new Handler().post(() -> {
				if( Alberi.apriGedcom(treeId, true) ) {
					rotella.setVisibility(View.VISIBLE);
					Global.indi = intent.getStringExtra(Notifier.INDI_ID_KEY);
					consumedNotifications.add(notifyId);
					startActivity(new Intent(this, Principal.class));
				}
			});
			return true;
		}
		return false;
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
							if( referrer != null && referrer.matches("[0-9]{14}") ) { // It's a dateId
								Global.settings.referrer = referrer;
								new AlertDialog.Builder( Alberi.this ).setTitle( R.string.a_new_tree )
										.setMessage( R.string.you_can_download )
										.setPositiveButton( R.string.download, (dialog, id) -> {
											Facciata.scaricaCondiviso( Alberi.this, referrer, rotella );
										}).setNeutralButton( R.string.cancel, (di, id) -> welcome.show() )
										.setOnCancelListener( d -> welcome.show() ).show();
							} else { // È qualunque altra cosa
								Global.settings.referrer = null; // lo annulla così non lo cercherà più
								welcome.show();
							}
							Global.settings.save();
							irc.endConnection();
						} catch( Exception e ) {
							U.tosta( Alberi.this, e.getLocalizedMessage() );
						}
						break;
					// App Play Store inesistente sul device o comunque risponde in modo errato
					case InstallReferrerClient.InstallReferrerResponse.FEATURE_NOT_SUPPORTED:
					// Questo non l'ho mai visto comparire
					case InstallReferrerClient.InstallReferrerResponse.SERVICE_UNAVAILABLE:
						Global.settings.referrer = null; // così non torniamo più qui
						Global.settings.save();
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
		for( Settings.Tree alb : Global.settings.trees ) {
			Map<String, String> dato = new HashMap<>(3);
			dato.put("id", String.valueOf(alb.id));
			dato.put("titolo", alb.title);
			// Se Gedcom già aperto aggiorna i dati
			if( Global.gc != null && Global.settings.openTree == alb.id && alb.persons < 100 )
				InfoAlbero.refreshData(Global.gc, alb);
			dato.put("dati", scriviDati(this, alb));
			elencoAlberi.add(dato);
		}
		adapter.notifyDataSetChanged();
	}

	static String scriviDati(Context contesto, Settings.Tree alb) {
		String dati = alb.persons + " " +
				contesto.getString(alb.persons == 1 ? R.string.person : R.string.persons).toLowerCase();
		if( alb.persons > 1 && alb.generations > 0 )
			dati += " - " + alb.generations + " " +
					contesto.getString(alb.generations == 1 ? R.string.generation : R.string.generations).toLowerCase();
		if( alb.media > 0 )
			dati += " - " + alb.media + " " + contesto.getString(R.string.media).toLowerCase();
		return dati;
	}

	// Apertura del Gedcom temporaneo per estrarne info in Alberi
	static Gedcom apriGedcomTemporaneo(int idAlbero, boolean mettiInGlobale) {
		Gedcom gc;
		if( Global.gc != null && Global.settings.openTree == idAlbero )
			gc = Global.gc;
		else {
			gc = leggiJson(idAlbero);
			if( mettiInGlobale ) {
				Global.gc = gc; // per poter usare ad esempio U.unaFoto()
				Global.settings.openTree = idAlbero; // così Global.gc e Global.preferenze.idAprendo sono sincronizzati
			}
		}
		return gc;
	}

	// Apertura del Gedcom per editare tutto in Family Gem
	static boolean apriGedcom(int idAlbero, boolean salvaPreferenze) {
		Global.gc = leggiJson(idAlbero);
		if( Global.gc == null )
			return false;
		if( salvaPreferenze ) {
			Global.settings.openTree = idAlbero;
			Global.settings.save();
		}
		Global.indi = Global.settings.getCurrentTree().root;
		Global.familyNum = 0; // eventualmente lo resetta se era > 0
		Global.daSalvare = false; // eventualmente lo resetta se era true
		return true;
	}

	// Legge il Json e restituisce un Gedcom
	static Gedcom leggiJson(int treeId) {
		Gedcom gedcom;
		File file = new File(Global.context.getFilesDir(), treeId + ".json");
		StringBuilder text = new StringBuilder();
		try {
			BufferedReader br = new BufferedReader(new FileReader(file));
			String line;
			while( (line = br.readLine()) != null ) {
				text.append(line);
				text.append('\n');
			}
			br.close();
		} catch( Exception | Error e ) {
			String message = e instanceof OutOfMemoryError ? Global.context.getString(R.string.not_memory_tree) : e.getLocalizedMessage();
			Toast.makeText(Global.context, message, Toast.LENGTH_LONG).show();
			return null;
		}
		String json = text.toString();
		json = updateLanguage(json);
		gedcom = new JsonParser().fromJson(json);
		if( gedcom == null ) {
			Toast.makeText(Global.context, R.string.no_useful_data, Toast.LENGTH_LONG).show();
			return null;
		}
		return gedcom;
	}

	// Replace Italian with English in Json tree data
	// Introduced in Family Gem 0.8
	static String updateLanguage(String json) {
		json = json.replace("\"zona\":", "\"zone\":");
		json = json.replace("\"famili\":", "\"kin\":");
		json = json.replace("\"passato\":", "\"passed\":");
		return json;
	}

	static void deleteTree(Context context, int treeId) {
		File treeFile = new File(context.getFilesDir(), treeId + ".json");
		treeFile.delete();
		File mediaDir = context.getExternalFilesDir(String.valueOf(treeId));
		deleteFilesAndDirs(mediaDir);
		if( Global.settings.openTree == treeId ) {
			Global.gc = null;
		}
		Global.settings.deleteTree(treeId);
		WorkManager.getInstance(context).cancelAllWorkByTag(Notifier.WORK_TAG + treeId);
	}

	static void deleteFilesAndDirs(File fileOrDirectory) {
		if( fileOrDirectory.isDirectory() ) {
			for( File child : fileOrDirectory.listFiles() )
				deleteFilesAndDirs(child);
		}
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

	Gedcom findErrors(final int treeId, final boolean correct) {
		Gedcom gc = leggiJson(treeId);
		if( gc == null ) {
			// todo fai qualcosa per recuperare un file introvabile..?
			return null;
		}
		int errors = 0;
		int num;
		// Radice in preferenze
		Settings.Tree albero = Global.settings.getTree(treeId);
		Person radica = gc.getPerson(albero.root);
		// Radice punta ad una persona inesistente
		if( albero.root != null && radica == null ) {
			if( !gc.getPeople().isEmpty() ) {
				if( correct ) {
					albero.root = U.trovaRadice(gc);
					Global.settings.save();
				} else errors++;
			} else { // albero senza persone
				if( correct ) {
					albero.root = null;
					Global.settings.save();
				} else errors++;
			}
		}
		// Oppure non è indicata una radice in preferenze pur essendoci persone nell'albero
		if( radica == null && !gc.getPeople().isEmpty() ) {
			if( correct ) {
				albero.root = U.trovaRadice(gc);
				Global.settings.save();
			} else errors++;
		}
		// O in preferenze è indicata una radiceCondivisione che non esiste
		Person radicaCondivisa = gc.getPerson(albero.shareRoot);
		if( albero.shareRoot != null && radicaCondivisa == null ) {
			if( correct ) {
				albero.shareRoot = null; // la elimina e basta
				Global.settings.save();
			} else errors++;
		}
		// Cerca famiglie vuote o con un solo membro per eliminarle
		for( Family f : gc.getFamilies() ) {
			if( f.getHusbandRefs().size() + f.getWifeRefs().size() + f.getChildRefs().size() <= 1 ) {
				if( correct ) {
					gc.getFamilies().remove(f); // così facendo lasci i ref negli individui orfani della famiglia a cui si riferiscono...
					// ma c'è il resto del correttore che li risolve
					break;
				} else errors++;
			}
		}
		// Silently delete empty list of families
		if( gc.getFamilies().isEmpty() && correct ) {
			gc.setFamilies(null);
		}
		// Riferimenti da una persona alla famiglia dei genitori e dei figli
		for( Person p : gc.getPeople() ) {
			for( ParentFamilyRef pfr : p.getParentFamilyRefs() ) {
				Family fam = gc.getFamily( pfr.getRef() );
				if( fam == null ) {
					if( correct ) {
						p.getParentFamilyRefs().remove( pfr );
						break;
					} else errors++;
				} else {
					num = 0;
					for( ChildRef cr : fam.getChildRefs() )
						if( cr.getRef() == null ) {
							if( correct ) {
								fam.getChildRefs().remove(cr);
								break;
							} else errors++;
						} else if( cr.getRef().equals(p.getId()) ) {
							num++;
							if( num > 1 && correct ) {
								fam.getChildRefs().remove( cr );
								break;
							}
						}
					if( num != 1 ) {
						if( correct && num == 0 ) {
							p.getParentFamilyRefs().remove( pfr );
							break;
						} else errors++;
					}
				}
			}
			// Remove empty list of parent family refs
			if( p.getParentFamilyRefs().isEmpty() && correct ) {
				p.setParentFamilyRefs(null);
			}
			for( SpouseFamilyRef sfr : p.getSpouseFamilyRefs() ) {
				Family fam = gc.getFamily(sfr.getRef());
				if( fam == null ) {
					if( correct ) {
						p.getSpouseFamilyRefs().remove(sfr);
						break;
					} else errors++;
				} else {
					num = 0;
					for( SpouseRef sr : fam.getHusbandRefs() )
						if( sr.getRef() == null ) {
							if( correct ) {
								fam.getHusbandRefs().remove(sr);
								break;
							} else errors++;
						} else if( sr.getRef().equals(p.getId()) ) {
							num++;
							if( num > 1 && correct ) {
								fam.getHusbandRefs().remove(sr);
								break;
							}
						}
					for( SpouseRef sr : fam.getWifeRefs() ) {
						if( sr.getRef() == null ) {
							if( correct ) {
								fam.getWifeRefs().remove(sr);
								break;
							} else errors++;
						} else if( sr.getRef().equals(p.getId()) ) {
							num++;
							if( num > 1 && correct ) {
								fam.getWifeRefs().remove(sr);
								break;
							}
						}
					}
					if( num != 1 ) {
						if( num == 0 && correct ) {
							p.getSpouseFamilyRefs().remove(sfr);
							break;
						} else errors++;
					}
				}
			}
			// Remove empty list of spouse family refs
			if( p.getSpouseFamilyRefs().isEmpty() && correct ) {
				p.setSpouseFamilyRefs(null);
			}
			// Riferimenti a Media inesistenti
			// ok ma SOLO per le persone, forse andrebbe fatto col Visitor per tutti gli altri
			num = 0;
			for( MediaRef mr : p.getMediaRefs() ) {
				Media med = gc.getMedia( mr.getRef() );
				if( med == null ) {
					if( correct ) {
						p.getMediaRefs().remove( mr );
						break;
					} else errors++;
				} else {
					if( mr.getRef().equals( med.getId() ) ) {
						num++;
						if( num > 1 )
							if( correct ) {
								p.getMediaRefs().remove( mr );
								break;
							} else errors++;
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
					if( correct ) {
						f.getHusbandRefs().remove(sr);
						break;
					} else errors++;
				} else {
					num = 0;
					for( SpouseFamilyRef sfr : husband.getSpouseFamilyRefs() )
						if( sfr.getRef() == null ) {
							if( correct ) {
								husband.getSpouseFamilyRefs().remove(sfr);
								break;
							} else errors++;
						} else if( sfr.getRef().equals(f.getId()) ) {
							num++;
							if( num > 1 && correct ) {
								husband.getSpouseFamilyRefs().remove(sfr);
								break;
							}
						}
					if( num != 1 ) {
						if( num == 0 && correct ) {
							f.getHusbandRefs().remove(sr);
							break;
						} else errors++;
					}

				}
			}
			// Remove empty list of husband refs
			if( f.getHusbandRefs().isEmpty() && correct ) {
				f.setHusbandRefs(null);
			}
			// Wives refs
			for( SpouseRef sr : f.getWifeRefs() ) {
				Person wife = gc.getPerson(sr.getRef());
				if( wife == null ) {
					if( correct ) {
						f.getWifeRefs().remove(sr);
						break;
					} else errors++;
				} else {
					num = 0;
					for( SpouseFamilyRef sfr : wife.getSpouseFamilyRefs() )
						if( sfr.getRef() == null ) {
							if( correct ) {
								wife.getSpouseFamilyRefs().remove(sfr);
								break;
							} else errors++;
						} else if( sfr.getRef().equals(f.getId()) ) {
							num++;
							if( num > 1 && correct ) {
								wife.getSpouseFamilyRefs().remove(sfr);
								break;
							}
						}
					if( num != 1 ) {
						if( num == 0 && correct ) {
							f.getWifeRefs().remove(sr);
							break;
						} else errors++;
					}
				}
			}
			// Remove empty list of wife refs
			if( f.getWifeRefs().isEmpty() && correct ) {
				f.setWifeRefs(null);
			}
			// Children refs
			for( ChildRef cr : f.getChildRefs() ) {
				Person child = gc.getPerson( cr.getRef() );
				if( child == null ) {
					if( correct ) {
						f.getChildRefs().remove( cr );
						break;
					} else errors++;
				} else {
					num = 0;
					for( ParentFamilyRef pfr : child.getParentFamilyRefs() )
						if( pfr.getRef() == null ) {
							if( correct ) {
								child.getParentFamilyRefs().remove(pfr);
								break;
							} else errors++;
						} else if( pfr.getRef().equals(f.getId()) ) {
							num++;
							if( num > 1 && correct ) {
								child.getParentFamilyRefs().remove(pfr);
								break;
							}
						}
					if( num != 1 ) {
						if( num == 0 && correct ) {
							f.getChildRefs().remove(cr);
							break;
						} else errors++;
					}
				}
			}
			// Remove empty list of child refs
			if( f.getChildRefs().isEmpty() && correct ) {
				f.setChildRefs(null);
			}
		}

		// Aggiunge un tag 'TYPE' ai name type che non l'hanno
		for( Person person : gc.getPeople() ) {
			for( Name name : person.getNames() ) {
				if( name.getType() != null && name.getTypeTag() == null ) {
					if( correct ) name.setTypeTag("TYPE");
					else errors++;
				}
			}
		}

		// Aggiunge un tag 'FILE' ai Media che non l'hanno
		ListaMedia visitaMedia = new ListaMedia(gc, 0);
		gc.accept(visitaMedia);
		for( Media med : visitaMedia.lista ) {
			if( med.getFileTag() == null ) {
				if( correct ) med.setFileTag("FILE");
				else errors++;
			}
		}

		if( !correct ) {
			AlertDialog.Builder dialog = new AlertDialog.Builder(this);
			dialog.setMessage(errors == 0 ? getText(R.string.all_ok) : getString(R.string.errors_found, errors));
			if( errors > 0 ) {
				dialog.setPositiveButton(R.string.correct, (dialogo, i) -> {
					dialogo.cancel();
					Gedcom gcCorretto = findErrors(treeId, true);
					U.saveJson(gcCorretto, treeId);
					Global.gc = null; // così se era aperto poi lo ricarica corretto
					findErrors(treeId, false);    // riapre per ammirere il risultato
					aggiornaLista();
				});
			}
			dialog.setNeutralButton(android.R.string.cancel, null).show();
		}
		return gc;
	}
}