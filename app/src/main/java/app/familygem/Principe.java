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
import app.familygem.visita.ListaMedia;
import static app.familygem.Globale.gc;

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
		U.gedcomSicuro( gc );
		arredaTestataMenu();

		if( savedInstanceState == null ) {  // carica la home solo la prima volta, non ruotando lo schermo
			Fragment fragment;
			String backName = null; // Etichetta per individuare diagramma nel backstack dei frammenti
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
			else { // la normale apertura
				fragment = new Diagram();
				backName = "diagram";
			}
			getSupportFragmentManager().beginTransaction().replace(R.id.contenitore_fragment, fragment)
					.addToBackStack(backName).commit();
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
		if( !(fragment instanceof NuovoParente) )
			aggiornaInterfaccia(fragment);
	}

	// Aggiorna i contenuti quando si torna indietro con backPressed()
	@Override
	public void onRestart() {
		super.onRestart();
		if( Globale.editato ) {
			Fragment attuale = getSupportFragmentManager().findFragmentById(R.id.contenitore_fragment);
			if( attuale instanceof Diagram ) {
				((Diagram)attuale).forceDraw = true; // Così ridisegna il diagramma
			} else if( attuale instanceof Anagrafe ) {
				// Aggiorna la lista di persone
				((Anagrafe)attuale).adattatore.notifyDataSetChanged();
				((Anagrafe)attuale).arredaBarra();
			} else if( attuale instanceof Galleria ) {
				((Galleria)attuale).ricrea();
			} else {
				recreate(); // questo dovrebbe andare a scomparire man mano
			}
			Globale.editato = false;
			arredaTestataMenu(); // praticamente solo per mostrare il bottone Salva
		}
	}

	// Riceve una classe tipo 'Diagram.class' e dice se è il fragment attualmente visibile sulla scena
	private boolean frammentoAttuale(Class classe) {
		Fragment attuale = getSupportFragmentManager().findFragmentById(R.id.contenitore_fragment);
		return classe.isInstance( attuale );
	}

	// Titolo, immagine a caso, bottone Salva nella testata del menu
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
						F.dipingiMedia( med, immagine, null );
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
			Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show();
		});
		bottoneSalva.setOnLongClickListener( vista -> {
			PopupMenu popup = new PopupMenu(this, vista);
			popup.getMenu().add(0, 0, 0, R.string.revert);
			popup.show();
			popup.setOnMenuItemClickListener( item -> {
				if( item.getItemId() == 0 ) {
					Alberi.apriGedcom( Globale.preferenze.idAprendo, false );
					U.qualiGenitoriMostrare( this, null, 0 ); // Semplicemente ricarica il diagramma
					scatolissima.closeDrawer( GravityCompat.START );
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
	void aggiornaInterfaccia( Fragment fragment ) {
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
					Globale.individuo = Globale.preferenze.alberoAperto().radice;
					cosaAprire = 1; // Eventualmente chiede dei molteplici genitori
				}
				U.qualiGenitoriMostrare( this, Globale.gc.getPerson(Globale.individuo), cosaAprire );
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
}
