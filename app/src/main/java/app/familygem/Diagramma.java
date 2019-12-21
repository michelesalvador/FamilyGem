package app.familygem;

import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Bundle;
import com.google.android.material.snackbar.Snackbar;
import androidx.fragment.app.Fragment;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import android.view.ContextMenu;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Space;
import android.widget.TextView;
import com.otaliastudios.zoom.ZoomApi;
import com.otaliastudios.zoom.ZoomLayout;
import java.util.ArrayList;
import java.util.List;
import org.folg.gedcom.model.EventFact;
import org.folg.gedcom.model.Family;
import org.folg.gedcom.model.Person;
import app.familygem.dettaglio.Famiglia;
import static app.familygem.Globale.gc;

public class Diagramma extends Fragment {

	ZoomLayout scatolaZoom;
	RelativeLayout scatolona;
	LinearLayout scatola;
	private int gente;

	@Override
	public void onCreate( Bundle bandolo ) {
		super.onCreate( bandolo );
	}

	@Override
	public View onCreateView( LayoutInflater inflater, ViewGroup container, Bundle bandolo) {

		((AppCompatActivity) getActivity()).getSupportActionBar().setTitle( Globale.preferenze.alberoAperto().nome );
		final View vista = inflater.inflate( R.layout.diagramma, container, false );

		scatolona = vista.findViewById( R.id.diagramma_scatolona );
		scatolaZoom = vista.findViewById( R.id.diagramma_zoom );

		if( gc != null )
			disegna();

		return vista;
	}

	// Il libro mastro in cui inserire le coppie di View da collegare con linee
	List<Corda> rete;
	class Corda {
		View origine;
		View mezzo;
		View fine;
		boolean arco;
		Corda( View origine, View mezzo, View fine, boolean arco ) {
			this.origine = origine;
			this.mezzo = mezzo;
			this.fine = fine;
			this.arco = arco;
		}
	}

	// Riempie le file di generazioni
	void disegna() {
		scatola = new LinearLayout( scatolona.getContext() );
		scatola.setOrientation(LinearLayout.VERTICAL);
		scatola.setGravity( Gravity.CENTER_HORIZONTAL );
		LinearLayout.LayoutParams linearParam = new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT );
		scatola.setClipChildren( false );
		scatolona.addView( scatola, linearParam );

		// Crea le righe per ospitare le generazioni
		for( int g=1; g<6; g++) {
			LinearLayout generaz = new LinearLayout( getContext() );
			generaz.setOrientation(LinearLayout.HORIZONTAL);
			generaz.setGravity( Gravity.CENTER );
			generaz.setId( g );
			generaz.setClipChildren( false );
			linearParam.width = LinearLayout.LayoutParams.MATCH_PARENT;
			//linearParam.topMargin = 40; linearParam.bottomMargin = 40; // ok ma per trovare il confine è meglio il padding
			generaz.setPadding( 40,30,40,30 );
			generaz.setClipToPadding( false );
			scatola.addView( generaz, linearParam );
		}
		rete = new ArrayList<>();
		// Trova un centro
		Person centro = gc.getPerson( Globale.individuo );
		if( centro == null ) {
			centro = gc.getPerson( Globale.preferenze.alberoAperto().radice );
			if( centro == null )
				centro = gc.getPerson( U.trovaRadice(gc) );
				if( centro == null ) {
					creaBottonePrimoIndividuo();	// se non ci sono persone mette un bel bottone per aggiungere la prima persona
					return;
				}
		}
		Globale.individuo = centro.getId(); // lo ribadisce nei due casi in cui mancava
		// Risale ai nonni
		if( !centro.getParentFamilies(gc).isEmpty() ) {
			Family famiglia = centro.getParentFamilies(gc).get( getActivity().getIntent().getIntExtra("genitoriNum", 0) );
			getActivity().getIntent().putExtra( "genitoriNum", 0 ); // lo resetta per gli altri che hanno una sola parent family
			// Ramo paterno
			Person padre = null;
			View nodoNonniPaterni = null;
			View nodoGenitori = null;
			if( !famiglia.getHusbandRefs().isEmpty() ) {
				padre = famiglia.getHusbands(gc).get(0);
				spazio(1, zii(padre,null, true) );
				nodoNonniPaterni = nonni( padre );
				zii( padre, nodoNonniPaterni, false );
				altriMatrimoni( padre, famiglia, nodoGenitori );	// todo: nodoGenitori sarebbe il nodo del padre, che però viene creato dopo..
			}
			// Inizia ramo materno
			Person madre = null;
			View nodoNonniMaterni = null;
			if( !famiglia.getWifeRefs().isEmpty() ) {
				madre = famiglia.getWives(gc).get(0);
				spazio(1, (zii(padre,null,true) + zii(madre,null,true))/2 );
				nodoNonniMaterni = nonni( madre );
			}
			// Genitori
			if( padre != null && madre != null )
				nodoGenitori = schedaDoppia( 2, famiglia, nodoNonniPaterni, nodoNonniMaterni, 0, false, 0 );
			else if( padre != null )
				nodoGenitori = schedaSingola( 2, padre, nodoNonniPaterni, false, false, 0 );
			else if( madre != null )
				nodoGenitori = schedaSingola( 2, madre, nodoNonniMaterni, false, false, 0 );
			// Completa arcobaleni degli zii paterni
			View legame = null;
			if( nodoGenitori != null ) {
				View paiolo = nodoGenitori.findViewWithTag( "padrePaiolo" );
				for( Corda corda : rete ) {
					if( corda.arco && corda.origine == null )
						corda.origine = paiolo!=null ? paiolo : nodoGenitori;
				}
				// Prepara il nodo a cui si attaccano centro e i suoi fratelli
				legame = nodoGenitori.findViewWithTag( "legame" );
			}
			// Fratelli (tra cui centro), figli e nipoti
			View nodoIo = null;
			View nodoFratello = null;
			for( Person fratello : famiglia.getChildren(gc) ) {
				if( fratello == centro )
					nodoIo = ioFigliNipoti( fratello, legame!=null?legame:nodoGenitori );  // i figli di centro mostrereanno anche i propri figli
				else
					nodoFratello = fratelloNipoti( fratello, legame!=null?legame:nodoGenitori ); // i figli dei fratelli no
				// Le linee ad arco per fratelli senza genitori
				if( nodoGenitori == null ) {
					int generaz = 3;
					rete.add( new Corda( nodoIo, scatola.findViewById( generaz ), nodoFratello, true ) );
				}
			}
			// Mette il paiolo di origine agli arcobaleni sprovvisti (livello fratelli)
			for( Corda corda : rete ) {
				if( corda.arco && corda.origine==null )
					corda.origine = nodoIo;
			}
			// Completa ramo materno
			if( madre != null ) {
				View paioloMadre = nodoGenitori.findViewWithTag( "madrePaiolo" );
				altriMatrimoni( madre, famiglia, paioloMadre!=null ? paioloMadre : nodoGenitori );	// todo: il nodo della madre viene giusto nel diagramma?
				zii( madre, nodoNonniMaterni, false );
				for( Corda corda : rete ) {
					if( corda.arco && corda.origine == null )
						corda.origine = paioloMadre!=null ? paioloMadre : nodoGenitori;
				}
			}
			spazio(1, zii(madre,null,true));
		} else	// individuo senza genitori
			ioFigliNipoti( centro, null );

