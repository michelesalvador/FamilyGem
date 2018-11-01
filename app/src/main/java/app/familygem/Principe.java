package app.familygem;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import org.folg.gedcom.model.Media;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Principe extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {

	DrawerLayout scatolissima;

	@Override
    protected void onCreate( Bundle savedInstanceState ) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.principe);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

		scatolissima = findViewById(R.id.scatolissima);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, scatolissima, toolbar, R.string.drawer_open, R.string.drawer_close );
		scatolissima.addDrawerListener(toggle);
        toggle.syncState();

        NavigationView menuPrincipe = findViewById(R.id.menu);
		menuPrincipe.setNavigationItemSelectedListener(this);
		Globale.vistaPrincipe = scatolissima;
		arredaTestataMenu();

        if( savedInstanceState == null ) {  // carica la home solo la prima volta, non ruotando lo schermo
			if( Globale.preferenze.idAprendo == 0 )	// cioè praticamente alla prima apertura
				startActivity( new Intent(this, AlberoNuovo.class) );
			else if( getIntent().getBooleanExtra("anagrafeScegliParente",false) )
				getSupportFragmentManager().beginTransaction().replace(R.id.contenitore_fragment, new Anagrafe()).commit();
			else if( getIntent().getBooleanExtra("galleriaScegliMedia",false) )
				getSupportFragmentManager().beginTransaction().replace(R.id.contenitore_fragment, new Galleria()).commit();
			else if( getIntent().getBooleanExtra("bibliotecaScegliFonte",false) )
				getSupportFragmentManager().beginTransaction().replace(R.id.contenitore_fragment, new Biblioteca()).commit();
			else if( getIntent().getBooleanExtra("quadernoScegliNota",false) )
				getSupportFragmentManager().beginTransaction().replace(R.id.contenitore_fragment, new Quaderno()).commit();
			else if( getIntent().getBooleanExtra("magazzinoScegliArchivio",false) )
				getSupportFragmentManager().beginTransaction().replace(R.id.contenitore_fragment, new Magazzino()).commit();
			else {    // la normale apertura
				getSupportFragmentManager().beginTransaction().replace(R.id.contenitore_fragment, new Diagramma()).commit();
			}
        }

		NavigationView menu = scatolissima.findViewById(R.id.menu);
		menu.getHeaderView(0).findViewById( R.id.menu_testa ).setOnClickListener( new View.OnClickListener() {
			@Override
			public void onClick( View v ) {
				scatolissima.closeDrawer(GravityCompat.START);
				startActivity( new Intent( Principe.this, Alberi.class ) );
			}
		} );
    }

	// Aggiorna i contenuti quando si torna indietro con backPressed()
	@Override
	public void onRestart() {
		super.onRestart();
		if( Globale.editato ) {
			recreate();
			Globale.editato = false;
		}
	}

	// Titolo e immagine a caso del Gedcom
	static void arredaTestataMenu() {
		NavigationView menu = Globale.vistaPrincipe.findViewById(R.id.menu);
		ImageView immagine = menu.getHeaderView(0).findViewById( R.id.menu_immagine );
		TextView testo = menu.getHeaderView(0).findViewById( R.id.menu_titolo );
		immagine.setVisibility( ImageView.GONE );
		testo.setText( "" );
		if( Globale.gc != null ) {
			VisitaListaMedia visitaMedia = new VisitaListaMedia(true);
			Globale.gc.accept( visitaMedia );
			if( visitaMedia.listaMedia.size() > 0 ) {
				List<Media> lista = new ArrayList<>( visitaMedia.listaMedia.keySet() );
				Random caso = new Random();
				int num = caso.nextInt( lista.size() );
				U.mostraMedia( immagine, lista.get(num) );
				//if( immagine.getTag(R.id.tag_file_senza_anteprima) == null ) essendo asincrono arriva in ritardo
				immagine.setVisibility( ImageView.VISIBLE );
			}
			testo.setText( Globale.preferenze.alberoAperto().nome );
		}
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
            fragment = new Diagramma();
        } else if (id == R.id.nav_persone) {
            fragment = new Anagrafe();
        } else if (id == R.id.nav_fonti) {
            fragment = new Biblioteca();
		} else if (id == R.id.nav_archivi) {
			fragment = new Magazzino();
        } else if (id == R.id.nav_media) {
        	// ToDo: Se clicco  IndividuoMedia > FAB > CollegaMedia....  "galleriaScegliMedia" viene passato all'intent dell'activity con valore 'true'
	        // todo: porò rimane true anche quando poi torno in Galleria cliccando nel drawer, con conseguenti errori:
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
            FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
            ft.replace( R.id.contenitore_fragment, fragment );
            ft.addToBackStack(null);
            ft.commit();
        }
	    scatolissima.closeDrawer(GravityCompat.START);

        return true;
    }
}
