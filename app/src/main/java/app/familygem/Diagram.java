package app.familygem;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Bundle;
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
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import com.google.android.material.snackbar.Snackbar;
import com.otaliastudios.zoom.ZoomLayout;
import org.folg.gedcom.model.Family;
import org.folg.gedcom.model.Person;
import app.familygem.dettaglio.Famiglia;
import graph.gedcom.AncestryNode;
import graph.gedcom.Card;
import graph.gedcom.UnitNode;
import graph.gedcom.Graph;
import graph.gedcom.Line;
import graph.gedcom.Node;
import graph.gedcom.Util;

import static app.familygem.Globale.gc;

public class Diagram extends Fragment {

	private Graph graph;
	private ZoomLayout zoomBox;
	private RelativeLayout box;
	private View fulcrumCard;
	private float zoomValue = 1;

	@Override
	public void onCreate( Bundle state ) {
		super.onCreate( state );
	}

	@Override
	public View onCreateView( LayoutInflater inflater, ViewGroup container, Bundle state) {

		((AppCompatActivity) getActivity()).getSupportActionBar().setTitle( Globale.preferenze.alberoAperto().nome );
		final View view = inflater.inflate( R.layout.diagram, container, false );

		zoomBox = view.findViewById( R.id.diagram_zoom );
		box = view.findViewById( R.id.diagram_box );

		// Create a diagram model
		graph = new Graph( Globale.gc );
		graph.maxAncestors(2)
				.showFamily( getActivity().getIntent().getIntExtra("genitoriNum", 0) );
		getActivity().getIntent().putExtra( "genitoriNum", 0 ); // lo resetta per gli altri che hanno una sola parent family
		drawDiagram();
		return view;
	}

