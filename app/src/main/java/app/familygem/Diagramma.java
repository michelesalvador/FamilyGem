package app.familygem;

import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.folg.gedcom.model.EventFact;
import org.folg.gedcom.model.Family;
import org.folg.gedcom.model.Person;
import app.familygem.dettaglio.Famiglia;
import static app.familygem.Globale.gc;

public class Diagramma extends Fragment {

	ZoomLayout scatolaZoom;
	RelativeLayout scatolona;
	LinearLayout scatola;
	//Person centro;
	private int gente;
	//public Diagramma() {}

    @Override
    public void onCreate( Bundle stato ) {
        super.onCreate( stato );
		//Globale.frammentoPrecedente = this;
    }

    /* onResume servirebbe a Rinfrescare il diagramma
    	Si attiva ad ogni:
    	 - avvio del programma
    	 - riaccensione dello schermo
    	 - ruotando lo schermo
    	 - ritornando a questo fragment con backpressed
	@Override
	public void onResume() {
		super.onResume();
		disegna( Globale.individuo );
	} */

    @Override
    public View onCreateView( LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        ((AppCompatActivity) getActivity()).getSupportActionBar().setTitle( Globale.preferenze.alberoAperto().nome );
        final View vista = inflater.inflate( R.layout.diagramma, container, false );

		scatolona = vista.findViewById( R.id.diagramma_scatolona );
		//scatola.setBackgroundColor( 0x3366ff99 );
	    scatolaZoom = vista.findViewById( R.id.diagramma_zoom );

		if( gc != null )
			disegna();

		/*LinearLayout figli = vista.findViewById( R.id.figli );
		for(int i=0; i<3; i++) {
			View scheda4 = inflater.inflate( R.layout.diagramma_pezzo, figli, false );
			figli.addView( scheda4 );
		}*/
		//View scheda4 = DiagrammaFragment.nuovo( scatola, linea );
		//scheda4.setRotation(45);

		return vista;
    }

	// Il libro mastro in cui inserire le coppie di View da collegare con linee
	List<Corda> rete;
	class Corda {
		View origine;
		View mezzo;
		View fine;
		Corda( View origine, View mezzo, View fine ) {
			this.origine = origine;
			this.mezzo = mezzo;
			this.fine = fine;
		}
	}

	/* Sostituito da onRestart recreate()
	public void rinfresca() {
		scatolona.removeAllViews();
		disegna();
	}*/

    // Riempie le file di generazioni
	void disegna() {
		scatola = new LinearLayout( scatolona.getContext() );
		scatola.setOrientation(LinearLayout.VERTICAL);
		scatola.setGravity( Gravity.CENTER_HORIZONTAL );
		//scatola.setBackgroundColor( 0x33ff6699 );
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
			//generaz.setBackgroundColor( 0x339933ff + 0x6666*g );	// ok, equivale a impostarlo nel xml
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
			centro = gc.getPerson( U.trovaRadice(gc) );
			if( centro == null ) {
				creaBottonePrimoIndividuo();    // se non ci sono persone mette un bel bottone per aggiungere la prima persona
				return;
			}
			Globale.preferenze.alberoAperto().radice = centro.getId();  // todo ma chi è che usa poi questa 'radice'?
		}
		// Risale ai nonni
		if( !centro.getParentFamilies(gc).isEmpty() ) {	// qui ci va eventuale scelta di QUALI genitori mostrare se centro ha più genitori
			Family famiglia = centro.getParentFamilies(gc).get(0);
			// Ramo paterno
			Person padre = null;
			View nodoNonniPaterni = null;
			if( !famiglia.getHusbands(gc).isEmpty() ) {
				padre = famiglia.getHusbands(gc).get(0);
				spazio(1, zii(padre,null, true) );
				//nodoNonni =
				nodoNonniPaterni = nonni( padre );
				zii( padre, nodoNonniPaterni, false );
				altriMatrimoni( padre, famiglia, null );	// todo: in nodoGenitori dovrebbe esserci il nodo del padre, che però viene creato dopo..
			}
			// Inizia ramo materno
			Person madre = null;
			View nodoNonniMaterni = null;
			View nodoGenitori = null;
			if( !famiglia.getWives(gc).isEmpty() ) {
				madre = famiglia.getWives(gc).get(0);
				spazio(1, (zii(padre,null,true) + zii(madre,null,true))/2 );
				nodoNonniMaterni = nonni( madre );
			}
			// Genitori
			if( padre != null && madre != null )
				nodoGenitori = schedaDoppia( 2, famiglia, padre, madre, nodoNonniPaterni, nodoNonniMaterni, 0, false, 0 );
			else if( padre != null )
				nodoGenitori = schedaSingola( 2, padre, nodoNonniPaterni, false, false, 0 );
			else if( madre != null )
				nodoGenitori = schedaSingola( 2, madre, nodoNonniMaterni, false, false, 0 );
			// Fratelli (tra cui centro), figli e nipoti
			for( Person fratello : famiglia.getChildren(gc) )
				if( fratello.equals(centro) )
					ioFigliNipoti( fratello, nodoGenitori );  // i figli di centro mostrereanno anche i propri figli
				else
					fratelloNipoti( fratello, nodoGenitori ); // i figli dei fratelli no
			// Completa ramo materno
			if( madre != null ) {
				altriMatrimoni( madre, famiglia, nodoGenitori );	// todo: nodoGenitori così è sbagliato, deve essere il nodo solo della madre
				zii( madre, nodoNonniMaterni, false );
			}
			spazio(1, zii(madre,null,true));
		} else	// individuo senza genitori
			ioFigliNipoti( centro, null );