		// Aggiunge le linee e centra il diagramma
		scatolaZoom.postDelayed( new Runnable() {
			public void run() {
				View nodoCentrale = null;  // per centrare il diagramma
				for( Corda corda : rete ) {
					if( corda.mezzo == null && corda.fine == null ) { // trucchetto per sfruttare rete come veicolo della View centrale
						if( nodoCentrale == null )  // così se eventualmente il centro compare più volte, prende il primo
							nodoCentrale = corda.origine;
					} else
						disegnaLinea( corda.origine, corda.mezzo, corda.fine, corda.arco );
				}
				scatolaZoom.setMinZoom( 1, ZoomApi.TYPE_ZOOM ); // occorrerebbe solo la prima volta
				if( nodoCentrale != null ) {
					Rect margini = new Rect();
					nodoCentrale.getDrawingRect( margini );
					scatola.offsetDescendantRectToMyCoords( nodoCentrale, margini );
					scatolaZoom.panTo( -margini.exactCenterX() + scatolaZoom.getWidth() / scatolaZoom.getRealZoom() / 2,
							-margini.exactCenterY() + scatolaZoom.getHeight() / scatolaZoom.getRealZoom() / 2, true );
				}
			}
		}, 100 );
	}

	void creaBottonePrimoIndividuo() {
		Button botto = new Button(getContext());
		botto.setText( R.string.new_person );
		int generaz = 3;
		((LinearLayout)scatola.findViewById( generaz )).addView( botto,
				new LinearLayout.LayoutParams( ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT ) );
		botto.setOnClickListener( new View.OnClickListener() {
			@Override
			public void onClick( View view ) {
				Intent intento =  new Intent( getContext(), EditaIndividuo.class );
				intento.putExtra( "idIndividuo", "TIZIO_NUOVO" );
				startActivity( intento );
			}
		} );
	}

	// Aggiunge uno spazio per distanziare i nonni
	void spazio( int generazione, int peso ) {
		if( peso == 0 ) peso = 1;
		Space spaz = new Space( getContext() );
		LinearLayout.LayoutParams param = new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, peso );
		LinearLayout gen = scatola.findViewById( generazione );
		gen.addView( spaz, param );
	}

	// Colloca una o DUE Person nel diagramma
	private View inserisci( int generazione, Person egli, View nodoSopra, boolean conDiscendenti ) {
		View nodo;
		if( !egli.getSpouseFamilies(gc).isEmpty() ) {
			Family fam = egli.getSpouseFamilies(gc).get(0);
			if( U.sesso(egli)==1 && !fam.getWives(gc).isEmpty() )	// Maschio ammogliato
				nodo = schedaDoppia( generazione, fam, nodoSopra, null, 2, conDiscendenti, 0 );
			else if( U.sesso(egli)==2 && !fam.getHusbands(gc).isEmpty() )	// Femmina ammogliata
				nodo = schedaDoppia( generazione, fam, null, nodoSopra, 1, conDiscendenti, 0 );
			else
				nodo = schedaSingola( generazione, egli, nodoSopra, false, conDiscendenti, 0 );	// senza sesso (o senza coniuge?)
		} else
			nodo = schedaSingola( generazione, egli, nodoSopra, false, conDiscendenti, 0 );
		return nodo;
	}

	// LinearLayout che aggiunge i discendenti sotto una scheda singola o doppia
	class ConDiscendenti extends LinearLayout {
		public ConDiscendenti( LinearLayout contenuto, Person egli, View nodoSopra  ) {
			super( contenuto.getContext() );
			setLayoutParams( new LinearLayout.LayoutParams( ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT ) );
			setOrientation( LinearLayout.VERTICAL );
			setGravity( Gravity.CENTER_HORIZONTAL );
			setClipChildren( false );
			addView( contenuto );
			List<Discendente> discendi = discendenti(egli);
			if( discendi.size() > 0 ) {
				LayoutParams paramContenuto = (LayoutParams) contenuto.getLayoutParams();
				paramContenuto.topMargin = 65;
				contenuto.setLayoutParams( paramContenuto );
				LinearLayout scatolaDiscendenti = new LinearLayout( getContext() );
				for( final Discendente disc : discendi ) {
					View vistaDiscend = getLayoutInflater().inflate( R.layout.diagramma_discendente, this, false );
					scatolaDiscendenti.addView( vistaDiscend );
					( (TextView) vistaDiscend.findViewById( R.id.num_discendenti ) ).setText( String.valueOf( disc.prole ) );
					if( disc.sesso == 1 )
						vistaDiscend.setBackgroundResource( R.drawable.casella_maschio );
					else if( disc.sesso == 2 )
						vistaDiscend.setBackgroundResource( R.drawable.casella_femmina );
					rete.add( new Corda( nodoSopra, null, vistaDiscend, false ) );
					vistaDiscend.setOnClickListener( new OnClickListener() {
						@Override
						public void onClick( View view ) {
							if( U.qualiGenitoriMostrare( Diagramma.this.getContext(), disc.egli, Principe.class ) )
								return;
							scatolona.removeAllViews();
							Globale.individuo = disc.egli.getId();
							disegna();
						}
					} );
				}
				LayoutParams paramDiscend = new LayoutParams( LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT );
				paramDiscend.topMargin = 35;
				addView( scatolaDiscendenti, paramDiscend );
			}
		}
	}

	// tratteggio orizzontale per rappresentare i matrimoni multipli
	ImageView tratteggia() {
		ImageView tratto = new ImageView( getContext() );
		tratto.setLayoutParams( new LinearLayout.LayoutParams( 45, 10 ) );
		tratto.setLayerType( View.LAYER_TYPE_SOFTWARE, null );  // necessario per far comparire il diagramma_tratteggio
		tratto.setImageResource( R.drawable.diagramma_tratteggio );
		return tratto;
	}

	// Inserisce la scheda di una persona nel diagramma
	// configura: 0 normale, 1 tratteggiato (rarissimo caso di matrimoni multipli senza il coniuge)
	private LinearLayout schedaSingola( int generazione, Person egli, View nodoSopra, boolean conAntenati, boolean conDiscendenti, int configura ) {
		LinearLayout scatolaSingola;
		LinearLayout gen = scatola.findViewById( generazione );
		if( configura == 0 ) {
			scatolaSingola = new Schedina( generazione, egli, conAntenati );
			rete.add( new Corda( nodoSopra, gen, scatolaSingola, false ) );
		} else {
			scatolaSingola = new LinearLayout( getContext() );
			scatolaSingola.addView( tratteggia() );
		}
		LinearLayout.LayoutParams param = new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT );
		param.setMargins(8,8,8,8);
		if( conDiscendenti ) {
			gen.addView( new ConDiscendenti( scatolaSingola, egli, scatolaSingola ), param );
		} else
			gen.addView( scatolaSingola, param );
		return scatolaSingola;
	}

	// Inserisce la scheda di una coppia nel diagramma
	// Oppure la scheda di uno dei matrimoni del centro
	// conAntenati: 0 nessuno, 1 lui, 2 lei, 3 entrambi
	// configura: 0 coppia normale, 1 altri matrimoni lui, 2 altri matrimoni lei
	private View schedaDoppia( int generazione, final Family famiglia, View nodoSopraLui, View nodoSopraLei,
							   int conAntenati, boolean conDiscendenti, int configura ) {
		boolean conAntenatiLui = false;
		boolean conAntenatiLei = false;
		if( conAntenati == 1 || conAntenati == 3 )
			conAntenatiLui = true;
		if( conAntenati == 2 || conAntenati == 3 )
			conAntenatiLei = true;
		LinearLayout scatolaCoppia = new LinearLayout( getContext() );
		scatolaCoppia.setOrientation(LinearLayout.HORIZONTAL);
		scatolaCoppia.setLayoutParams( new LinearLayout.LayoutParams( ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT ) );
		scatolaCoppia.setGravity( Gravity.CENTER_VERTICAL );
		scatolaCoppia.setClipChildren( false );
		Person lui = null, lei = null;
		if( !famiglia.getHusbands(gc).isEmpty() )
			lui = famiglia.getHusbands(gc).get(0);
		if( !famiglia.getWives(gc).isEmpty() )
			lei = famiglia.getWives(gc).get(0);
		LinearLayout gen = scatola.findViewById( generazione );
		ImageView tratto = new ImageView( getContext() );
		tratto.setLayoutParams( new LinearLayout.LayoutParams( 45, 10 ) );
		tratto.setLayerType( View.LAYER_TYPE_SOFTWARE, null );  // necessario per far comparire il diagramma_tratteggio
		tratto.setImageResource( R.drawable.diagramma_tratteggio );
		if( configura == 1 )
			scatolaCoppia.addView( tratto );
		else if( lui != null ) {
			LinearLayout schedinaLui = new Schedina( generazione, lui, conAntenatiLui );
			scatolaCoppia.addView( schedinaLui );
			rete.add( new Corda( nodoSopraLui, gen, schedinaLui, false ) );
			if( conAntenatiLei ) // Marchia la schedina per gli archi che collegano fratelli
				schedinaLui.setTag("paiolo");
			else if( conAntenati == 0 ) // Marchia la schedina per gli archi che collegano zii
				schedinaLui.setTag("padrePaiolo");
		}
		// Legame di matrimonio con eventuale anno
		String dataMatrimonio = null;
		for( EventFact ef : famiglia.getEventsFacts() ) {
			if( ef.getTag().equals("MARR") )
				dataMatrimonio = ef.getDate();
		}
		FrameLayout legame = new FrameLayout( getContext() );
		if( dataMatrimonio == null ) {
			View linea = new View( getContext() );
			LinearLayout.LayoutParams paramLinea = new LinearLayout.LayoutParams( ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT );
			paramLinea.width = 25;
			paramLinea.height = 4;
			linea.setBackgroundColor( 0xffffffff );
			legame.addView( linea, paramLinea );
		} else {
			TextView anno = new TextView( getContext() );
			anno.setBackgroundResource( R.drawable.diagramma_cerchio_anno );
			anno.setPadding(4,4,4,4);
			anno.setText( new Datatore(dataMatrimonio).scriviAnno() );
			legame.addView( anno );
		}
		legame.setOnClickListener( new View.OnClickListener() {
			@Override
			public void onClick( View view ) {
				Memoria.setPrimo( famiglia );
				startActivity( new Intent( getContext(), Famiglia.class ) );
			}
		} );
		legame.setTag("legame");
		scatolaCoppia.addView( legame );
		// Lei
		if( configura == 2 )
			scatolaCoppia.addView( tratto );
		else if( lei != null ) {
			LinearLayout schedinaLei = new Schedina( generazione, lei, conAntenatiLei );
			scatolaCoppia.addView( schedinaLei );
			rete.add( new Corda( nodoSopraLei, gen, schedinaLei, false ) );
			if( conAntenatiLui )
				schedinaLei.setTag("paiolo");
			else if( conAntenati == 0 )
				schedinaLei.setTag("madrePaiolo");
		}
		LinearLayout.LayoutParams param = new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT );
		param.setMargins(8,8,8,8);
		if( configura == 1 )
			param.leftMargin = -5;
		else if( configura == 2 )
			param.rightMargin = -5;
		if( conDiscendenti ) {
			gen.addView( new ConDiscendenti( scatolaCoppia, lui, legame ), param );
		} else
			gen.addView( scatolaCoppia, param );
		return scatolaCoppia;
	}

	// La schedina base del diagramma
	private class Schedina extends LinearLayout {
		String id;
		Schedina( int generazione, final Person egli, boolean conAntenati ) {   //, boolean conDiscendenti
			super( Globale.contesto );	// necessario per estendere View
			id = egli.getId(); // per il menu contestuale
			setLayoutParams( new LinearLayout.LayoutParams( LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT ) );
			setOrientation( LinearLayout.VERTICAL );
			setGravity( Gravity.CENTER_HORIZONTAL );
			View vista = getLayoutInflater().inflate( R.layout.diagramma_pezzo, this, false );
			ImageView sfondo = vista.findViewById( R.id.schedina_sfondo );
			if( conAntenati ) {
				final Antenato[] avi = antenati( egli );
				View avetti = getLayoutInflater().inflate( R.layout.diagramma_avi, this, false );
				TextView testoAvi = avetti.findViewById( R.id.num_avi );
				TextView testoAve = avetti.findViewById( R.id.num_ave );
				if( avi[0] == null ) {
					testoAvi.setVisibility( View.GONE );
					RelativeLayout.LayoutParams param = (RelativeLayout.LayoutParams) testoAve.getLayoutParams();
					param.addRule( RelativeLayout.RIGHT_OF, 0 );
				} else {
					testoAvi.setText( String.valueOf( avi[0].ascendenza ) );
					testoAvi.setOnClickListener( new OnClickListener() {
						@Override
						public void onClick( View v ) {
							if( U.qualiGenitoriMostrare( Diagramma.this.getContext(), avi[0].egli, Principe.class ) )
								return;
							scatolona.removeAllViews();
							Globale.individuo = avi[0].egli.getId();
							disegna();
						}
					} );
				}
				if( avi[1] == null ) {
					testoAve.setVisibility( View.GONE );
					RelativeLayout.LayoutParams param = (RelativeLayout.LayoutParams) avetti.findViewById( R.id.avi_connettore ).getLayoutParams();
					param.addRule( RelativeLayout.RIGHT_OF, 0 );
				} else {
					testoAve.setText( String.valueOf( avi[1].ascendenza ) );
					testoAve.setOnClickListener( new OnClickListener() {
						@Override
						public void onClick( View v ) {
							if( U.qualiGenitoriMostrare( Diagramma.this.getContext(), avi[1].egli, Principe.class ) )
								return;
							scatolona.removeAllViews();
							Globale.individuo = avi[1].egli.getId();
							disegna();
						}
					} );
				}
				if( avi[0]!=null || avi[1]!=null ) {
					addView( avetti );
					// per allineare i due coniugi di cui questo con corona di avi in testa
					LayoutParams lp = new LayoutParams(	LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT );
					lp.bottomMargin = 28;
					setLayoutParams( lp ); // ok, gli avi sopra verrebbero clippati, ma possono fuoriuscire con setClipChildren(false)
				}
				if( generazione > 1 ) { // i nonni non vengono alphizzati anche se hanno gli avi
					avetti.setAlpha( 0.7f );
					sfondo.setAlpha( 0.7f );
				}
			}

			if( egli.getId().equals( Globale.individuo ) ) {
				sfondo.setBackgroundResource( R.drawable.casella_evidente );
			} else if( U.sesso(egli) == 1 )
				sfondo.setBackgroundResource( R.drawable.casella_maschio );
			else if( U.sesso(egli) == 2 )
				sfondo.setBackgroundResource( R.drawable.casella_femmina );
			U.unaFoto( Globale.gc, egli, (ImageView) vista.findViewById( R.id.schedina_foto ) );
			TextView vistaNome = vista.findViewById(R.id.schedina_nome);
			String nome = U.epiteto(egli);
			if( nome.isEmpty() && vista.findViewById(R.id.schedina_foto).getVisibility()==VISIBLE ) vistaNome.setVisibility( GONE );
			else vistaNome.setText( nome );
			TextView vistaTitolo = vista.findViewById(R.id.schedina_titolo);
			String titolo = U.titolo( egli );
			if( titolo.isEmpty() ) vistaTitolo.setVisibility( GONE );
			else vistaTitolo.setText( titolo );
			TextView vistaDati = vista.findViewById(R.id.schedina_dati);
			String dati = U.dueAnni( egli, true );
			if( dati.isEmpty() ) vistaDati.setVisibility(GONE);
			else vistaDati.setText( dati );
			if( !U.morto(egli) )
				vista.findViewById(R.id.schedina_lutto).setVisibility(GONE);
			addView( vista );
			registerForContextMenu(this);
			setOnClickListener( new OnClickListener() {
				@Override
				public void onClick( View vista ) {
					if( egli.getId().equals(Globale.individuo) ) {
						Intent intento = new Intent( getContext(), Individuo.class );
						intento.putExtra( "idIndividuo", egli.getId() );
						startActivity( intento );
					} else {
						if( U.qualiGenitoriMostrare( Diagramma.this.getContext(), egli, Principe.class ) )
							return;
						scatolona.removeAllViews();
						Globale.individuo = egli.getId();
						disegna();
					}
				}
			});
			if( egli.getId().equals( Globale.individuo ) && !conAntenati )
				rete.add( new Corda( this, null, null, false ) );  // sfrutto rete per veicolare il centro del diagramma
		}
	}

	// Un blocco ripetitivo per inserire i nonni
	private View nonni( Person genitore ) {
		View nodoNonni = null;
		if( !genitore.getParentFamilies(gc).isEmpty() ) {
			Family fam = genitore.getParentFamilies(gc).get(0);
			// Nonni
			if( !fam.getHusbandRefs().isEmpty() && !fam.getWifeRefs().isEmpty() )	// ci sono entrambi i nonni
				nodoNonni = schedaDoppia( 1, fam, null, null, 3, false, 0 );
			else if( !fam.getHusbandRefs().isEmpty() )
				nodoNonni = schedaSingola( 1, fam.getHusbands(gc).get(0), null, true, false, 0 );
			else if( !fam.getWifeRefs().isEmpty() )
				nodoNonni = schedaSingola( 1, fam.getWives(gc).get(0), null, true, false, 0 );
		}
		View legame = null;
		if( nodoNonni != null )
			legame = nodoNonni.findViewWithTag( "legame" );
		return legame != null ? legame : nodoNonni;
	}

	// Gli zii (senza cugini)
	private int zii( Person genitore, View nodoNonni, boolean contaZii ) {
		if( genitore != null )
			if( !genitore.getParentFamilies(gc).isEmpty() ) {
				Family fam = genitore.getParentFamilies(gc).get(0);
				// Zii
				List<Person> zii = fam.getChildren(gc);
				if( contaZii ) return zii.size();   // serve per posizionare i nonni
				zii.remove(genitore);
				for( Person zio : zii ) {
					View nodoZio = inserisci( 2, zio, nodoNonni, true );
					View paiolo = nodoZio.findViewWithTag( "paiolo" );
					if( nodoNonni == null ) {
						int generaz = 2;
						rete.add( new Corda( null, scatola.findViewById(generaz), paiolo!=null?paiolo:nodoZio, true ) );
					}
				}
			}
		return 0;
	}

	// Fratellastri nati dai matrimoni precedenti o seguenti dei genitori
	private void altriMatrimoni( Person genitore, Family famiglia, View nodoGenitore ) {
		List<Family> altreFamiglie = genitore.getSpouseFamilies(gc);
		altreFamiglie.remove( famiglia );
		for( Family altraFamiglia : altreFamiglie ) {
			for( Person fratellastro : altraFamiglia.getChildren(gc) )
				inserisci( 3, fratellastro, nodoGenitore, true );
		}
	}

	// Il centro con i suoi matrimoni + figli e nipoti
	View ioFigliNipoti( Person io, View nodoGenitori ) {
		List<Family> matrimoni = io.getSpouseFamilies(gc);
		View nodoCentro = null;
		if( matrimoni.isEmpty() ) { // centro non si è sposato e non ha figli
			nodoCentro = schedaSingola( 3, io, nodoGenitori, false, false, 0 );
		} else {
			int configura = 0; // indica come mostrare la coppia (se ci sono matrimoni precedenti)
			for( int i = 0; i < matrimoni.size(); i++ ) { // i molteplici matrimoni del centro
				Family famig = matrimoni.get(i);
				if( U.sesso(io) == 1 && !famig.getWives(gc).isEmpty() ) { // maschio con moglie/i
					if( i > 0) configura = 1; // dal secondo matrimonio in poi
					nodoCentro = schedaDoppia( 3, famig, nodoGenitori, null, 2, false, configura );
				} else if( U.sesso(io) == 2 && !famig.getHusbands(gc).isEmpty() ) { // femmina con marito/i
					if( i+1 == matrimoni.size() ) configura = 0; // ultimo matrimonio
					else configura = 2; // matrimoni precedenti
					nodoCentro = schedaDoppia( 3, famig, null, nodoGenitori, 1, false, configura );
				} else { // centro neutro o senza coniuge
					if( i > 0) configura = 1;
					nodoCentro = schedaSingola( 3, io, nodoGenitori, false, false, configura);
				}
				View legame = nodoCentro.findViewWithTag("legame");
				for( Person figlio : famig.getChildren(gc) ) {
					View nodoFiglio = inserisci( 4, figlio, legame!=null?legame:nodoCentro, false );
					if( !figlio.getSpouseFamilies(gc).isEmpty() ) {
						Family fam = figlio.getSpouseFamilies( gc ).get( 0 );
						View legame2 = nodoFiglio.findViewWithTag("legame");
						for( Person nipote : fam.getChildren( gc ) )
							inserisci( 5, nipote, legame2!=null?legame2:nodoFiglio, true );
					}
				}
			}
		}
		View paiolo = nodoCentro.findViewWithTag("paiolo");
		return paiolo != null ? paiolo : nodoCentro;
	}

	// I fratelli del centro con i rispettivi figli
	View fratelloNipoti( Person fratello, View nodoGenitori ) {
		View nodoFratello = inserisci( 3, fratello, nodoGenitori, false );
		if( !fratello.getSpouseFamilies(gc).isEmpty() ) {
			Family famig = fratello.getSpouseFamilies(gc).get(0);
			View legame = nodoFratello.findViewWithTag("legame");
			for( Person nipote : famig.getChildren(gc) )
				inserisci(4, nipote, legame!=null?legame:nodoFratello, true );
		}
		View paiolo = nodoFratello.findViewWithTag("paiolo");
		return paiolo != null ? paiolo : nodoFratello;
	}

	// classe per memorizzare i dati delle caselline antenati
	class Antenato {
		Person egli;
		int ascendenza;
		public Antenato( Person egli, int ascendenza ) {
			this.egli = egli;
			this.ascendenza = ascendenza;
		}
	}

	// di una Person scrive e restituisce una coppietta di classi Antenato
	private Antenato[] antenati( Person capo ) {
		Antenato[] avi = new Antenato[2];
		if( !capo.getParentFamilies(gc).isEmpty() ) {
			Family fam = capo.getParentFamilies(gc).get(0);
			if( !fam.getHusbands(gc).isEmpty() ) {
				gente = 1;
				contaAntenati( fam.getHusbands(gc).get(0) );
				avi[0] = new Antenato( fam.getHusbands(gc).get(0), gente);
			}
			if( !fam.getWives(gc).isEmpty() ) {
				gente = 1;
				contaAntenati( fam.getWives(gc).get(0) );
				avi[1] = new Antenato( fam.getWives(gc).get(0), gente);
			}
		}
		return avi;
	}

	// contatore ricorsivo degli antenati DIRETTI
	private void contaAntenati( Person p ) {
		if( gente < 100 )
		for( Family f : p.getParentFamilies(gc) ) {
			for( Person pa : f.getHusbands(gc) ) {
				gente++;
				contaAntenati( pa );
			}
			for( Person ma : f.getWives(gc) ) {
				gente++;
				contaAntenati( ma );
			}
		}
	}

	// classe per memorizzare i dati delle caselline discendenti
	class Discendente {
		Person egli;
		int sesso;
		int prole;
		public Discendente( Person egli, int sesso, int prole ) {
			this.egli = egli;
			this.sesso = sesso;
			this.prole = prole;
		}
	}

	// di una Person restituisce una lista di figli col numero di rispettivi discendenti
	private List<Discendente> discendenti( Person p ) {
		List<Discendente> sfilza = new ArrayList<>();
		for( Family famiglia : p.getSpouseFamilies(gc) )
			for( Person nipote : famiglia.getChildren(gc) ) {
				gente = 1;
				contaDiscendenti( nipote );
				sfilza.add( new Discendente( nipote, U.sesso(nipote), gente ) );
			}
		return sfilza;
	}

	// conta ricorsivamente i discendenti
	private void contaDiscendenti( Person p ) {
		if( gente < 500 )
			for( Family fam : p.getSpouseFamilies(gc) )
				for( Person figlio : fam.getChildren(gc) ) {
					gente++;
					contaDiscendenti( figlio );
				}
	}

	//boolean alterna;
	void disegnaLinea( View vistaInizio, View vistaMezzo, View vistaFine, boolean arco ) {
		/*s.l( "disegnaLinea: ");
		if( vistaInizio != null ) s.l( "vistaInizio  "+ vistaInizio.getClass() + "\t" + vistaInizio.getScrollX()+ " " + vistaInizio.getScrollY()+ " " + vistaInizio.getWidth()+ " " + vistaInizio.getHeight() );
		if( vistaMezzo != null ) s.l( "vistaMezzo  "+ vistaMezzo.getClass() + "\t" + vistaMezzo.getScrollX()+ " " + vistaMezzo.getScrollY()+ " " + vistaMezzo.getWidth()+ " " + vistaMezzo.getHeight() );
		if( vistaFine != null ) s.l( "vistaFine  "+ vistaFine.getClass() + "\t" + vistaFine.getScrollX()+ " " + vistaFine.getScrollY()+ " " + vistaFine.getWidth()+ " " + vistaFine.getHeight() );*/
		if( vistaInizio != null && vistaFine != null ) {
			// Coordinate assolute start
			Rect marginiInizio = new Rect();
			vistaInizio.getDrawingRect( marginiInizio );
			//s.l( "marginiInizio = " + marginiInizio.top +"  "+ marginiInizio.right +"  "+ marginiInizio.bottom +"  "+ marginiInizio.left );
			scatolona.offsetDescendantRectToMyCoords( vistaInizio, marginiInizio );
			// fine
			Rect marginiFine = new Rect();
			vistaFine.getDrawingRect( marginiFine );
			scatolona.offsetDescendantRectToMyCoords( vistaFine, marginiFine );
			// mezzo
			int mezzoY;
			if( vistaMezzo != null ) {
				Rect marginiMezzo = new Rect();
				vistaMezzo.getDrawingRect( marginiMezzo );
				scatolona.offsetDescendantRectToMyCoords( vistaMezzo, marginiMezzo );
				mezzoY = marginiMezzo.top;
				//mezzoY += alterna ? 10 : -10; // TODO
				//alterna = !alterna;
			} else
				mezzoY = (int) ( marginiInizio.bottom + ( marginiFine.top - marginiInizio.bottom ) / 1.3 );
			//s.l( (int) marginiInizio.exactCenterX()+"-"+marginiInizio.bottom +"  "+ mezzoY +"  "+ (int) marginiFine.exactCenterX()+"-"+marginiFine.top );
			View linea = new Linea( (int) marginiInizio.exactCenterX(),
					arco ? marginiInizio.top : marginiInizio.bottom,
					mezzoY, (int) marginiFine.exactCenterX(), marginiFine.top );
			RelativeLayout.LayoutParams paramLinea = new RelativeLayout.LayoutParams( scatolona.getWidth(), scatolona.getHeight() );
			scatolona.addView( linea, paramLinea );
		}
	}

	class Linea extends View {
		Paint paint = new Paint();
		Path path = new Path();
		int xInizio, yInizio, yMezzo, xFine, yFine;
		public Linea( int x1, int y1, int y2, int x3, int y3 ) {
			super( Globale.contesto );
			xInizio = x1;
			yInizio = y1;
			yMezzo = y2;
			xFine = x3;
			yFine = y3;
		}
		@Override
		protected void onDraw(Canvas canvas) {
			paint.setStyle( Paint.Style.STROKE );
			paint.setColor( Color.WHITE );
			paint.setStrokeWidth(3);	// misura in pixel
			path.moveTo( xInizio, yInizio );
			path.lineTo( xInizio, yMezzo );
			path.lineTo( xFine, yMezzo );
			path.lineTo( xFine, yFine );
			canvas.drawPath( path, paint );
		}
	}

	// Menu contestuale
	String idPersona;
	View vistaScelta;
	Person pers;
	@Override
	public void onCreateContextMenu( ContextMenu menu, View vista, ContextMenu.ContextMenuInfo info ) {
		vistaScelta = vista;
		idPersona = ((Schedina)vista).id;
		pers = gc.getPerson( idPersona );
		if( !idPersona.equals(Globale.individuo) )
			menu.add(0, 0, 0, R.string.card );
		if( !pers.getParentFamilies(gc).isEmpty() )
			menu.add(0, 1, 0, pers.getSpouseFamilies(gc).isEmpty() ? R.string.family : R.string.family_as_child );
		if( !pers.getSpouseFamilies(gc).isEmpty() )
			menu.add(0, 2, 0, pers.getParentFamilies(gc).isEmpty() ? R.string.family : R.string.family_as_spouse );
		menu.add(0, 3, 0, R.string.new_relative);
		if( U.ciSonoIndividuiCollegabili(gc.getPerson(idPersona)) )
			menu.add(0, 4, 0, R.string.link_person);
		menu.add(0, 5, 0, R.string.modify);
		if( !pers.getParentFamilies(gc).isEmpty() || !pers.getSpouseFamilies(gc).isEmpty() )
			menu.add(0, 6, 0, R.string.unlink);
		menu.add(0, 7, 0, R.string.delete);
	}
	@Override
	public boolean onContextItemSelected( MenuItem item ) {
		CharSequence[] parenti = { getText(R.string.parent), getText(R.string.sibling), getText(R.string.spouse), getText(R.string.child) };
		int id = item.getItemId();
		if( id == 0 ) {	// Apri scheda individuo
			Intent intento = new Intent( getContext(), Individuo.class );
			intento.putExtra( "idIndividuo", idPersona );
			startActivity( intento );
		} else if( id == 1) {	// Famiglia come figlio
			U.qualiGenitoriMostrare( getContext(), pers, Famiglia.class );
		} else if( id == 2 ) {	// Famiglia come coniuge
			U.qualiConiugiMostrare( getContext(), pers );
		} else if( id == 3 ) {	// Aggiungi parente
			new AlertDialog.Builder( getActivity() ).setItems( parenti, new DialogInterface.OnClickListener() {
				@Override
				public void onClick( DialogInterface dialog, int quale ) {
					Intent intento = new Intent( getContext(), EditaIndividuo.class );
					intento.putExtra( "idIndividuo", idPersona );
					intento.putExtra( "relazione", quale + 1 );
					if( EditaIndividuo.controllaMultiMatrimoni(intento,getContext(),null) )
						return; // se perno è sposo in più famiglie dialogo chiede a chi aggiungere un figlio
					startActivity( intento );
				}
			}).show();
		} else if( id == 4 ) {	// Collega persona
			new AlertDialog.Builder( getActivity() ).setItems( parenti, new DialogInterface.OnClickListener() {
				@Override
				public void onClick( DialogInterface dialog, int quale ) {
					Intent intento = new Intent( getContext(), Principe.class );
					intento.putExtra( "idIndividuo", idPersona ); // serve solo a quel pistino di controllaMultiMatrimoni()
					intento.putExtra( "anagrafeScegliParente", true );
					intento.putExtra( "relazione", quale + 1 );
					if( EditaIndividuo.controllaMultiMatrimoni(intento,getContext(),Diagramma.this) )
						return;
					startActivityForResult( intento,1401 );
				}
			}).show();
		} else if( id == 5 ) {	// Modifica
			Intent intento = new Intent( getContext(), EditaIndividuo.class );
			intento.putExtra( "idIndividuo", idPersona );
			startActivity( intento );
		} else if( id == 6 ) {	// Scollega
			Family[] famiglie = Anagrafe.scollega( idPersona );
			getActivity().recreate();
			Snackbar.make( getView(), R.string.person_unlinked, Snackbar.LENGTH_LONG ).show();
			U.aggiornaDate( pers );
			U.salvaJson( false, (Object[])famiglie );
		} else if( id == 7 ) {	// Elimina
			Anagrafe.elimina( idPersona, getContext(), vistaScelta );
			//getActivity().recreate(); todo
		} else
			return false;
		return true;
	}

	// Aggiunge il parente che è stata scelto in Anagrafe
	@Override
	public void onActivityResult( int requestCode, int resultCode, Intent data ) {
		if( requestCode == 1401  ) {
			if( resultCode == AppCompatActivity.RESULT_OK ) {
				Object[] modificati = EditaIndividuo.aggiungiParente( idPersona,
						data.getStringExtra( "idParente" ),
						data.getIntExtra( "relazione", 0 ),
						data.getIntExtra( "famigliaNum", 0 ));
				U.salvaJson( true, modificati );
			}
		}
	}
}