	private void drawDiagram() {

		// Empty diagram
		if( !graph.startFrom(Globale.individuo,Globale.preferenze.alberoAperto().radice,U.trovaRadice(gc)) ) {
			Button button = new Button(getContext());
			button.setText( R.string.new_person );
			box.addView( button, new RelativeLayout.LayoutParams(
					RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT ) );
			button.setOnClickListener( new View.OnClickListener() {
				@Override
				public void onClick( View view ) {
					Intent intento =  new Intent( getContext(), EditaIndividuo.class );
					intento.putExtra( "idIndividuo", "TIZIO_NUOVO" );
					startActivity( intento );
				}
			});
			return;
		}

		// Place graphic nodes in the box taking them from the list of nodes
		for( Node node : graph.getNodes() ) {
			if( node instanceof UnitNode )
				box.addView( new GraphicUnitNode(getContext(), (UnitNode)node) );
			if( node instanceof AncestryNode ) {
				box.addView( new GraphicAncestry(getContext(), (AncestryNode)node, false));
			}
		}

		box.post(new Runnable() {
			@Override
			public void run() {

				// Set dimensions of each graphic card
				for (int i = 0; i < box.getChildCount(); i++) {
					View graphicNode = box.getChildAt( i );
					if( graphicNode instanceof GraphicUnitNode) {
						for( int c = 0; c < ((GraphicUnitNode)graphicNode).getChildCount(); c++ ) {
							View card = ((GraphicUnitNode)graphicNode).getChildAt( c );
							if( card instanceof GraphicCardContainer) {
								GraphicCardContainer graphicCard = (GraphicCardContainer) card;
								graphicCard.card.width = card.getWidth();
								graphicCard.card.height = card.getHeight();
							}
						}
					} // And each Ancestry node
					else if( graphicNode instanceof GraphicAncestry) {
						GraphicAncestry graphicAncestry = (GraphicAncestry) graphicNode;
						graphicAncestry.node.width = graphicNode.getWidth();
						graphicAncestry.node.height = graphicNode.getHeight();
						if( graphicAncestry.node.isCouple() )
							graphicAncestry.node.horizontalCenter =
									graphicAncestry.findViewById( R.id.ancestry_father ).getWidth() +
									graphicAncestry.findViewById( R.id.ancestry_connector ).getWidth() / 2;
						else
							graphicAncestry.node.horizontalCenter = graphicNode.getWidth() / 2;
					}
				}

				// Let the graph calculate positions of Nodes and Lines
				graph.arrange();

				// Final position of the nodes
				for (int i = 0; i < box.getChildCount(); i++) {
					View node = box.getChildAt( i );
					if( node instanceof GraphicUnitNode) {
						GraphicUnitNode graphicUnitNode = (GraphicUnitNode) node;
						RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) graphicUnitNode.getLayoutParams();
						params.width = graphicUnitNode.node.width;
						params.leftMargin = graphicUnitNode.node.x;
						params.topMargin = graphicUnitNode.node.y;
						//graphicUnitNode.setLayoutParams(params);
						if( graphicUnitNode.node.isCouple() ) {
							Bond bond = new Bond(getContext(),graphicUnitNode.node);
							RelativeLayout.LayoutParams bondParams = new RelativeLayout.LayoutParams(
									RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT );
							bondParams.addRule( bond.youths ? RelativeLayout.ALIGN_PARENT_BOTTOM : RelativeLayout.CENTER_VERTICAL );
							bondParams.leftMargin = graphicUnitNode.node.husband.width;
							graphicUnitNode.addView( bond, bondParams );
						}
					} else if( node instanceof GraphicAncestry) {
						GraphicAncestry ancestry = (GraphicAncestry) node;
						RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) ancestry.getLayoutParams();
						params.leftMargin = ancestry.node.x;
						params.topMargin = ancestry.node.y;
						//ancestry.setLayoutParams(params);
					}
				}

				// Add the lines
				RelativeLayout.LayoutParams paramLines = new RelativeLayout.LayoutParams( box.getWidth(), box.getHeight() );
				box.addView( new Lines(getContext()), 0, paramLines );

				// Pan to diagram fulcrum
				zoomBox.post( new Runnable() {
					public void run() {
						if( fulcrumCard != null ) {
							zoomBox.realZoomTo(zoomValue, false); // Restore previous zoom
							Rect margini = new Rect();
							fulcrumCard.getDrawingRect( margini );
							box.offsetDescendantRectToMyCoords( fulcrumCard, margini );
							zoomBox.panTo( -margini.exactCenterX() + zoomBox.getWidth() / zoomBox.getRealZoom() / 2,
									-margini.exactCenterY() + zoomBox.getHeight() / zoomBox.getRealZoom() / 2, false );
						}
					}
				});
			}
		});
	}

	class GraphicUnitNode extends RelativeLayout {
		UnitNode node;
		GraphicUnitNode( final Context context, UnitNode node ) {
			super(context);
			this.node = node;
			setClipChildren( false );
			//setBackgroundColor( 0x3300FF00 );
			if( node.husband != null ) {
				LayoutParams paramsHusband = new LayoutParams( LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT );
				paramsHusband.addRule( CENTER_VERTICAL );
				addView( new GraphicCardContainer( context, node.husband ), paramsHusband );
			} if( node.wife != null ) {
				LayoutParams paramsWife = new LayoutParams( LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT );
				paramsWife.addRule( CENTER_VERTICAL );
				paramsWife.addRule( ALIGN_PARENT_RIGHT );
				addView( new GraphicCardContainer(context,node.wife), paramsWife );
			}
		}
	}

	// Container for card with ancestors on top
	class GraphicCardContainer extends RelativeLayout {
		Card card;
		GraphicCardContainer( Context context, Card card ) {
			super(context);
			this.card = card;
			GraphicCard graphicCard = new GraphicCard(context, card);
			graphicCard.setId( R.id.card );
			addView( graphicCard );
			setClipChildren( false );
			//setBackgroundColor( 0x66ff00ff );
			if( card.acquired && card.hasAncestry() ) {
				LayoutParams params = new LayoutParams( LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT );
				params.addRule( RelativeLayout.ABOVE, graphicCard.getId() );
				params.addRule( RelativeLayout.CENTER_HORIZONTAL );
				//params.bottomMargin = Util.TIC;
				addView( new GraphicAncestry(context, (AncestryNode)card.origin, true), params);
			}
		}
	}


	class GraphicCard extends LinearLayout {

		Card card;

		public GraphicCard( Context context, final Card card ) {
			super(context);
			this.card = card;
			Person person = card.getPerson();
			setLayoutParams( new LayoutParams( LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT ) );
			setOrientation( LinearLayout.VERTICAL );
			setGravity( Gravity.CENTER_HORIZONTAL );
			View view = getLayoutInflater().inflate( R.layout.diagram_card, this, true );

			ImageView background = view.findViewById( R.id.card_background );
			if( person.getId().equals( Globale.individuo ) ) {
				background.setBackgroundResource( R.drawable.casella_evidente );
				fulcrumCard = this;
			} else if( U.sesso(person) == 1 )
				background.setBackgroundResource( R.drawable.casella_maschio );
			else if( U.sesso(person) == 2 )
				background.setBackgroundResource( R.drawable.casella_femmina );
			if( card.acquired )
				background.setAlpha( 0.7f );

			U.unaFoto( Globale.gc, person, (ImageView) view.findViewById( R.id.card_photo ) );
			TextView vistaNome = view.findViewById(R.id.card_name);
			String nome = U.epiteto(person);
			if( nome.isEmpty() && view.findViewById(R.id.card_photo).getVisibility()==View.VISIBLE )
				vistaNome.setVisibility( View.GONE );
			else vistaNome.setText( nome );
			TextView vistaTitolo = view.findViewById(R.id.card_title);
			String titolo = U.titolo( person );
			if( titolo.isEmpty() ) vistaTitolo.setVisibility( View.GONE );
			else vistaTitolo.setText( titolo );
			TextView vistaDati = view.findViewById(R.id.card_data);
			String dati = U.dueAnni( person, true );
			if( dati.isEmpty() ) vistaDati.setVisibility(View.GONE);
			else vistaDati.setText( dati );
			if( !U.morto(person) )
				view.findViewById(R.id.card_mourn).setVisibility(View.GONE);
			//addView( view );
			registerForContextMenu(this);
			setOnClickListener( new OnClickListener() {
				@Override
				public void onClick( View v ) {
					Person person = card.getPerson();
					if( person.getId().equals(Globale.individuo) ) {
						Intent intent = new Intent( getContext(), Individuo.class );
						intent.putExtra( "idIndividuo", person.getId() );
						startActivity( intent );
					} else {
						clickCard(person);
				}
				}
			});
		}
	}

	class GraphicAncestry extends RelativeLayout {
		AncestryNode node;
		GraphicAncestry( Context context, final AncestryNode node, boolean acquired) {
			super(context);
			this.node = node;
			//setBackgroundColor( 0x440000FF );
			View view = getLayoutInflater().inflate(R.layout.diagram_ancestry,this, true);
			TextView testoAvi = view.findViewById( R.id.ancestry_father );
			TextView testoAve = view.findViewById( R.id.ancestry_mother );
			if( node.foreFather == null ) {
				testoAvi.setVisibility( View.GONE );
				RelativeLayout.LayoutParams param = (RelativeLayout.LayoutParams) testoAve.getLayoutParams();
				param.addRule( RelativeLayout.RIGHT_OF, 0 );
			} else {
				testoAvi.setText( String.valueOf(node.foreFather.ancestry) );
				testoAvi.setOnClickListener( new View.OnClickListener() {
					@Override
					public void onClick( View v ) {
						clickCard(node.foreFather.person);
					}
				});
			}
			if( node.foreMother == null ) {
				testoAve.setVisibility( View.GONE );
				RelativeLayout.LayoutParams param = (RelativeLayout.LayoutParams) findViewById( R.id.ancestry_connector ).getLayoutParams();
				param.addRule( RelativeLayout.RIGHT_OF, 0 );
			} else {
				testoAve.setText( String.valueOf(node.foreMother.ancestry) );
				testoAve.setOnClickListener( new View.OnClickListener() {
					@Override
					public void onClick( View v ) {
						clickCard(node.foreMother.person);
					}
				} );
			}
			if( acquired ) {
				setAlpha( 0.7f );
				View verticalLine = findViewById( R.id.ancestry_connector_vertical );
				LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) verticalLine.getLayoutParams();
				params.height = 50;
				verticalLine.setLayoutParams(params);
			}
		}
	}

	class Lines extends View {
		Paint paint = new Paint();
		Path path = new Path();
		public Lines( Context context ) {
			super( context );
		}
		@Override
		protected void onDraw( Canvas canvas) {
			paint.setStyle( Paint.Style.STROKE );
			paint.setColor( Color.WHITE );
			paint.setStrokeWidth(3);
			for(Line line : graph.getLines()) {
				path.moveTo( line.x1, line.y1 );
				//path.lineTo( line.x2, line.y2 );
				path.cubicTo( line.x1, line.y2, line.x2, line.y1, line.x2, line.y2 );
			}
			canvas.drawPath( path, paint );
		}
	}

	// Marriage with eventual year and vertical line
	class Bond extends FrameLayout {
		boolean youths;
		Bond( final Context context, UnitNode unitNode) {
			super(context);
			if (unitNode.guardGroup != null && !unitNode.guardGroup.getYouths().isEmpty()) {
				View verticaLine = new View( context );
				verticaLine.setBackgroundColor( 0xffffffff );
				LayoutParams lineParams = new LayoutParams( LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT );
				lineParams.width = 4;
				lineParams.height = unitNode.height / 2;
				lineParams.leftMargin = Util.MARGIN / 2 - 2;
				addView( verticaLine, lineParams );
				youths = true;
			}
			if( unitNode.marriageDate == null ) {
				View horizontaLine = new View( context );
				horizontaLine.setBackgroundColor( 0xffffffff );
				LayoutParams paramLine = new LayoutParams( LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT );
				paramLine.width = Util.MARGIN;
				paramLine.height = 4;
				addView( horizontaLine, paramLine );
			} else {
				TextView year = new TextView( context );
				year.setBackgroundResource( R.drawable.diagramma_cerchio_anno );
				year.setPadding(8,10,8,0);
				year.setText( new Datatore(unitNode.marriageDate).scriviAnno() );
				year.setTextSize( 12f );
				LayoutParams yearParams = new LayoutParams( LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT );
				yearParams.leftMargin = -15;
				addView( year, yearParams );
				s.l(">>>>>>>>  "+year.getWidth());
			}
			setOnClickListener( new View.OnClickListener() {
				@Override
				public void onClick( View view ) {
					//Memoria.setPrimo( famiglia ); // TODO famiglia?
					startActivity( new Intent( context, Famiglia.class ) );
				}
			});
		}
	}

	private void clickCard(Person person) {
		if( U.qualiGenitoriMostrare(getContext(), person, Principe.class) )
			return;
		zoomValue = zoomBox.getRealZoom();
		box.removeAllViews();
		Globale.individuo = person.getId();
		//graph.restartFrom(Globale.individuo);
		drawDiagram();
	}

	// Menu contestuale
	private String idPersona;
	private View vistaScelta;
	private Person pers;
	@Override
	public void onCreateContextMenu( @NonNull ContextMenu menu, @NonNull View vista, ContextMenu.ContextMenuInfo info ) {
		vistaScelta = vista;
		idPersona = ((GraphicCard)vista).card.getPerson().getId();
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
					if( EditaIndividuo.controllaMultiMatrimoni(intento,getContext(),Diagram.this) )
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
