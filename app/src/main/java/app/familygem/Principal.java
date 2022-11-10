package app.familygem;

import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.PopupMenu;
import androidx.fragment.app.Fragment;
import com.google.android.material.navigation.NavigationView;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.FragmentManager;
import android.os.Handler;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import org.folg.gedcom.model.Media;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import app.familygem.visitor.MediaList;
import app.familygem.visitor.NoteList;
import static app.familygem.Global.gc;

public class Principal /*TODO Main?*/extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {

	DrawerLayout scatolissima;
	Toolbar toolbar;
	NavigationView menuPrincipe;
	List<Integer> idMenu = Arrays.asList( R.id.nav_diagramma, R.id.nav_persone, R.id.nav_famiglie,
			R.id.nav_media, R.id.nav_note, R.id.nav_fonti, R.id.nav_archivi, R.id.nav_autore );
	List<Class> frammenti = Arrays.asList( Diagram.class, ListOfPeopleFragment.class, ChurchFragment.class,
			GalleryFragment.class, NotebookFragment.class, LibraryFragment.class, RepositoryFragment.class, ListOfAuthorsFragment.class );

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.principe);

		toolbar = findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);

		scatolissima = findViewById(R.id.scatolissima);
		ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
				this, scatolissima, toolbar, R.string.drawer_open, R.string.drawer_close );
		scatolissima.addDrawerListener(toggle);
		toggle.syncState();

		menuPrincipe = findViewById(R.id.menu);
		menuPrincipe.setNavigationItemSelectedListener(this);
		Global.principalView = scatolissima;
		U.ensureGlobalGedcomNotNull( gc );
		furnishMenu();

		if( savedInstanceState == null ) {  // carica la home solo la prima volta, non ruotando lo schermo
			Fragment fragment;
			String backName = null; // Etichetta per individuare diagramma nel backstack dei frammenti
			if( getIntent().getBooleanExtra("anagrafeScegliParente",false) )
				fragment = new ListOfPeopleFragment();
			else if( getIntent().getBooleanExtra("galleriaScegliMedia",false) )
				fragment = new GalleryFragment();
			else if( getIntent().getBooleanExtra("bibliotecaScegliFonte",false) )
				fragment = new LibraryFragment();
			else if( getIntent().getBooleanExtra("quadernoScegliNota",false) )
				fragment = new NotebookFragment();
			else if( getIntent().getBooleanExtra("magazzinoScegliArchivio",false) )
				fragment = new RepositoryFragment();
			else { // la normale apertura
				fragment = new Diagram();
				backName = "diagram";
			}
			getSupportFragmentManager().beginTransaction().replace(R.id.contenitore_fragment, fragment)
					.addToBackStack(backName).commit();
		}

		menuPrincipe.getHeaderView(0).findViewById(R.id.menu_alberi).setOnClickListener(v -> {
			scatolissima.closeDrawer(GravityCompat.START);
			startActivity(new Intent(Principal.this, TreesActivity.class));
		});

		// Nasconde le voci del menu più ostiche
		if( !Global.settings.expert ) {
			Menu menu = menuPrincipe.getMenu();
			menu.findItem(R.id.nav_fonti).setVisible(false);
			menu.findItem(R.id.nav_archivi).setVisible(false);
			menu.findItem(R.id.nav_autore).setVisible(false);
		}
	}

	// Chiamato praticamente sempre tranne che onBackPressed
	@Override
	public void onAttachFragment(@NonNull Fragment fragment) {
		super.onAttachFragment(fragment);
		if( !(fragment instanceof NewRelativeDialog) )
			aggiornaInterfaccia(fragment);
	}

	// Aggiorna i contenuti quando si torna indietro con backPressed()
	@Override
	public void onRestart() {
		super.onRestart();
		if( Global.edited ) {
			Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.contenitore_fragment);
			if( fragment instanceof Diagram ) {
				((Diagram)fragment).forceDraw = true; // Così ridisegna il diagramma
			} else if( fragment instanceof ListOfPeopleFragment) {
				// Update persons list
				ListOfPeopleFragment listOfPeopleFragment = (ListOfPeopleFragment)fragment;
				if( listOfPeopleFragment.people.size() == 0 ) // Probably it's a Collections.EmptyList
					listOfPeopleFragment.people = gc.getPeople(); // replace it with the real ArrayList
				listOfPeopleFragment.adapter.notifyDataSetChanged();
				listOfPeopleFragment.arredaBarra();
			} else if( fragment instanceof ChurchFragment) {
				((ChurchFragment)fragment).refresh(ChurchFragment.What.RELOAD);
			} else if( fragment instanceof GalleryFragment) {
				((GalleryFragment)fragment).recreate();
			/*} else if( fragment instanceof Quaderno ) {
				// Doesn't work to update Quaderno when a note is deleted
				((Quaderno)fragment).adapter.notifyDataSetChanged();*/
			} else {
				recreate(); // questo dovrebbe andare a scomparire man mano
			}
			Global.edited = false;
			furnishMenu(); // praticamente solo per mostrare il bottone Salva
		}
	}

	// Riceve una classe type 'Diagram.class' e dice se è il fragment attualmente visibile sulla scena
	private boolean frammentoAttuale(Class classe) {
		Fragment attuale = getSupportFragmentManager().findFragmentById(R.id.contenitore_fragment);
		return classe.isInstance(attuale);
	}

	// Update title, random image, 'Save' button in menu header, and menu items count
	void furnishMenu() {
		NavigationView navigation = scatolissima.findViewById(R.id.menu);
		View menuHeader = navigation.getHeaderView(0);
		ImageView imageView = menuHeader.findViewById( R.id.menu_immagine );
		TextView mainTitle = menuHeader.findViewById( R.id.menu_titolo );
		imageView.setVisibility( ImageView.GONE );
		mainTitle.setText( "" );
		if( Global.gc != null ) {
			MediaList cercaMedia = new MediaList( Global.gc, 3 );
			Global.gc.accept( cercaMedia );
			if( cercaMedia.list.size() > 0 ) {
				int caso = new Random().nextInt( cercaMedia.list.size() );
				for( Media med : cercaMedia.list)
					if( --caso < 0 ) { // arriva a -1
						F.showImage( med, imageView, null );
						imageView.setVisibility( ImageView.VISIBLE );
						break;
					}
			}
			mainTitle.setText( Global.settings.getCurrentTree().title);
			if( Global.settings.expert ) {
				TextView treeNumView = menuHeader.findViewById(R.id.menu_number);
				treeNumView.setText(String.valueOf(Global.settings.openTree));
				treeNumView.setVisibility(ImageView.VISIBLE);
			}
			// Put count of existing records in menu items
			Menu menu = navigation.getMenu();
			for( int i = 1; i <= 7; i++ ) {
				int count = 0;
				switch( i ) {
					case 1: count = gc.getPeople().size(); break;
					case 2: count = gc.getFamilies().size(); break;
					case 3:
						MediaList mediaList = new MediaList(gc, 0);
						gc.accept(mediaList);
						count = mediaList.list.size();
						break;
					case 4:
						NoteList notesList = new NoteList();
						gc.accept(notesList);
						count = notesList.noteList.size() + gc.getNotes().size();
						break;
					case 5: count = gc.getSources().size(); break;
					case 6: count = gc.getRepositories().size(); break;
					case 7: count = gc.getSubmitters().size();
				}
				TextView countView = menu.getItem(i).getActionView().findViewById(R.id.menu_item_text);
				if( count > 0 )
					countView.setText(String.valueOf(count));
				else
					countView.setVisibility(View.GONE);
			}
		}
		// Save button
		Button saveButton = menuHeader.findViewById(R.id.menu_salva);
		saveButton.setOnClickListener(view -> {
			view.setVisibility(View.GONE);
			U.saveJson(Global.gc, Global.settings.openTree);
			scatolissima.closeDrawer(GravityCompat.START);
			Global.daSalvare = false;
			Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show();
		});
		saveButton.setOnLongClickListener( vista -> {
			PopupMenu popup = new PopupMenu(this, vista);
			popup.getMenu().add(0, 0, 0, R.string.revert);
			popup.show();
			popup.setOnMenuItemClickListener( item -> {
				if( item.getItemId() == 0 ) {
					TreesActivity.openGedcom(Global.settings.openTree, false);
					U.askWhichParentsToShow(this, null, 0); // Semplicemente ricarica il diagramma
					scatolissima.closeDrawer(GravityCompat.START);
					saveButton.setVisibility(View.GONE);
					Global.daSalvare = false;
				}
				return true;
			});
			return true;
		});
		if( Global.daSalvare )
			saveButton.setVisibility( View.VISIBLE );
	}

	// Evidenzia voce del menu e mostra/nasconde toolbar
	void aggiornaInterfaccia(Fragment fragment) {
		if( fragment == null )
			fragment = getSupportFragmentManager().findFragmentById(R.id.contenitore_fragment);
		if( fragment != null ) {
			int numFram = frammenti.indexOf(fragment.getClass());
			if( menuPrincipe != null )
				menuPrincipe.setCheckedItem(idMenu.get(numFram));
			if( toolbar == null )
				toolbar = findViewById(R.id.toolbar);
			if( toolbar != null )
				toolbar.setVisibility(numFram == 0 ? View.GONE : View.VISIBLE);
		}
	}

	@Override
	public void onBackPressed() {
		if( scatolissima.isDrawerOpen(GravityCompat.START) ) {
			scatolissima.closeDrawer(GravityCompat.START);
		} else {
			super.onBackPressed();
			if( getSupportFragmentManager().getBackStackEntryCount() == 0 ) {
				// Fa tornare ad Alberi invece di rivedere il primo diagramma del backstack
				super.onBackPressed();
			} else
				aggiornaInterfaccia(null);
		}
	}

	@Override
	public boolean onNavigationItemSelected(MenuItem item) {
		Fragment fragment = null;
		try {
			fragment = (Fragment) frammenti.get( idMenu.indexOf(item.getItemId()) ).newInstance();
		} catch(Exception e) {}
		if( fragment != null ) {
			if( fragment instanceof Diagram ) {
				int cosaAprire = 0; // Mostra il diagramma senza chiedere dei molteplici genitori
				// Se sono già in diagramma e clicco Diagramma, mostra la persona radice
				if( frammentoAttuale(Diagram.class) ) {
					Global.indi = Global.settings.getCurrentTree().root;
					cosaAprire = 1; // Eventualmente chiede dei molteplici genitori
				}
				U.askWhichParentsToShow( this, Global.gc.getPerson(Global.indi), cosaAprire );
			} else {
				FragmentManager fm = getSupportFragmentManager();
				// Rimuove frammento precedente dalla storia se è lo stesso che stiamo per vedere
				if( frammentoAttuale(fragment.getClass()) ) fm.popBackStack();
				fm.beginTransaction().replace( R.id.contenitore_fragment, fragment ).addToBackStack(null).commit();
			}
		}
		scatolissima.closeDrawer(GravityCompat.START);
		return true;
	}

	// Automatically open the 'Sort by' sub-menu
	@Override
	public boolean onMenuOpened(int featureId, Menu menu) {
		MenuItem item0 = menu.getItem(0);
		if( item0.getTitle().equals(getString(R.string.order_by)) ) {
			item0.setVisible(false); // a little hack to prevent options menu to appear
			new Handler().post(() -> {
				item0.setVisible(true);
				menu.performIdentifierAction(item0.getItemId(), 0);
			});
		}
		return super.onMenuOpened(featureId, menu);
	}
}
