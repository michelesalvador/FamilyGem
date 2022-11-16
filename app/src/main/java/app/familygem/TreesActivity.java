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
import app.familygem.visitor.MediaList;

public class TreesActivity extends AppCompatActivity {

	List<Map<String,String>> treeList;
	SimpleAdapter adapter;
	View wheel;
	SpeechBubble welcome;
	Exporter exporter;
	/**
	 * To open automatically the tree at startup only once
	 */
	private boolean autoOpenedTree;
	/**
	 * The birthday notification IDs are stored to display the corresponding person only once
	 */
	private ArrayList<Integer> consumedNotifications = new ArrayList<>();

	@Override
	protected void onCreate(Bundle savedState) {
		super.onCreate(savedState);
		setContentView(R.layout.alberi);
		ListView listView = findViewById(R.id.lista_alberi);
		wheel = findViewById(R.id.alberi_circolo);
		welcome = new SpeechBubble(this, R.string.tap_add_tree);
		exporter = new Exporter(TreesActivity.this);

		// At the very first start
		String referrer = Global.settings.referrer;
		if( referrer != null && referrer.equals("start") )
			fetchReferrer();
		// If a dataid has been stored (which will be deleted as soon as used)
		else if( referrer != null && referrer.matches("[0-9]{14}") ) {
			new AlertDialog.Builder(this).setTitle(R.string.a_new_tree)
					.setMessage(R.string.you_can_download)
					.setPositiveButton(R.string.download, (dialog, id) -> {
						FacadeActivity.downloadShared(this, referrer, wheel);
					}).setNeutralButton(R.string.cancel, null).show();
		} // If there is no tree
		else if( Global.settings.trees.isEmpty() )
			welcome.show();

		if( savedState != null ) {
			autoOpenedTree = savedState.getBoolean("autoOpenedTree");
			consumedNotifications = savedState.getIntegerArrayList("consumedNotifications");
		}

		if( Global.settings.trees != null ) {

			// List of family trees
			treeList = new ArrayList<>();

			// Feed the data to the adapter
			adapter = new SimpleAdapter(this, treeList,
					R.layout.pezzo_albero,
					new String[]{"titolo", "dati"},
					new int[]{R.id.albero_titolo, R.id.albero_dati}) { //TODO just implement custom adapter? This is part of the outdated android.widget package, and has a slightly clunky API compared to a custom adapter.
				// Locate each view in the list
				@Override
				public View getView(final int position, View convertView, ViewGroup parent) {
					View treeView = super.getView(position, convertView, parent);
					int treeId = Integer.parseInt(treeList.get(position).get("id"));
					Settings.Tree tree = Global.settings.getTree(treeId);
					boolean derivative = tree.grade == 20;
					boolean noNovelties = tree.grade == 30;
					if( derivative ) {
						treeView.setBackgroundColor(getResources().getColor(R.color.evidenziaMedio));
						((TextView)treeView.findViewById(R.id.albero_dati)).setTextColor(getResources().getColor(R.color.text));
						treeView.setOnClickListener(v -> {
							if( !NewTree.compare(TreesActivity.this, tree, true) ) {
								tree.grade = 10; // is demoted
								Global.settings.save();
								updateList();
								Toast.makeText(TreesActivity.this, R.string.something_wrong, Toast.LENGTH_LONG).show();
							}
						});
					} else if( noNovelties ) {
						treeView.setBackgroundColor(getResources().getColor(R.color.consumed));
						((TextView)treeView.findViewById(R.id.albero_titolo)).setTextColor(getResources().getColor(R.color.grayText));
						treeView.setOnClickListener(v -> {
							if( !NewTree.compare(TreesActivity.this, tree, true) ) {
								tree.grade = 10; // is demoted
								Global.settings.save();
								updateList();
								Toast.makeText(TreesActivity.this, R.string.something_wrong, Toast.LENGTH_LONG).show();
							}
						});
					} else {
						treeView.setBackgroundColor(getResources().getColor(R.color.back_element));
						treeView.setOnClickListener(v -> {
							wheel.setVisibility(View.VISIBLE);
							if( !(Global.gc != null && treeId == Global.settings.openTree) ) { // if it's not already open
								if( !openGedcom(treeId, true) ) {
									wheel.setVisibility(View.GONE);
									return;
								}
							}
							startActivity(new Intent(TreesActivity.this, Principal.class));
						});
					}
					treeView.findViewById(R.id.albero_menu).setOnClickListener( vista -> {
						boolean exists = new File( getFilesDir(), treeId + ".json" ).exists();
						PopupMenu popup = new PopupMenu( TreesActivity.this, vista );
						Menu menu = popup.getMenu();
						if( treeId == Global.settings.openTree && Global.shouldSave)
							menu.add(0, -1, 0, R.string.save);
						if( (Global.settings.expert && derivative) || (Global.settings.expert && noNovelties) )
							menu.add(0, 0, 0, R.string.open);
						if( !noNovelties || Global.settings.expert )
							menu.add(0, 1, 0, R.string.tree_info);
						if( (!derivative && !noNovelties) || Global.settings.expert )
							menu.add(0, 2, 0, R.string.rename);
						if( exists && (!derivative || Global.settings.expert) && !noNovelties )
							menu.add(0, 3, 0, R.string.media_folders);
						if( !noNovelties )
							menu.add(0, 4, 0, R.string.find_errors);
						if( exists && !derivative && !noNovelties ) // non si può ri-condividere un albero ricevuto indietro, anche se sei esperto..
							menu.add(0, 5, 0, R.string.share_tree);
						if( exists && !derivative && !noNovelties && Global.settings.expert && Global.settings.trees.size() > 1
								&& tree.shares != null && tree.grade != 0 ) // cioè dev'essere 9 o 10
							menu.add(0, 6, 0, R.string.compare);
						if( exists && Global.settings.expert && !noNovelties )
							menu.add(0, 7, 0, R.string.export_gedcom);
						if( exists && Global.settings.expert )
							menu.add(0, 8, 0, R.string.make_backup);
						menu.add(0, 9, 0, R.string.delete);
						popup.show();
						popup.setOnMenuItemClickListener(item -> {
							int id = item.getItemId();
							if( id == -1 ) { // Save
								U.saveJson(Global.gc, treeId);
								Global.shouldSave = false;
							} else if( id == 0 ) { // Opens a child tree
								openGedcom(treeId, true);
								startActivity(new Intent(TreesActivity.this, Principal.class));
							} else if( id == 1 ) { // Info Gedcom
								Intent intent = new Intent(TreesActivity.this, TreeInfoActivity.class);
								intent.putExtra("idAlbero", treeId);
								startActivity(intent);
							} else if( id == 2 ) { // Rename tree
								AlertDialog.Builder builder = new AlertDialog.Builder(TreesActivity.this);
								View messageView = getLayoutInflater().inflate(R.layout.albero_nomina, listView, false);
								builder.setView(messageView).setTitle(R.string.title);
								EditText nameEditText = messageView.findViewById(R.id.nuovo_nome_albero);
								nameEditText.setText(treeList.get(position).get("titolo"));
								AlertDialog dialog = builder.setPositiveButton(R.string.rename, (view, i1) -> {
									Global.settings.rename(treeId, nameEditText.getText().toString());
									updateList();
								}).setNeutralButton(R.string.cancel, null).create();
								nameEditText.setOnEditorActionListener((view, action, event) -> {
									if( action == EditorInfo.IME_ACTION_DONE )
										dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
									return false;
								});
								dialog.show();
								messageView.postDelayed( () -> {
									nameEditText.requestFocus();
									nameEditText.setSelection(nameEditText.getText().length());
									InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
									inputMethodManager.showSoftInput(nameEditText, InputMethodManager.SHOW_IMPLICIT);
								}, 300);
							} else if( id == 3 ) { // Media folders
								startActivity(new Intent(TreesActivity.this, MediaFoldersActivity.class)
										.putExtra("idAlbero", treeId)
								);
							} else if( id == 4 ) { // Correct errors
								findErrors(treeId, false);
							} else if( id == 5 ) { // Share tree
								startActivity(new Intent(TreesActivity.this, SharingActivity.class)
										.putExtra("idAlbero", treeId)
								);
							} else if( id == 6 ) { // Compare with existing trees
								if( NewTree.compare(TreesActivity.this, tree, false) ) {
									tree.grade = 20;
									updateList();
								} else
									Toast.makeText(TreesActivity.this, R.string.no_results, Toast.LENGTH_LONG).show();
							} else if( id == 7 ) { // Export Gedcom
								if( exporter.openTree(treeId) ) {
									String mime = "application/octet-stream";
									String ext = "ged";
									int code = 636;
									if( exporter.numMediaFilesToAttach() > 0 ) {
										mime = "application/zip";
										ext = "zip";
										code = 6219;
									}
									F.saveDocument(TreesActivity.this, null, treeId, mime, ext, code);
								}
							} else if( id == 8 ) { // Make backups
								if( exporter.openTree(treeId) )
									F.saveDocument(TreesActivity.this, null, treeId, "application/zip", "zip", 327);
							} else if( id == 9 ) {	// Delete tree
								new AlertDialog.Builder(TreesActivity.this).setMessage(R.string.really_delete_tree)
										.setPositiveButton(R.string.delete, (dialog, id1) -> {
											deleteTree(TreesActivity.this, treeId);
											updateList();
										}).setNeutralButton(R.string.cancel, null).show();
							} else {
								return false;
							}
							return true;
						});
					});
					return treeView;
				}
			};
			listView.setAdapter(adapter);
			updateList();
		}

		// Custom bar
		ActionBar toolbar = getSupportActionBar();
		View treeToolbar = getLayoutInflater().inflate(R.layout.alberi_barra, null);
		treeToolbar.findViewById(R.id.alberi_opzioni).setOnClickListener(v -> startActivity(
				new Intent(TreesActivity.this, OptionsActivity.class))
		);
		toolbar.setCustomView(treeToolbar);
		toolbar.setDisplayShowCustomEnabled(true);

		// FAB
		findViewById(R.id.fab).setOnClickListener(v -> {
			welcome.hide();
			startActivity(new Intent(TreesActivity.this, NewTree.class));
		});

		// Automatic load of last opened tree of previous session
		if( !birthdayNotifyTapped(getIntent()) && !autoOpenedTree
				&& getIntent().getBooleanExtra("apriAlberoAutomaticamente", false) && Global.settings.openTree > 0 ) {
			listView.post(() -> {
				if( openGedcom(Global.settings.openTree, false) ) {
					wheel.setVisibility(View.VISIBLE);
					autoOpenedTree = true;
					startActivity(new Intent(this, Principal.class));
				}
			});
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		// Hides the wheel, especially when navigating back to this activity
		wheel.setVisibility(View.GONE);
	}

	/**
	 * Trees being launchMode=singleTask, onRestart is also called with startActivity (except the first one)
	 * but obviously only if {@link TreesActivity} has called onStop (doing it fast calls only onPause)
	 */
	@Override
	protected void onRestart() {
		super.onRestart();
		updateList();
	}

	/**
	 * New intent coming from a tapped notification
	 */
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

	/**
	 * If a birthday notification was tapped loads the relative tree and returns true
	 */
	private boolean birthdayNotifyTapped(Intent intent) {
		int treeId = intent.getIntExtra(Notifier.TREE_ID_KEY, 0);
		int notifyId = intent.getIntExtra(Notifier.NOTIFY_ID_KEY, 0);
		if( treeId > 0 && !consumedNotifications.contains(notifyId) ) {
			new Handler().post(() -> {
				if( openGedcom(treeId, true) ) {
					wheel.setVisibility(View.VISIBLE);
					Global.indi = intent.getStringExtra(Notifier.INDI_ID_KEY);
					consumedNotifications.add(notifyId);
					startActivity(new Intent(this, Principal.class));
					new Notifier(this, Global.gc, treeId, Notifier.What.DEFAULT); // Actually delete present notification
				}
			});
			return true;
		}
		return false;
	}

	/**
	 * Try to retrieve the dataID from the Play Store in case the app was installed following a share
	 * If it finds the dataid it offers to download the shared tree
	 */
	void fetchReferrer() {
		InstallReferrerClient irc = InstallReferrerClient.newBuilder(this).build();
		irc.startConnection(new InstallReferrerStateListener() {
			@Override
			public void onInstallReferrerSetupFinished(int reply) {
				switch( reply ) {
					case InstallReferrerClient.InstallReferrerResponse.OK:
						try {
							ReferrerDetails details = irc.getInstallReferrer();
							// Normally 'referrer' is a string type 'utm_source=google-play&utm_medium=organic'
							// But if the app was installed from the link in the share page it will be a data-id like '20191003215337'
							String referrer = details.getInstallReferrer();
							if( referrer != null && referrer.matches("[0-9]{14}") ) { // It's a dateId
								Global.settings.referrer = referrer;
								new AlertDialog.Builder( TreesActivity.this ).setTitle( R.string.a_new_tree )
										.setMessage( R.string.you_can_download )
										.setPositiveButton( R.string.download, (dialog, id) -> {
											FacadeActivity.downloadShared( TreesActivity.this, referrer, wheel);
										}).setNeutralButton( R.string.cancel, (di, id) -> welcome.show() )
										.setOnCancelListener( d -> welcome.show() ).show();
							} else { // It's anything else
								Global.settings.referrer = null; // we cancel it so we won't look for it again
								welcome.show();
							}
							Global.settings.save();
							irc.endConnection();
						} catch( Exception e ) {
							U.toast(TreesActivity.this, e.getLocalizedMessage());
						}
						break;
					// App Play Store does not exist on the device or responds incorrectly
					case InstallReferrerClient.InstallReferrerResponse.FEATURE_NOT_SUPPORTED:
					// I've never seen this appear
					case InstallReferrerClient.InstallReferrerResponse.SERVICE_UNAVAILABLE:
						Global.settings.referrer = null; // so we never come back here
						Global.settings.save();
						welcome.show();
				}
			}
			@Override
			public void onInstallReferrerServiceDisconnected() {
				// Never seen it appear
				U.toast(TreesActivity.this, "Install Referrer Service Disconnected");
			}
		});
	}

	void updateList() {
		treeList.clear();
		for( Settings.Tree alb : Global.settings.trees ) {
			Map<String, String> data = new HashMap<>(3);
			data.put("id", String.valueOf(alb.id));
			data.put("titolo", alb.title);
			// If Gedcom is already open, update the data
			if( Global.gc != null && Global.settings.openTree == alb.id && alb.persons < 100 )
				TreeInfoActivity.refreshData(Global.gc, alb);
			data.put("dati", writeData(this, alb));
			treeList.add(data);
		}
		adapter.notifyDataSetChanged();
	}

	static String writeData(Context context, Settings.Tree alb) {
		String dati = alb.persons + " " +
				context.getString(alb.persons == 1 ? R.string.person : R.string.persons).toLowerCase();
		if( alb.persons > 1 && alb.generations > 0 )
			dati += " - " + alb.generations + " " +
					context.getString(alb.generations == 1 ? R.string.generation : R.string.generations).toLowerCase();
		if( alb.media > 0 )
			dati += " - " + alb.media + " " + context.getString(R.string.media).toLowerCase();
		return dati;
	}

	/**
	 * Opening the temporary Gedcom to extract info in {@link TreesActivity}
	 */
	static Gedcom openGedcomTemporarily(int treeId, boolean putInGlobal) {
		Gedcom gc;
		if( Global.gc != null && Global.settings.openTree == treeId )
			gc = Global.gc;
		else {
			gc = readJson(treeId);
			if( putInGlobal ) {
				Global.gc = gc; // to be able to use for example F.showMainImageForPerson()
				Global.settings.openTree = treeId; // so Global.gc and Global.settings.openTree are synchronized
			}
		}
		return gc;
	}

	/**
	 * Opening the Gedcom to edit everything in Family Gem
	 */
	static boolean openGedcom(int treeId, boolean savePreferences) {
		Global.gc = readJson(treeId);
		if( Global.gc == null )
			return false;
		if( savePreferences ) {
			Global.settings.openTree = treeId;
			Global.settings.save();
		}
		Global.indi = Global.settings.getCurrentTree().root;
		Global.familyNum = 0; // eventually resets it if it was > 0
		Global.shouldSave = false; // eventually resets it if it was true
		return true;
	}

	/**
	 * Read the Json and return a Gedcom
	 */
	static Gedcom readJson(int treeId) {
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
		// This Notifier was introduced in version 0.9.1
		// Todo: Can be removed from here in the future because tree.birthdays will never more be null
		if( Global.settings.getTree(treeId).birthdays == null) {
			new Notifier(Global.context, gedcom, treeId, Notifier.What.CREATE);
		}
		return gedcom;
	}

	/**
	 * Replace Italian with English in Json tree data
	 * Introduced in Family Gem 0.8
	 */
	static String updateLanguage(String json) {
		return json
				.replace("\"zona\":", "\"zone\":")
				.replace("\"famili\":", "\"kin\":")
				.replace("\"passato\":", "\"passed\":");
	}

	static void deleteTree(Context context, int treeId) {
		File treeFile = new File(context.getFilesDir(), treeId + ".json");
		treeFile.delete();
		File mediaDir = context.getExternalFilesDir(String.valueOf(treeId));
		deleteFilesAndDirs(mediaDir);
		if( Global.settings.openTree == treeId ) {
			Global.gc = null;
		}
		new Notifier(context, null, treeId, Notifier.What.DELETE);
		Global.settings.deleteTree(treeId);
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
			if( requestCode == 636 ) { // Export the GEDCOM
				result = exporter.exportGedcom( uri );
			} else if( requestCode == 6219 ) { // Export the zipped GEDCOM with media
				result = exporter.exportGedcomToZip( uri );
			} // Export the ZIP backup
			else if( requestCode == 327 ) {
				result = exporter.exportBackupZip( null, -1, uri );
			}
			if( result )
				Toast.makeText( TreesActivity.this, exporter.successMessage, Toast.LENGTH_SHORT ).show();
			else
				Toast.makeText( TreesActivity.this, exporter.errorMessage, Toast.LENGTH_LONG ).show();
		}
	}

