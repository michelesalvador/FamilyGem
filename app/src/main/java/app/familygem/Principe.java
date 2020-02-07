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
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import org.folg.gedcom.model.Media;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import app.familygem.visita.ListaMedia;

public class Principe extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {

	DrawerLayout scatolissima;
	Toolbar toolbar;
	NavigationView menuPrincipe;
	List<Integer> idMenu = Arrays.asList( R.id.nav_diagramma, R.id.nav_persone, R.id.nav_famiglie,
			R.id.nav_media, R.id.nav_note, R.id.nav_fonti, R.id.nav_archivi, R.id.nav_autore );
	List<Class> frammenti = Arrays.asList( Diagram.class, Anagrafe.class, Chiesa.class,
			Galleria.class, Quaderno.class, Biblioteca.class, Magazzino.class, Podio.class );

	@Override
	protected void onCreate( Bundle savedInstanceState ) {
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
		Globale.vistaPrincipe = scatolissima;
		if( Globale.gc == null ) {
			if( !Alberi.apriGedcom( Globale.preferenze.idAprendo, false ) )
				return;
		}
		arredaTestataMenu();

		if( savedInstanceState == null ) {  // carica la home solo la prima volta, non ruotando lo schermo
			Fragment fragment;
			if( getIntent().getBooleanExtra("anagrafeScegliParente",false) )
				fragment = new Anagrafe();
			else if( getIntent().getBooleanExtra("galleriaScegliMedia",false) )
				fragment = new Galleria();
			else if( getIntent().getBooleanExtra("bibliotecaScegliFonte",false) )
				fragment = new Biblioteca();
			else if( getIntent().getBooleanExtra("quadernoScegliNota",false) )
				fragment = new Quaderno();
			else if( getIntent().getBooleanExtra("magazzinoScegliArchivio",false) )
				fragment = new Magazzino();
			else // la normale apertura
				fragment = new Diagram();
			getSupportFragmentManager().beginTransaction().replace(R.id.contenitore_fragment, fragment).commit();
		}

		menuPrincipe.getHeaderView(0).findViewById( R.id.menu_alberi ).setOnClickListener( v -> {
			scatolissima.closeDrawer(GravityCompat.START);
			startActivity( new Intent( Principe.this, Alberi.class ) );
		});

		// Nasconde le voci del menu più ostiche
		if( !Globale.preferenze.esperto ) {
			Menu menu = menuPrincipe.getMenu();
			menu.findItem(R.id.nav_fonti).setVisible(false);
			menu.findItem( R.id.nav_archivi ).setVisible(false);
			menu.findItem( R.id.nav_autore ).setVisible(false);
		}
	}

	// Chiamato praticamente sempre tranne che onBackPressed
	@Override
	public void onAttachFragment( @NonNull Fragment fragment ) {
		super.onAttachFragment( fragment );
		aggiornaInterfaccia(fragment);
	}

	// Aggiorna i contenuti quando si torna indietro al primo backPressed()
	@Override
	public void onRestart() {
		super.onRestart();
		if( Globale.editato ) {
			recreate();
		}
	}

	// Titolo e immagine a caso del Gedcom
	void arredaTestataMenu() {
		NavigationView menu = scatolissima.findViewById(R.id.menu);
		ImageView immagine = menu.getHeaderView(0).findViewById( R.id.menu_immagine );
		TextView testo = menu.getHeaderView(0).findViewById( R.id.menu_titolo );
		immagine.setVisibility( ImageView.GONE );
		testo.setText( "" );
		if( Globale.gc != null ) {
			ListaMedia cercaMedia = new ListaMedia( Globale.gc, 3 );
			Globale.gc.accept( cercaMedia );
			if( cercaMedia.lista.size() > 0 ) {
				int caso = new Random().nextInt( cercaMedia.lista.size() );
				for( Media med : cercaMedia.lista )
					if( --caso < 0 ) { // arriva a -1
						U.dipingiMedia( med, immagine, null );
						immagine.setVisibility( ImageView.VISIBLE );
						break;
					}
			}
			testo.setText( Globale.preferenze.alberoAperto().nome );
		}
		Button bottoneSalva = menu.getHeaderView(0).findViewById( R.id.menu_salva );
		bottoneSalva.setOnClickListener( vista -> {
			vista.setVisibility( View.GONE );
			U.salvaJson( Globale.gc, Globale.preferenze.idAprendo );
			scatolissima.closeDrawer(GravityCompat.START);
			Globale.daSalvare = false;
		});
		bottoneSalva.setOnLongClickListener( vista -> {
			PopupMenu popup = new PopupMenu(this, vista);
			popup.getMenu().add(0, 0, 0, R.string.revert);
			popup.show();
			popup.setOnMenuItemClickListener( item -> {
				if( item.getItemId() == 0 ) {
					Alberi.apriGedcom( Globale.preferenze.idAprendo, false );
					getSupportFragmentManager().beginTransaction().replace(R.id.contenitore_fragment, new Diagram())
							.addToBackStack(null).commit();
					scatolissima.closeDrawer(GravityCompat.START);
					bottoneSalva.setVisibility( View.GONE );
					Globale.daSalvare = false;
				}
				return true;
			});
			return true;
		});
		if( Globale.daSalvare )
			bottoneSalva.setVisibility( View.VISIBLE );
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
				toolbar.setVisibility( numFram == 0 ? View.GONE : View.VISIBLE );
		}
	}

	@Override
	public void onBackPressed() {
		if( scatolissima.isDrawerOpen(GravityCompat.START) ) {
			scatolissima.closeDrawer(GravityCompat.START);
		} else {
			super.onBackPressed();
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
			getSupportFragmentManager().beginTransaction().replace( R.id.contenitore_fragment, fragment )
					.addToBackStack(null).commit();
		}
		scatolissima.closeDrawer(GravityCompat.START);
		return true;
	}

	@Override
	public void onRequestPermissionsResult( int codice, String[] permessi, int[] accordi ) {
		U.risultatoPermessi( this, codice, permessi, accordi, null );
	}
}