		// Aggiunge le linee e centra il diagramma
		scatolaZoom.postDelayed( new Runnable() {
			public void run() {
				s.l( "rete.size " + rete.size() );
				View nodoCentrale = null;  // per centrare il diagramma
				for( Corda corda : rete ) {
					if( corda.mezzo == null && corda.fine == null ) { // trucchetto per sfruttare rete come veicolo della View centrale
						if( nodoCentrale == null )  // così se eventualmente il centro compare più volte, prende il primo
							nodoCentrale = corda.origine;
					} else
						disegnaLinea( corda.origine, corda.mezzo, corda.fine );
				}
				scatolaZoom.setMinZoom( 1, ZoomApi.TYPE_ZOOM ); // occorrerebbe solo la prima volta
				if( nodoCentrale != null ) {
					Rect margini = new Rect();
					nodoCentrale.getDrawingRect( margini );
					scatola.offsetDescendantRectToMyCoords( nodoCentrale, margini );
					//s.l( margini.exactCenterX() +"  "+ margini.exactCenterY() );
					//scatolaZoom.realZoomTo( 1, false );
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

	void spazio( int generazione, int peso ) {
		if( peso == 0 ) peso = 1;
		Space spaz = new Space( getContext() );
		LinearLayout.LayoutParams param = new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, peso );
		//param.weight = 1;
		LinearLayout gen = scatola.findViewById( generazione );
		gen.addView( spaz, param );
	}

	// Colloca una o DUE Person nel diagramma
	private View inserisci( int generazione, Person egli, View nodoSopra, boolean conDiscendenti ) {
		View nodo;
		if( !egli.getSpouseFamilies(gc).isEmpty() ) {
			Family fam = egli.getSpouseFamilies(gc).get(0);
			if( U.sesso(egli)==1 && !fam.getWives(gc).isEmpty() )	// Maschio ammogliato
				nodo = schedaDoppia( generazione, fam, egli, fam.getWives(gc).get(0), nodoSopra, null, 2, conDiscendenti, 0 );
			else if( U.sesso(egli)==2 && !fam.getHusbands(gc).isEmpty() )	// Femmina ammogliata
				nodo = schedaDoppia( generazione, fam, fam.getHusbands(gc).get(0), egli, null, nodoSopra, 1, conDiscendenti, 0 );
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
			//setBackgroundColor( 0x666633ff );
			addView( contenuto );
			List<Discendente> discendi = discendenti(egli);
			if( discendi.size() > 0 ) {
				LayoutParams paramContenuto = (LayoutParams) contenuto.getLayoutParams();
				paramContenuto.topMargin = 65;
				contenuto.setLayoutParams( paramContenuto );
				LinearLayout scatolaDiscendenti = new LinearLayout( getContext() );
				//scatolaDiscendenti.setOrientation( LinearLayout.HORIZONTAL ); è di default
				//scatolaDiscendenti.setLayoutParams( new LinearLayout.LayoutParams( LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT ) );
				//scatolaDiscendenti.setPadding( 0, 30, 0, 0 );
				//scatolaDiscendenti.setBackgroundColor( 0x99ff3333 );
				for( final Discendente disc : discendi ) {
					View vistaDiscend = getLayoutInflater().inflate( R.layout.diagramma_discendente, this, false );
					scatolaDiscendenti.addView( vistaDiscend );
					( (TextView) vistaDiscend.findViewById( R.id.num_discendenti ) ).setText( String.valueOf( disc.prole ) );
					if( disc.sesso == 1 )
						vistaDiscend.setBackgroundResource( R.drawable.casella_maschio );
					else if( disc.sesso == 2 )
						vistaDiscend.setBackgroundResource( R.drawable.casella_femmina );
					rete.add( new Corda( nodoSopra, null, vistaDiscend ) );
					vistaDiscend.setOnClickListener( new OnClickListener() {
						@Override
						public void onClick( View view ) {
							scatolona.removeAllViews();
							Globale.individuo = disc.id;
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
			rete.add( new Corda( nodoSopra, gen, scatolaSingola ) );
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
	// spaziatura: 0 coppia normale, 1 primo matrimonio lui, 2 ultimo matrimonio lei, 3 altri matrimoni lui, 4 altri matrimoni lei
	// configura: 0 coppia normale, 1 altri matrimoni lui, 2 altri matrimoni lei
	private View schedaDoppia( int generazione, final Family famiglia, Person lui0, Person lei0, View nodoSopraLui, // todo se tutto va bene elimina lui0 e lei0
	                               View nodoSopraLei, int conAntenati, boolean conDiscendenti, int configura ) {
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
		//scatolaCoppia.setBackgroundColor( 0x44ff9933 );
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
		//tratto.setBackgroundColor( 0x44ff9933 );
		if( configura == 1 )
			scatolaCoppia.addView( tratto );
		else if( lui != null ) {
			LinearLayout schedinaLui = new Schedina( generazione, lui, conAntenatiLui );
			scatolaCoppia.addView( schedinaLui );
			rete.add( new Corda( nodoSopraLui, gen, schedinaLui ) );
		}
		// Legame di matrimonio con eventuale anno
		/*final Family fam;
		if( lui != null ) fam = lui.getSpouseFamilies(gc).get(0);
		else fam = lei.getSpouseFamilies(gc).get(0);*/
		String dataMatrimonio = null;
		for( EventFact ef : famiglia.getEventsFacts() ) {
			if( ef.getTag().equals("MARR") )
				dataMatrimonio = ef.getDate();
		}
		FrameLayout legame = new FrameLayout( getContext() );
		//legame.setLayoutParams( new LinearLayout.LayoutParams( ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT ) );
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
			anno.setText( U.soloAnno(dataMatrimonio) );
			legame.addView( anno );
		}
		legame.setOnClickListener( new View.OnClickListener() {
			@Override
			public void onClick( View view ) {
				Intent intento = new Intent( getContext(), Famiglia.class );
				intento.putExtra( "idFamiglia", famiglia.getId() );
				startActivity( intento );
			}
		} );
		scatolaCoppia.addView( legame );
		// Lei
		if( configura == 2 )
			scatolaCoppia.addView( tratto );
		else if( lei != null ) {
			LinearLayout schedinaLei = new Schedina( generazione, lei, conAntenatiLei );
			scatolaCoppia.addView( schedinaLei );
			rete.add( new Corda( nodoSopraLei, gen, schedinaLei ) );
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
		//return scatolaCoppia;
		return legame;
	}

	// La schedina base del diagramma
	private class Schedina extends LinearLayout {	// implements OnClickListener
		String id;
    	Schedina( int generazione, final Person egli, boolean conAntenati ) {   //, boolean conDiscendenti
			super( Globale.contesto );	// necessario per estendere View
			id = egli.getId(); // per il menu contestuale
		    setLayoutParams( new LinearLayout.LayoutParams( LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT ) );
			setOrientation( LinearLayout.VERTICAL );
			//setBackgroundColor( 0x6666ff33 );
		    View vista = getLayoutInflater().inflate( R.layout.diagramma_pezzo, this, false );
		    ImageView sfondo = vista.findViewById( R.id.schedina_sfondo );
			if( conAntenati ) {
				Map<String,Integer> avi = antenati(egli);
				View avetti = getLayoutInflater().inflate(R.layout.diagramma_avi, this, false );
				//View connettore = avetti.findViewById( R.id.avi_connettore );
				TextView testoAvi = avetti.findViewById(R.id.num_avi);
				TextView testoAve = avetti.findViewById(R.id.num_ave);
				if( avi.get("avo") == null ) {
					testoAvi.setVisibility( View.GONE );
					RelativeLayout.LayoutParams param = (RelativeLayout.LayoutParams) testoAve.getLayoutParams();
					param.addRule( RelativeLayout.RIGHT_OF, 0 );
					param.addRule( RelativeLayout.CENTER_HORIZONTAL );
				} else
					testoAvi.setText( String.valueOf(avi.get("avo")) );
				if( avi.get("ava") == null ) {
					testoAve.setVisibility( View.GONE );
					RelativeLayout.LayoutParams param = (RelativeLayout.LayoutParams) testoAvi.getLayoutParams();
					param.addRule( RelativeLayout.LEFT_OF, 0 );
					param.addRule( RelativeLayout.CENTER_HORIZONTAL );
				} else
					testoAve.setText( String.valueOf(avi.get("ava")) );
				if( avi.get("avo")!=null || avi.get("ava")!=null ) {
					addView( avetti );
					// per allineare i due coniugi di cui questo con corona di avi in testa
					LayoutParams lp = new LayoutParams(	LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT );
					//lp.setMargins( 0, 0, 0, 30 );
					lp.bottomMargin = 28;
					setLayoutParams( lp ); // ok, gli avi sopra verrebbero clippati, ma possono fuoriuscire con setClipChildren(false)
					//setPadding( 0, 0, 0, 55 );
				}
				if( generazione > 1 ) { // i nonni non vengono alphizzati anche se hanno gli avi
					avetti.setAlpha( 0.7f );
					sfondo.setAlpha( 0.7f );
				}
			}
			// Foto sopra alla schedina
			/*if( !egli.getAllMedia(gc).isEmpty() ) {
				ImageView vistaFoto = new ImageView( getContext() );
				vistaFoto.setLayoutParams( new LinearLayout.LayoutParams( LinearLayout.LayoutParams.MATCH_PARENT, 100) );
				addView(vistaFoto);
				//U.mostraMedia( vistaFoto, egli.getAllMedia(gc).get(0) );
				U.unaFoto( egli, vistaFoto );
			}*/

			if( egli.getId().equals( Globale.individuo ) ) {
				//vista.setBackgroundColor( 0xffffbb33 );	//getContext().getColor( R.color.colorAccent );
				sfondo.setBackgroundResource( R.drawable.casella_evidente );
			} else if( U.sesso(egli) == 1 )
				sfondo.setBackgroundResource( R.drawable.casella_maschio );
			else if( U.sesso(egli) == 2 )
				sfondo.setBackgroundResource( R.drawable.casella_femmina );
			U.unaFoto( egli, (ImageView) vista.findViewById( R.id.schedina_foto ) );
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
			addView( vista );
			registerForContextMenu(this);
			setOnClickListener( new OnClickListener() {
				@Override
				public void onClick( View vista ) {
					//System.out.println( "OnClickListener: "+ ((TextView)v.findViewById(R.id.schedina_nome)).getText() +" "+ ((Schedina)v).egli.getId() );
					//Schedina questa = (Schedina)vista;
					if( egli.getId().equals(Globale.individuo) ) {
						//startActivity( new Intent( getActivity(), Individuo.class ));
						Intent intento = new Intent( getContext(), Individuo.class );
						intento.putExtra( "idIndividuo", egli.getId() );
						startActivity( intento );
					} else {
						scatolona.removeAllViews();
						Globale.individuo = egli.getId();
						disegna();
					}
				}
			});
			//setTag( egli.getId() );
		    if( egli.getId().equals( Globale.individuo ) && !conAntenati )
			    rete.add( new Corda( this, null, null ) );  // sfrutto rete per veicolare il centro del diagramma
		}

		/*@Override 	// non funziona
		public void onClick(View v) {
			System.out.println( "onClick NOME: "+ ((TextView)v.findViewById(R.id.schedina_nome)).getText());
		}*/
		/*@Override	// ok ma esclude il context menu
		public boolean onTouchEvent( final MotionEvent event ) {
			if( event.getAction() == MotionEvent.ACTION_UP ){
				System.out.println( "onTouchEvent NOME: "+ ((TextView)this.findViewById(R.id.schedina_nome)).getText());
				for( int g=1; g<6; g++ )
					((LinearLayout)scatola.findViewById( g )).removeAllViews();
				disegna( egli.getId() );
				//return performClick();
			}
			return true;
		}*/
	}

	// Un blocco ripetitivo per inserire i nonni
	private View nonni( Person genitore ) {
		View nodoNonni = null;
		if( !genitore.getParentFamilies(gc).isEmpty() ) {
			Family fam = genitore.getParentFamilies(gc).get(0);
			// Nonni
			if( !fam.getHusbands(gc).isEmpty() && !fam.getWives(gc).isEmpty() )	// ci sono entrambi i nonni
				nodoNonni = schedaDoppia( 1, fam, fam.getHusbands(gc).get(0), fam.getWives(gc).get(0), null, null, 3, false, 0 );
			else if( !fam.getHusbands(gc).isEmpty() )
				nodoNonni = schedaSingola( 1, fam.getHusbands(gc).get(0), null, true, false, 0 );
			else if( !fam.getWives(gc).isEmpty() )
				nodoNonni = schedaSingola( 1, fam.getWives(gc).get(0), null, true, false, 0 );
		}
		return nodoNonni;
	}

	// Un blocco ripetitivo per inserire zii e cugini
	/*@Deprecated
	private int ziiCugini( Person genitore, View nodoNonni, boolean contaZii ) {
		if( genitore != null )
		if( !genitore.getParentFamilies(gc).isEmpty() ) {
			Family fam = genitore.getParentFamilies(gc).get(0);
			// Zii
			List<Person> zii = fam.getChildren(gc);
			if( contaZii ) return zii.size();   // serve per posizionare i nonni
			zii.remove(genitore);
			for( Person zio : zii ) {
				View nodoZii = inserisci( 2, zio, nodoNonni, false );
				if( !zio.getSpouseFamilies(gc).isEmpty() ) {
					fam = zio.getSpouseFamilies(gc).get(0);
					for( Person cugino : fam.getChildren(gc) )
						inserisci( 3, cugino, nodoZii, true );
				}
			}
		}
		return 0;
	}*/

	// Il nuovo trend è di non mettere i cugini, solo gli zii
	private int zii( Person genitore, View nodoNonni, boolean contaZii ) {
		if( genitore != null )
			if( !genitore.getParentFamilies(gc).isEmpty() ) {
				Family fam = genitore.getParentFamilies(gc).get(0);
				// Zii
				List<Person> zii = fam.getChildren(gc);
				if( contaZii ) return zii.size();   // serve per posizionare i nonni
				zii.remove(genitore);
				for( Person zio : zii )
					inserisci( 2, zio, nodoNonni, true );
			}
		return 0;
	}

	// Fratellastri nati dai matrimoni precedenti o seguenti dei genitori
	private void altriMatrimoni( Person genitore, Family famiglia, View nodoGenitore ) {
		List<Family> altreFamiglie = genitore.getSpouseFamilies(gc);
		altreFamiglie.remove( famiglia );
		for( Family altraFamiglia : altreFamiglie ) {
			//if( U.sesso(genitore) == 1 )	// si potrebbe anche mostrare gli altri coniugi
			//altraFamiglia.getWives(gc).get(0);
			for( Person fratellastro : altraFamiglia.getChildren(gc) )
				inserisci( 3, fratellastro, nodoGenitore, true );
		}
	}

	/*void ioFigliNipotiX( Person io, View nodoGenitori ) {
		List<Family> matrimoni = io.getSpouseFamilies(gc);
		if ( matrimoni.isEmpty() ) // centro non si è sposato e non ha figli
			schedaSingola( 3, io, nodoGenitori, false, false );
		else
			for( int i = 0; i < matrimoni.size(); i++ ) {
				View nodoCentro = inserisci( 3, io, nodoGenitori, false );
				//View nodoCentro = schedaDoppia( 3, null, famig.getWives( gc ).get( 0 ), null, null, 2, false, 1 + distanza );
				for( Person figlio : matrimoni.get(i).getChildren(gc) ) {
					View nodoFiglio = inserisci( 4, figlio, nodoCentro, false );
					if( !figlio.getSpouseFamilies(gc).isEmpty() ) {
						Family fam = figlio.getSpouseFamilies( gc ).get( 0 );
						for( Person nipote : fam.getChildren( gc ) )
							inserisci( 5, nipote, nodoFiglio, true );
					}
				}
			}
	}*/

	// Il centro con i suoi matrimoni + figli e nipoti
	void ioFigliNipoti( Person io, View nodoGenitori ) {
		List<Family> matrimoni = io.getSpouseFamilies(gc);
		View nodoCentro;
		if( matrimoni.isEmpty() ) { // centro non si è sposato e non ha figli
			nodoCentro = schedaSingola( 3, io, nodoGenitori, false, false, 0 );
			//rete.add( new Corda( nodoCentro, null, null ) );  // sfrutto rete per veicolare il centro del diagramma
		} else {
			int configura = 0; // indica come mostrare la coppia (se ci sono matrimoni precedenti)
			for( int i = 0; i < matrimoni.size(); i++ ) { // i molteplici matrimoni del centro
				Family famig = matrimoni.get(i);
				if( U.sesso(io) == 1 && !famig.getWives(gc).isEmpty() ) { // maschio con moglie/i
					if( i > 0) configura = 1; // dal secondo matrimonio in poi
					nodoCentro = schedaDoppia( 3, famig, io, famig.getWives( gc ).get( 0 ), nodoGenitori, null, 2, false, configura );
				} else if( U.sesso(io) == 2 && !famig.getHusbands(gc).isEmpty() ) { // femmina con marito/i
					if( i+1 == matrimoni.size() ) configura = 0; // ultimo matrimonio
					else configura = 2; // matrimoni precedenti
					nodoCentro = schedaDoppia( 3, famig, famig.getHusbands( gc ).get( 0 ), io, null, nodoGenitori, 1, false, configura );
				} else { // centro neutro o senza coniuge
					if( i > 0) configura = 1;
					nodoCentro = schedaSingola( 3, io, nodoGenitori, false, false, configura);
				}
				//rete.add( new Corda( nodoCentro, null, null ) );  // il centro del diagramma
				for( Person figlio : famig.getChildren(gc) ) {
					View nodoFiglio = inserisci( 4, figlio, nodoCentro, false );
					if( !figlio.getSpouseFamilies(gc).isEmpty() ) {
						Family fam = figlio.getSpouseFamilies( gc ).get( 0 );
						for( Person nipote : fam.getChildren( gc ) )
							inserisci( 5, nipote, nodoFiglio, true );
					}
				}
			}
		}
	}

	// I fratelli del centro con i rispettivi figli
	void fratelloNipoti( Person fratello, View nodoGenitori ) {
		View nodoFratello = inserisci( 3, fratello, nodoGenitori, false );
		if( !fratello.getSpouseFamilies(gc).isEmpty() ) {
			Family famig = fratello.getSpouseFamilies(gc).get(0);
			for( Person nipote : famig.getChildren(gc) )
				inserisci(4, nipote, nodoFratello, true );
		}
	}

	// di una Person scrive e restituisce una coppietta di avi con il numero di antenati
	private Map<String,Integer> antenati( Person capo ) {
		Map<String,Integer> avi = new HashMap<>();
		if( !capo.getParentFamilies(gc).isEmpty() ) {
			Family fam = capo.getParentFamilies(gc).get(0);
			if( !fam.getHusbands(gc).isEmpty() ) {
				gente = 1;
				contaAntenati( fam.getHusbands(gc).get(0) );
				avi.put( "avo", gente);
			}
			if( !fam.getWives(gc).isEmpty() ) {
				gente = 1;
				contaAntenati( fam.getWives(gc).get(0) );
				avi.put( "ava", gente);
			}
		}
		//if( !avi.isEmpty() ) sl( "   " + avi );
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
		String id;
		int sesso;
		int prole;
		public Discendente( String id, int sesso, int prole ) {
			this.id = id;
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
				sfilza.add( new Discendente( nipote.getId(), U.sesso(nipote), gente ) );
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

	/*void disegnaLinea( View vistaStart, View vistaEnd ) {
		if( vistaStart != null && vistaEnd != null ) {
			// Coordinate assolute start
			Rect marginiStart = new Rect();
			vistaStart.getDrawingRect(marginiStart);
			scatolona.offsetDescendantRectToMyCoords(vistaStart, marginiStart);
			// e end
			Rect marginiEnd = new Rect();
			vistaEnd.getDrawingRect(marginiEnd);
			scatolona.offsetDescendantRectToMyCoords(vistaEnd, marginiEnd);
			View linea = new Linea((int) marginiStart.exactCenterX(), marginiStart.bottom,
					(int) marginiEnd.exactCenterX(), marginiEnd.top);
			RelativeLayout.LayoutParams paramLinea = new RelativeLayout.LayoutParams(9000, 3000);
			scatolona.addView(linea, paramLinea);
		}
	}*/
	void disegnaLinea( View vistaInizio, View vistaMezzo, View vistaFine ) {
		s.l( "disegnaLinea: ");
		if( vistaInizio != null ) s.l( "vistaInizio  "+ vistaInizio.getClass() + "\t" + vistaInizio.getScrollX()+ " " + vistaInizio.getScrollY()+ " " + vistaInizio.getWidth()+ " " + vistaInizio.getHeight() );
		if( vistaMezzo != null ) s.l( "vistaMezzo  "+ vistaMezzo.getClass() + "\t" + vistaMezzo.getScrollX()+ " " + vistaMezzo.getScrollY()+ " " + vistaMezzo.getWidth()+ " " + vistaMezzo.getHeight() );
		if( vistaFine != null ) s.l( "vistaFine  "+ vistaFine.getClass() + "\t" + vistaFine.getScrollX()+ " " + vistaFine.getScrollY()+ " " + vistaFine.getWidth()+ " " + vistaFine.getHeight() );
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
			} else
				mezzoY = (int) ( marginiInizio.bottom + ( marginiFine.top - marginiInizio.bottom ) / 1.3 );
			s.l( (int) marginiInizio.exactCenterX()+"-"+marginiInizio.bottom +"  "+ mezzoY +"  "+ (int) marginiFine.exactCenterX()+"-"+marginiFine.top );
			View linea = new Linea( (int) marginiInizio.exactCenterX(), marginiInizio.bottom,
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
			paint.setStrokeWidth(3);    // misura in pixel
			path.moveTo( xInizio, yInizio );
			/*float rapporto = Math.abs( (float)(yFine-yInizio) / (float)(xFine-xInizio) );	// altezza / base
			//System.out.println( (yFine-yInizio) +" / "+ (xFine-xInizio) +" = "+ rapporto );
			if( rapporto > 1 ) {    // linee strette
				rapporto *= 30;
				path.cubicTo(xInizio, yFine - rapporto, xFine, yInizio + rapporto, xFine, yFine);
			} else {    // linee larghe
				int mezzAlto = (yFine - yInizio) / 2;
				path.lineTo(xInizio, yInizio+mezzAlto);
				int direzione = ( xInizio < xFine )? 1 : -1;
				path.lineTo(xFine-mezzAlto*direzione, yInizio+mezzAlto);
				//path.lineTo(xFine, yFine);
				path.cubicTo( xFine-mezzAlto*direzione, yInizio+mezzAlto, xFine, yInizio+mezzAlto, xFine, yFine);
			}*/
			path.lineTo( xInizio, yMezzo );
			path.lineTo( xFine, yMezzo );
			path.lineTo( xFine, yFine );
			canvas.drawPath( path, paint );
			//s.l( "onDraw = " + xInizio +"-"+ yInizio +"  "+ yMezzo +"  "+ xFine +"-"+ yFine );
		}
	}

	// Menu contestuale
	String idPersona;
	View vistaScelta;
	@Override
	public void onCreateContextMenu( ContextMenu menu, View vista, ContextMenu.ContextMenuInfo info ) {
		vistaScelta = vista;
		idPersona = ((Schedina)vista).id;
		//idPersona = (String)vista.getTag();
		Person pers = gc.getPerson( idPersona );
		if( !idPersona.equals(Globale.individuo) )
			menu.add(0, 0, 0, R.string.card );
		if( !pers.getParentFamilies(gc).isEmpty() )
			menu.add(0, 1, 0, R.string.family_as_child );
		if( !pers.getSpouseFamilies(gc).isEmpty() )
			menu.add(0, 2, 0, R.string.family_as_spouse );
		menu.add(0, 3, 0, R.string.new_relative);
		//if( gc.getPeople().size() > 1 )
		if( U.ciSonoIndividuiCollegabili(gc.getPerson(idPersona)) )
			menu.add(0, 4, 0, R.string.link_person);
		menu.add(0, 5, 0, R.string.modify);
		if( !pers.getParentFamilies(gc).isEmpty() || !pers.getSpouseFamilies(gc).isEmpty() )
			menu.add(0, 6, 0, R.string.unlink);
		menu.add(0, 7, 0, R.string.delete);
	}
	@Override
	public boolean onContextItemSelected( MenuItem item ) {
		//AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();	// purtroppo qui è null
		//System.out.println( info.id +"  "+ info.position );
		//String idPersona = lungoCliccata.egli.getId();
		CharSequence[] parenti = { getText(R.string.parent), getText(R.string.sibling), getText(R.string.spouse), getText(R.string.child) };
		int id = item.getItemId();
		if( id == 0 ) {    // Apri scheda individuo
			//Globale.individuo = idPersona;
			//startActivity( new Intent( getActivity(), Individuo.class ));
			Intent intento = new Intent( getContext(), Individuo.class );
			intento.putExtra( "idIndividuo", idPersona );
			startActivity( intento );
		} else if( id == 1) {	// Famiglia come figlio
			Intent intento = new Intent( getContext(), Famiglia.class );
			intento.putExtra( "idFamiglia", gc.getPerson(idPersona).getParentFamilies(gc).get(0).getId() );
			startActivity( intento );
		} else if( id == 2 ) {	// Famiglia come coniuge
			Intent intento = new Intent( getContext(), Famiglia.class );
			intento.putExtra( "idFamiglia", gc.getPerson(idPersona).getSpouseFamilies(gc).get(0).getId() );
			startActivity( intento );
		} else if( id == 3 ) {	// Aggiungi parente
			AlertDialog.Builder builder = new AlertDialog.Builder( getActivity() );
			//builder.setTitle("Pick a color");
			builder.setItems( parenti, new DialogInterface.OnClickListener() {
				@Override
				public void onClick( DialogInterface dialog, int quale ) {
					Intent intento = new Intent( getContext(), EditaIndividuo.class );
					intento.putExtra( "idIndividuo", idPersona );
					intento.putExtra( "relazione", quale + 1 );
					startActivity( intento );
					/*U.editaIndividuo( getContext(), idPersona, quale+1, getTargetFragment() );*/
				}
			});
			builder.show();
		} else if( id == 4 ) {	// Collega persona
			AlertDialog.Builder builder = new AlertDialog.Builder( getActivity() );
			builder.setItems( parenti, new DialogInterface.OnClickListener() {
				@Override
				public void onClick( DialogInterface dialog, int quale ) {
					Intent intento = new Intent( getContext(), Principe.class );
					intento.putExtra( "anagrafeScegliParente", true );
					intento.putExtra( "relazione", quale + 1 );
					startActivityForResult( intento,1401 );
				}
			});
			builder.show();
		} else if( id == 5 ) {	// Modifica
			Intent intento = new Intent( getContext(), EditaIndividuo.class );
			intento.putExtra( "idIndividuo", idPersona );
			startActivity( intento );
		} else if( id == 6 ) {	// Scollega
			Anagrafe.scollega( idPersona );
			getActivity().recreate();
			Snackbar.make( getView(), R.string.person_unlinked, Snackbar.LENGTH_LONG ).show();
					//.setAction(R.string.undo, new ascoltatoreAnnulla() ).show();
			U.salvaJson();
		} else if( id == 7 ) {	// Elimina
			Anagrafe.elimina( idPersona, getContext(), vistaScelta );
		} else
			return false;
		return true;
	}

	/* Annulla nello snackbar
	public class ascoltatoreAnnulla implements View.OnClickListener {
		@Override
		public void onClick( View v ) {}
	}*/

	/*class dopoEliminaDiagramma implements Anagrafe.dopoEliminazione {
		public void esegui( String id ) {
			//System.out.println( "dopoEliminaDiagramma " + id );
			rinfresca();
		}
	}*/

	// Aggiunge il parente che è stata scelto in Anagrafe
	@Override
	public void onActivityResult( int requestCode, int resultCode, Intent data ) {
		s.l( "Diagramma onActivityResult " + requestCode );
		if( requestCode == 1401  ) {
			if( resultCode == AppCompatActivity.RESULT_OK ) {
				EditaIndividuo.aggiungiParente( idPersona,
						data.getStringExtra("idParente" ),
						data.getIntExtra("relazione", 0 ));
				U.salvaJson();
				Globale.editato = true; // Così Principe.onRestart aggiorna il diagramma
			}
		}
	}
}