	Gedcom findErrors(final int treeId, final boolean correct) {
		Gedcom gc = readJson(treeId);
		if( gc == null ) {
			// do you do something to recover an untraceable file..?
			return null;
		}
		int errors = 0;
		int num;
		// Root in preferences
		Settings.Tree tree = Global.settings.getTree(treeId);
		Person root = gc.getPerson(tree.root);
		// Root points to a non-existent person
		if( tree.root != null && root == null ) {
			if( !gc.getPeople().isEmpty() ) {
				if( correct ) {
					tree.root = U.findRoot(gc);
					Global.settings.save();
				} else errors++;
			} else { // tree without people
				if( correct ) {
					tree.root = null;
					Global.settings.save();
				} else errors++;
			}
		}
		// Or a root is not indicated in preferences even though there are people in the tree
		if( root == null && !gc.getPeople().isEmpty() ) {
			if( correct ) {
				tree.root = U.findRoot(gc);
				Global.settings.save();
			} else errors++;
		}
		// Or a shareRoot is listed in preferences that doesn't exist
		Person shareRoot = gc.getPerson(tree.shareRoot);
		if( tree.shareRoot != null && shareRoot == null ) {
			if( correct ) {
				tree.shareRoot = null; // just delete it
				Global.settings.save();
			} else errors++;
		}
		// Search for empty or single-member families to eliminate them
		for( Family f : gc.getFamilies() ) {
			if( f.getHusbandRefs().size() + f.getWifeRefs().size() + f.getChildRefs().size() <= 1 ) {
				if( correct ) {
					gc.getFamilies().remove(f); // in doing so you leave the refs in the orphaned individuals of the family to which they refer...
					// but there's the rest of the checker to fix them
					break;
				} else errors++;
			}
		}
		// Silently delete empty list of families
		if( gc.getFamilies().isEmpty() && correct ) {
			gc.setFamilies(null);
		}
		// References from a person to the parents' and children's family
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
			// References to non-existent Media
			// ok but ONLY for people, maybe it should be done with the Visitor for everyone else
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

		// Adds a 'TYPE' tag to name types that don't have it
		for( Person person : gc.getPeople() ) {
			for( Name name : person.getNames() ) {
				if( name.getType() != null && name.getTypeTag() == null ) {
					if( correct ) name.setTypeTag("TYPE");
					else errors++;
				}
			}
		}

		// Adds a 'FILE' tag to Media that don't have it
		MediaList mediaList = new MediaList(gc, 0);
		gc.accept(mediaList);
		for( Media med : mediaList.list) {
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
					Global.gc = null; // so if it was open then reload it correct
					findErrors(treeId, false);    // reopen to admire (??) the result
					updateList();
				});
			}
			dialog.setNeutralButton(android.R.string.cancel, null).show();
		}
		return gc;
	}
}