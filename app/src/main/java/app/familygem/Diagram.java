package app.familygem;

import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import com.otaliastudios.zoom.ZoomLayout;

import org.folg.gedcom.model.EventFact;
import org.folg.gedcom.model.Person;

import app.familygem.dettaglio.Famiglia;
import graph.gedcom.AncestryNode;
import graph.gedcom.Card;
import graph.gedcom.CardNode;
import graph.gedcom.Graph;
import graph.gedcom.Line;
import graph.gedcom.Node;
import graph.gedcom.Util;

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
				.showFamily( getActivity().getIntent().getIntExtra("genitoriNum", 0) )
				.startFrom(Globale.individuo);
		getActivity().getIntent().putExtra( "genitoriNum", 0 ); // lo resetta per gli altri che hanno una sola parent family
		drawDiagram();
		return view;
	}

	private void drawDiagram() {

		// Place graphic nodes in the box taking them from the list of nodes
		for( Node node : graph.getNodes() ) {
			if( node instanceof CardNode )
				box.addView( new GraphicCardNode(getContext(), (CardNode)node) );
			if( node instanceof AncestryNode ) {
				box.addView( new GraphicAncestry(getContext(), (AncestryNode)node) );
			}
		}

		box.post(new Runnable() {
			@Override
			public void run() {

				// Set dimensions of each graphic card
				for (int i = 0; i < box.getChildCount(); i++) {
					View node = box.getChildAt( i );
					if( node instanceof GraphicCardNode) {
						for( int c = 0; c < ((GraphicCardNode)node).getChildCount(); c++ ) {
							View card = ((GraphicCardNode)node).getChildAt( c );
							if( card instanceof GraphicCardContainer) {
								GraphicCardContainer graphicCard = (GraphicCardContainer) card;
								graphicCard.card.width = graphicCard.getWidth();
								graphicCard.card.height = graphicCard.getHeight();
								s.l(graphicCard.card.width);
							}
						}
					} // And each Ancestry node
					else if( node instanceof GraphicAncestry) {
						GraphicAncestry ancestry = (GraphicAncestry) node;
						ancestry.node.width = ancestry.getWidth();
						ancestry.node.height = ancestry.getHeight();
					}
				}

				// Let the graph calculate positions of Nodes and Lines
				graph.arrange();

				// Final position of the nodes
				for (int i = 0; i < box.getChildCount(); i++) {
					View node = box.getChildAt( i );
					if( node instanceof GraphicCardNode) {
						GraphicCardNode graphicCardNode = (GraphicCardNode) node;
						RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) graphicCardNode.getLayoutParams();
						params.width = graphicCardNode.node.width;
						params.leftMargin = graphicCardNode.node.x;
						params.topMargin = graphicCardNode.node.y;
						graphicCardNode.setLayoutParams(params);
						if( graphicCardNode.node.isCouple() ) {
							//s.l( "> " + graphicCardNode.node.husband.width );
							View legame = graphicCardNode.findViewById( R.id.tag_legame );
							RelativeLayout.LayoutParams legameParams = (RelativeLayout.LayoutParams)legame.getLayoutParams();
							legameParams.addRule( RelativeLayout.CENTER_VERTICAL );
							legameParams.leftMargin = graphicCardNode.node.husband.width;
							legame.setLayoutParams( legameParams );
						}
					} else if( node instanceof GraphicAncestry) {
						GraphicAncestry ancestry = (GraphicAncestry) node;
						RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) ancestry.getLayoutParams();
						params.leftMargin = ancestry.node.x;
						params.topMargin = ancestry.node.y;
						ancestry.setLayoutParams(params);
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

	class GraphicCardNode extends RelativeLayout {
		CardNode node;
		GraphicCardNode( final Context context, CardNode node ) {
			super(context);
			this.node = node;
			setClipChildren( false );
			//setBackgroundColor( 0x3300FF00 );
			if( node.husband != null ) {
				LayoutParams paramsHusband = new LayoutParams( LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT );
				paramsHusband.addRule( CENTER_VERTICAL );
				this.addView( new GraphicCardContainer( context, node.husband ), paramsHusband );
			} if( node.wife != null ) {
				LayoutParams paramsWife = new LayoutParams( LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT );
				paramsWife.addRule( CENTER_VERTICAL );
				paramsWife.addRule( ALIGN_PARENT_RIGHT );
				this.addView( new GraphicCardContainer(context,node.wife), paramsWife );
			}

			// Legame di matrimonio con eventuale anno
			if( node.isCouple() ) {
				FrameLayout legame = new FrameLayout( getContext() );
				if( node.marriageDate == null ) {
					View line = new View( getContext() );
					LayoutParams paramLine = new LayoutParams( LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT );
					paramLine.width = Util.MARGIN;
					paramLine.height = 4;
					line.setBackgroundColor( 0xffffffff );
					legame.addView( line, paramLine );
				} else {
					TextView year = new TextView( context );
					year.setBackgroundResource( R.drawable.diagramma_cerchio_anno );
					year.setPadding(4,5,4,4);
					year.setText( new Datatore(node.marriageDate).scriviAnno() );
					legame.addView( year );
				}
				legame.setOnClickListener( new View.OnClickListener() {
					@Override
					public void onClick( View view ) {
						//Memoria.setPrimo( famiglia ); // TODO famiglia?
						startActivity( new Intent( context, Famiglia.class ) );
					}
				});
				legame.setId( R.id.tag_legame );
				addView( legame );
			}
		}
	}

	// Container for card crowned with ancestors
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
			if( card.ancestryNode != null && card.ancestryNode.foreFather != null && card.ancestryNode.foreMother != null ) {
				LayoutParams params = new LayoutParams( LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT );
				params.addRule( RelativeLayout.ABOVE, graphicCard.getId() );
				params.addRule( RelativeLayout.CENTER_HORIZONTAL );
				addView( new GraphicAncestry(context, card.ancestryNode), params);
			}
		}
	}


	class GraphicCard extends LinearLayout {

		//Card card;

		public GraphicCard( Context context, final Card card ) {
			super(context);
			//this.card = card;
			Person person = card.getPerson();
			//setId( R.id. );
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

	private void clickCard(Person person) {
		if( U.qualiGenitoriMostrare(getContext(), person, Principe.class) )
			return;
		zoomValue = zoomBox.getRealZoom();
		box.removeAllViews();
		Globale.individuo = person.getId();
		graph.restartFrom(Globale.individuo);
		drawDiagram();
	}

	class GraphicAncestry extends RelativeLayout {
		AncestryNode node;
		GraphicAncestry( Context context, final AncestryNode node) {
			super(context);
			this.node = node;
			//setBackgroundColor( 0x440000FF );
			View view = getLayoutInflater().inflate(R.layout.diagramma_avi,this, true);
			TextView testoAvi = view.findViewById( R.id.num_avi );
			TextView testoAve = view.findViewById( R.id.num_ave );
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
				RelativeLayout.LayoutParams param = (RelativeLayout.LayoutParams) findViewById( R.id.avi_connettore ).getLayoutParams();
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
			if( 0 > 1 ) {
				setAlpha( 0.7f );
				//sfondo.setAlpha( 0.7f );
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
}
