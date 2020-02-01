package app.familygem;

import android.content.Intent;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
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
import java.util.Random;
import app.familygem.visita.ListaMedia;

public class Principe extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {

	DrawerLayout scatolissima;
	Toolbar toolbar;

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

		NavigationView menuPrincipe = findViewById(R.id.menu);
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

	// Aggiorna i contenuti quando si torna indietro con backPressed()
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
		if( Globale.daSalvare )
			bottoneSalva.setVisibility( View.VISIBLE );
	}

	@Override
	public void onBackPressed() {
		if( scatolissima.isDrawerOpen(GravityCompat.START) ) {
			scatolissima.closeDrawer(GravityCompat.START);
		} else {
			super.onBackPressed();
		}
	}

	@Override
	public boolean onNavigationItemSelected(MenuItem item) {
		int id = item.getItemId();
		Fragment fragment = null;
		if (id == R.id.nav_diagramma) {
			fragment = new Diagram();
		} else if (id == R.id.nav_persone) {
			fragment = new Anagrafe();
		} else if (id == R.id.nav_fonti) {
			fragment = new Biblioteca();
		} else if (id == R.id.nav_archivi) {
			fragment = new Magazzino();
		} else if (id == R.id.nav_media) {
			// ToDo: Se clicco  IndividuoMedia > FAB > CollegaMedia....  "galleriaScegliMedia" viene passato all'intent dell'activity con valore 'true'
			// todo: però rimane true anche quando poi torno in Galleria cliccando nel drawer, con conseguenti errori:
			// vengono visualizzati solo gli oggetti media
			// cliccandone uno esso viene aggiunto ai media dell'ultima persona vista !
			fragment = new Galleria();
		} else if (id == R.id.nav_famiglie) {
			fragment = new Chiesa();
		} else if (id == R.id.nav_note) {
			fragment = new Quaderno();
		} else if (id == R.id.nav_autore) {
			fragment = new Podio();
		}
		if( fragment != null ) {
			toolbar.setVisibility( View.VISIBLE );
			FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
			ft.replace( R.id.contenitore_fragment, fragment );
			ft.addToBackStack(null);
			ft.commit();
		}
		scatolissima.closeDrawer(GravityCompat.START);
		return true;
	}

	@Override
	public void onRequestPermissionsResult( int codice, String[] permessi, int[] accordi ) {
		U.risultatoPermessi( this, codice, permessi, accordi, null );
	}
}
