package app.familygem;

import android.content.Context;
import android.content.Intent;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import com.otaliastudios.zoom.ZoomLayout;
import org.folg.gedcom.model.Family;
import org.folg.gedcom.model.Person;
import java.util.ArrayList;
import java.util.List;
import app.familygem.dettaglio.Famiglia;
import graph.gedcom.AncestryNode;
import graph.gedcom.IndiCard;
import graph.gedcom.MiniCard;
import graph.gedcom.ProgenyNode;
import graph.gedcom.UnitNode;
import graph.gedcom.Graph;
import graph.gedcom.Line;
import graph.gedcom.Node;
import graph.gedcom.Util;
import static app.familygem.Globale.gc;

public class Diagram extends Fragment {

	Graph graph;
	private ZoomLayout zoomBox;
	private RelativeLayout box;
	private GraphicCard fulcrumView;
	private float zoomValue = 0.7f;
	private float density;
	private int STROKE;
	private int GLOW_SPACE = 35; // space to display glow, in dp
	private View popup;
	private boolean forceDraw;

	@Override
	public View onCreateView( LayoutInflater inflater, ViewGroup container, Bundle state) {
		density = getResources().getDisplayMetrics().density;
		STROKE = toPx(2);

		getActivity().findViewById(R.id.toolbar).setVisibility( View.GONE ); // Necessario in caso di backPressed dopo onActivityresult
		final View view = inflater.inflate( R.layout.diagram, container, false );
		view.findViewById( R.id.diagram_hamburger ).setOnClickListener( v -> {
			DrawerLayout scatolissima = getActivity().findViewById(R.id.scatolissima);
			scatolissima.openDrawer( GravityCompat.START );
		});
		view.findViewById( R.id.diagram_options ).setOnClickListener( v ->
				startActivity( new Intent(getContext(), DiagramSettings.class) )
		);

		zoomBox = view.findViewById( R.id.diagram_zoom );
		box = view.findViewById( R.id.diagram_box );
		graph = new Graph( Globale.gc ); // Create a diagram model
		forceDraw = true; // To be sure the diagram will be draw

		return view;
	}

	// Individua il fulcro da cui partire, mostra eventuale bottone 'Crea la prima persona' oppure avvia il diagramma
	@Override
	public void onStart() {
		super.onStart();
		// Ragioni per cui bisogna proseguire, in particolare cose che sono cambiate
		if( forceDraw || (fulcrumView != null && !fulcrumView.card.person.getId().equals(Globale.individuo))
				|| Globale.editato || (graph != null && graph.whichFamily != Globale.numFamiglia) ) {
			forceDraw = false;
			Globale.editato = false;

			String[] ids = { Globale.individuo, Globale.preferenze.alberoAperto().radice, U.trovaRadice(gc) };
			Person fulcrum = null;
			for( String id : ids ) {
				fulcrum = gc.getPerson(id);
				if (fulcrum != null)
					break;
			}
			// Empty diagram
			if( fulcrum == null ) {
				View button = LayoutInflater.from(getContext()).inflate(R.layout.diagram_button, box, true);
				button.findViewById( R.id.diagram_new ).setOnClickListener( view -> {
					Intent intento =  new Intent( getContext(), EditaIndividuo.class );
					intento.putExtra( "idIndividuo", "TIZIO_NUOVO" );
					startActivity( intento );
				});
				// se eventualmente era stato cambiato lo zoom
				zoomBox.post( () -> zoomBox.realZoomTo(0.7f, true) );
				if( !Globale.preferenze.esperto )
					((View)zoomBox.getParent()).findViewById( R.id.diagram_options ).setVisibility( View.GONE );
			} else {
				Globale.individuo = fulcrum.getId(); // Casomai lo ribadisce
				graph.maxAncestors( Globale.preferenze.diagram.ancestors )
						.maxUncles( Globale.preferenze.diagram.uncles )
						.displaySiblings( Globale.preferenze.diagram.siblings )
						.maxDescendants( Globale.preferenze.diagram.descendants )
						.showFamily( Globale.numFamiglia )
						.startFrom( fulcrum );
				drawDiagram();
			}
		}
	}

	void drawDiagram() {
		box.removeAllViews();

		// Place graphic nodes in the box taking them from the list of nodes
		for(Node node : graph.getNodes()) {
			if(node instanceof UnitNode)
				box.addView(new GraphicUnitNode(getContext(), (UnitNode) node));
			else if( node instanceof AncestryNode )
				box.addView(new GraphicAncestry(getContext(), (AncestryNode)node));
			else if( node instanceof ProgenyNode )
				box.addView(new GraphicProgeny(getContext(), (ProgenyNode) node));
		}

		// Only one person in the diagram
		if( gc.getPeople().size() == 1 && gc.getFamilies().size() == 0 ) {

			// Add the suggestion balloon
			View popupView = LayoutInflater.from(getContext()).inflate(R.layout.popup, box);
			ConstraintLayout popupLayout = popupView.findViewById( R.id.popup_layout );
			View singleNode = box.getChildAt(0);
			box.removeView(singleNode);
			ConstraintLayout.LayoutParams nodeParams = new ConstraintLayout.LayoutParams(
					ConstraintLayout.LayoutParams.WRAP_CONTENT, ConstraintLayout.LayoutParams.WRAP_CONTENT);
			nodeParams.topToBottom = R.id.popup_fumetto;
			nodeParams.topMargin = toPx(6);
			nodeParams.startToStart = ConstraintLayout.LayoutParams.PARENT_ID;
			nodeParams.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID;
			singleNode.setId( R.id.tag_fulcrum );
			popupLayout.addView(singleNode, nodeParams);
			popup = popupView.findViewById( R.id.popup_fumetto );
			popup.setOnClickListener( vista -> {
				vista.setVisibility(View.INVISIBLE);
			});

			// Add the glow to the fulcrum card
			if( fulcrumView != null ) {
				box.post( () -> {
					ConstraintLayout.LayoutParams glowParams = new ConstraintLayout.LayoutParams(
							singleNode.getWidth() + toPx(GLOW_SPACE*2), singleNode.getHeight() + toPx(GLOW_SPACE*2) );
					glowParams.topToTop = R.id.tag_fulcrum;
					glowParams.bottomToBottom = R.id.tag_fulcrum;
					glowParams.startToStart = ConstraintLayout.LayoutParams.PARENT_ID;
					glowParams.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID;
					fulcrumView.card.width = toDp(singleNode.getWidth());
					fulcrumView.card.height = toDp(singleNode.getHeight());
					popupLayout.addView( new FulcrumGlow(getContext()), 0, glowParams );
					zoomBox.realZoomTo(1, false); // only to center the box
					zoomBox.post( () -> {
						zoomBox.realZoomTo(1.4f, true);
					});
				});
			}

		} else { // Two or more persons in the diagram

			box.postDelayed( () -> {
				// Get the dimensions of various nodes converting from pixel to dip
				for (int i = 0; i < box.getChildCount(); i++) {
					View nodeView = box.getChildAt( i );
					if( nodeView instanceof GraphicUnitNode) {
						// Get the bond width
						GraphicUnitNode graphicUnitNode = (GraphicUnitNode) nodeView;
						Bond bond = nodeView.findViewById( R.id.tag_bond );
						if( bond != null )
							graphicUnitNode.unitNode.bondWidth = toDp(bond.getWidth());
						// Get dimensions of each graphic card
						for( int c = 0; c < graphicUnitNode.getChildCount(); c++ ) {
							View cardView = graphicUnitNode.getChildAt( c );
							if( cardView instanceof GraphicCard ) {
								GraphicCard graphicCard = (GraphicCard) cardView;
								graphicCard.card.width = toDp(cardView.getWidth()) ;
								graphicCard.card.height = toDp(cardView.getHeight());
							}
						}
					} // Get dimensions of each ancestry node
					else if( nodeView instanceof GraphicAncestry) {
						GraphicAncestry graphicAncestry = (GraphicAncestry) nodeView;
						graphicAncestry.node.width = toDp(nodeView.getWidth());
						graphicAncestry.node.height = toDp(nodeView.getHeight());
						if( graphicAncestry.node.isCouple() ) {
							graphicAncestry.node.horizontalCenter = toDp(
									graphicAncestry.findViewById( R.id.ancestry_father ).getWidth() +
											graphicAncestry.findViewById( R.id.ancestry_connector ).getWidth() / 2);
						} else
							graphicAncestry.node.horizontalCenter = toDp(nodeView.getWidth() / 2);
					} // Get the dimensions of each progeny node
					else if( nodeView instanceof GraphicProgeny ) {
						GraphicProgeny graphicProgeny = (GraphicProgeny) nodeView;
						ProgenyNode progeny = graphicProgeny.progenyNode;
						progeny.width = toDp(graphicProgeny.getWidth());
						for(int p=0; p <graphicProgeny.getChildCount(); p++) {
							View miniCard = graphicProgeny.getChildAt(p);
							progeny.miniChildren.get(p).width = toDp(miniCard.getWidth());
						}
					}
				}

				// Let the graph calculate positions of Nodes and Lines
				graph.arrange();

				// Final position of the nodes from dips to pixels
				for (int i = 0; i < box.getChildCount(); i++) {
					View nodeView = box.getChildAt( i );
					if( nodeView instanceof GraphicUnitNode) {
						GraphicUnitNode graphicUnitNode = (GraphicUnitNode) nodeView;
						RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) graphicUnitNode.getLayoutParams();
						params.leftMargin = toPx(graphicUnitNode.unitNode.x);
						params.topMargin = toPx(graphicUnitNode.unitNode.y);
						graphicUnitNode.setLayoutParams( params );
						// Bond height
						if( graphicUnitNode.unitNode.isCouple() ) {
							Bond bond = graphicUnitNode.findViewById( R.id.tag_bond  );
							RelativeLayout.LayoutParams bondParams = (RelativeLayout.LayoutParams) bond.getLayoutParams();
							bondParams.height = toPx(graphicUnitNode.unitNode.height);
							bond.setLayoutParams( bondParams );
						}
					} else if( nodeView instanceof GraphicAncestry ) {
						AncestryNode ancestry = ((GraphicAncestry) nodeView).node;
						RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) nodeView.getLayoutParams();
						params.leftMargin = toPx(ancestry.x);
						params.topMargin = toPx(ancestry.y);
						nodeView.setLayoutParams(params);
					} else if( nodeView instanceof GraphicProgeny ) {
						ProgenyNode progeny = ((GraphicProgeny) nodeView).progenyNode;
						RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) nodeView.getLayoutParams();
						params.leftMargin = toPx(progeny.x);
						params.topMargin = toPx(progeny.y);
						nodeView.setLayoutParams( params );
					}
				}

				// Add the lines
				RelativeLayout.LayoutParams paramLines = new RelativeLayout.LayoutParams( toPx(graph.width), toPx(graph.height) );
				box.addView( new Lines(getContext()), 0, paramLines );

				// Pan to diagram fulcrum
				if( fulcrumView != null ) {
					zoomBox.post( () -> {
						IndiCard fulCard = graph.getFulcrum();
						int padding = box.getPaddingTop(); // box padding (50dp in px)
						zoomBox.realZoomTo(zoomValue, false); // Restore previous zoom
						zoomBox.panTo( -toPx(fulCard.centerX()) + zoomBox.getWidth()/zoomBox.getRealZoom()/2 - padding,
								-toPx(fulCard.centerY()) + zoomBox.getHeight()/zoomBox.getRealZoom()/2 - padding, false);
						// Add the glow
						RelativeLayout.LayoutParams glowParams = new RelativeLayout.LayoutParams(
								toPx(fulCard.width+GLOW_SPACE*2), toPx(fulCard.height+GLOW_SPACE*2) );
						glowParams.setMargins(toPx(fulCard.x-GLOW_SPACE), toPx(fulCard.y-GLOW_SPACE), -toPx(GLOW_SPACE), -toPx(GLOW_SPACE));
						box.addView( new FulcrumGlow(getContext()), 0, glowParams );
					});
				}
			}, 100);
		}
	}

	// Glow around fulcrum card
	class FulcrumGlow extends View {
		Paint paint = new Paint( Paint.ANTI_ALIAS_FLAG );
		BlurMaskFilter bmf = new BlurMaskFilter(toPx(25), BlurMaskFilter.Blur.NORMAL);
		int extend = 5; // draw a rectangle a little bigger
		public FulcrumGlow( Context context ) {
			super(context);
			//setBackgroundColor( 0x330099FF );
		}
		@Override
		protected void onDraw( Canvas canvas) {
			paint.setColor( getResources().getColor(R.color.evidenzia) );
			paint.setMaskFilter(bmf);
			setLayerType(View.LAYER_TYPE_SOFTWARE, paint);
			IndiCard fulcrum = graph.getFulcrum();
			canvas.drawRect( toPx(GLOW_SPACE-extend), toPx(GLOW_SPACE-extend),
					toPx(fulcrum.width+GLOW_SPACE+extend), toPx(fulcrum.height+GLOW_SPACE+extend), paint );
		}
	}

	// Node with one person or couple + marriage
	class GraphicUnitNode extends RelativeLayout {
		UnitNode unitNode;
		GraphicUnitNode( Context context, UnitNode unitNode ) {
			super(context);
			this.unitNode = unitNode;
			setClipChildren( false );
			//setBackgroundColor( 0xff00FF00 );
			if( unitNode.husband != null )
				addView( new GraphicCard(context, unitNode.husband, true) );
			if( unitNode.wife != null )
				addView( new GraphicCard(context, unitNode.wife, false) );
			if( unitNode.isCouple() ) {
				Bond bond = new Bond(context, unitNode);
				LayoutParams bondParams = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
				bondParams.addRule( unitNode.hasChildren() ? ALIGN_PARENT_BOTTOM : CENTER_VERTICAL);
				bondParams.addRule( RIGHT_OF, R.id.tag_husband );
				if( unitNode.marriageDate != null ) {
					bondParams.leftMargin = toPx(-Util.TIC);
					bondParams.rightMargin = toPx(-Util.TIC);
				}
				addView( bond, bondParams );
			}
		}
	}

	// Card of a person
	class GraphicCard extends LinearLayout {
		IndiCard card;
		public GraphicCard( Context context, final IndiCard card, boolean husband ) {
			super(context);
			this.card = card;
			Person person = card.person;
			setOrientation( LinearLayout.VERTICAL );
			setGravity( Gravity.CENTER_HORIZONTAL );
			RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams( LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT );
			params.addRule( RelativeLayout.CENTER_VERTICAL );
			if (husband)
				setId( R.id.tag_husband );
			else
				params.addRule( RelativeLayout.RIGHT_OF, R.id.tag_bond );
			setLayoutParams( params );
			if (card.asterisk) {
				addView( new Asterisk( context, card ) );
			} else {
				View view = getLayoutInflater().inflate( R.layout.diagram_card, this, true );

				ImageView background = view.findViewById( R.id.card_background );
				ImageView lutto = view.findViewById(R.id.card_mourn);
				if( person.getId().equals( Globale.individuo ) ) {
					if( U.sesso(person) == 1 )
						background.setBackgroundResource( R.drawable.casella_maschio_evidente );
					else if( U.sesso(person) == 2 )
						background.setBackgroundResource( R.drawable.casella_femmina_evidente );
					else
						background.setBackgroundResource( R.drawable.casella_neutro_evidente );
					fulcrumView = this;
				} else if( U.sesso(person) == 1 )
					background.setBackgroundResource( R.drawable.casella_maschio );
				else if( U.sesso(person) == 2 )
					background.setBackgroundResource( R.drawable.casella_femmina );
				if( card.acquired && !person.getId().equals(Globale.individuo) )
					background.setAlpha( 0.7f );

				U.unaFoto( Globale.gc, person, view.findViewById( R.id.card_photo ) );
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
					lutto.setVisibility(View.GONE);
				registerForContextMenu(this);
				setOnClickListener( v -> {
					Person person1 = card.person;
					if( person1.getId().equals(Globale.individuo) ) {
						Memoria.setPrimo( person1 );
						startActivity( new Intent(getContext(), Individuo.class) );
					} else {
						clickCard( person1 );
					}
				});
			}
		}
	}

	// Replacement for person with multiple marriages
	class Asterisk extends LinearLayout {
		IndiCard card;
		Asterisk( Context context, final IndiCard card ) {
			super(context);
			this.card = card;
			getLayoutInflater().inflate( R.layout.diagram_asterisk, this, true );
			registerForContextMenu(this);
			setOnClickListener( v -> {
				Memoria.setPrimo( card.person );
				startActivity(new Intent(getContext(), Individuo.class));
			});
		}
	}

	// Marriage with eventual year and vertical line
	class Bond extends LinearLayout {
		Bond( final Context context, final UnitNode unitNode) {
			super(context);
			setOrientation( VERTICAL );
			View spacer = new View( context );
			LayoutParams spacerParams = new LayoutParams( LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT );
			spacerParams.weight = 0.5f;
			addView( spacer, spacerParams );
			if( unitNode.marriageDate == null ) {
				View horizontaLine = new View( context );
				horizontaLine.setBackgroundColor( 0xffffffff );
				addView( horizontaLine, new LayoutParams( toPx(Util.MARGIN), STROKE ) );
			} else {
				TextView year = new TextView( context );
				year.setBackgroundResource( R.drawable.diagramma_cerchio_anno );
				year.setPadding(toPx(5),toPx(5),toPx(5),0);
				year.setText( new Datatore(unitNode.marriageDate).scriviAnno() );
				year.setTextSize( 13f );
				addView( year, LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT );
			}
			View verticaLine = new View( context );
			LayoutParams lineParams = new LayoutParams( STROKE, LayoutParams.MATCH_PARENT );
			lineParams.weight = 0.5f;
			lineParams.gravity = Gravity.CENTER_HORIZONTAL;
			addView( verticaLine, lineParams );
			if (unitNode.hasChildren()) {
				verticaLine.setBackgroundColor( 0xffffffff );
			}
			setId( R.id.tag_bond );
			setOnClickListener( view -> {
				Memoria.setPrimo( unitNode.family );
				startActivity( new Intent( context, Famiglia.class ) );
			});
		}
	}

	class GraphicAncestry extends RelativeLayout {
		AncestryNode node;
		GraphicAncestry( Context context, final AncestryNode node) {
			super(context);
			this.node = node;
			//setBackgroundColor( 0x440000FF );
			View view = getLayoutInflater().inflate(R.layout.diagram_ancestry,this, true);
			TextView first = view.findViewById( R.id.ancestry_father );
			TextView second = view.findViewById( R.id.ancestry_mother );
			if( node.miniFather == null ) {
				first.setVisibility( View.GONE );
				RelativeLayout.LayoutParams param = (RelativeLayout.LayoutParams) second.getLayoutParams();
				param.addRule( RelativeLayout.RIGHT_OF, 0 );
			} else {
				first.setText( String.valueOf(node.miniFather.amount) );
				if( U.sesso(node.miniFather.person) == 1 )
					first.setBackgroundResource( R.drawable.casella_maschio );
				else if( U.sesso(node.miniFather.person) == 2 )
					first.setBackgroundResource( R.drawable.casella_femmina );
				first.setOnClickListener( v -> clickCard(node.miniFather.person) );
			}
			if( node.miniMother == null ) {
				second.setVisibility( View.GONE );
				RelativeLayout.LayoutParams param = (RelativeLayout.LayoutParams) findViewById( R.id.ancestry_connector ).getLayoutParams();
				param.addRule( RelativeLayout.RIGHT_OF, 0 );
			} else {
				second.setText( String.valueOf(node.miniMother.amount) );
				if( U.sesso(node.miniMother.person) == 1 )
					second.setBackgroundResource( R.drawable.casella_maschio );
				else if( U.sesso(node.miniMother.person) == 2 )
					second.setBackgroundResource( R.drawable.casella_femmina );
				second.setOnClickListener( v -> clickCard(node.miniMother.person) );
			}
			if( node.miniFather == null && node.miniMother == null )
				this.setVisibility( INVISIBLE );
			if( node.acquired ) {
				setAlpha( 0.7f );
				View verticalLine = findViewById( R.id.ancestry_connector_vertical );
				LinearLayout.LayoutParams lineParams = (LinearLayout.LayoutParams) verticalLine.getLayoutParams();
				lineParams.height = toPx(25);
				verticalLine.setLayoutParams(lineParams);
			}
		}
	}

	// List of little descendants cards
	class GraphicProgeny extends LinearLayout {
		ProgenyNode progenyNode;
		GraphicProgeny( final Context context, ProgenyNode progenyNode) {
			super( context );
			this.progenyNode = progenyNode;
			LayoutParams params = new LayoutParams( LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT );
			params.topMargin = toPx(Util.GAP);
			params.gravity = Gravity.CENTER_HORIZONTAL;
			setLayoutParams( params );
			setClipChildren( false );
			//setBackgroundColor( 0x330000FF );
			for( int i = 0; i < progenyNode.miniChildren.size(); i++ ) {
				final MiniCard miniChild = progenyNode.miniChildren.get(i);
				View graphicMiniCard = getLayoutInflater().inflate( R.layout.diagram_progeny, this, false );
				((TextView)graphicMiniCard.findViewById( R.id.progeny_number ) ).setText( String.valueOf( miniChild.amount ) );
				int sex = U.sesso( miniChild.person );
				int background = R.drawable.casella_neutro;
				if( sex == 1 )
					background = R.drawable.casella_maschio;
				else if( sex == 2 )
					background = R.drawable.casella_femmina;
				graphicMiniCard.setBackgroundResource( background );
				if( i < progenyNode.miniChildren.size() - 1 ) {
					LayoutParams cardParams = (LayoutParams) graphicMiniCard.getLayoutParams();
					cardParams.rightMargin = toPx(Util.PLAY);
					graphicMiniCard.setLayoutParams( cardParams );
				}
				graphicMiniCard.setOnClickListener( view -> clickCard( miniChild.person ) );
				addView( graphicMiniCard );
			}
		}
	}

	// Generate the view of lines connecting the cards
	class Lines extends View {
		Paint paint = new Paint( Paint.ANTI_ALIAS_FLAG );
		List<Path> paths = new ArrayList<>(); // Each path contains many lines
		public Lines( Context context ) {
			super(context);
			paths.add( new Path() );
			paint.setStyle( Paint.Style.STROKE );
			paint.setStrokeWidth( STROKE );
			paint.setColor( Color.WHITE );
		}
		@Override
		protected void onDraw( Canvas canvas) {
			float restartX = 0; // (re)starting point of every path
			int maxBitmapWidth = canvas.getMaximumBitmapWidth() // is 16384 on emulators, 4096 on my physical devices
					- STROKE * 3; // the space actually occupied by the line is a little bit larger
			int pathNum = 0; // index of paths
			// Put the lines in one or more paths
			for(Line line : graph.getLines()) {
				float x1 = toPx(line.x1), y1 = toPx(line.y1), x2 = toPx(line.x2), y2 = toPx(line.y2);
				float lineWidth = Math.max(x1,x2) - Math.min(x1,x2);
				if( lineWidth <= maxBitmapWidth ) {
					float pathRight = Math.max(x1,x2) - restartX;
					// Start another path
					if( pathRight > maxBitmapWidth ) {
						pathNum++;
						restartX = Math.min(x1,x2);
						paths.add( new Path() );
					}
					paths.get(pathNum).moveTo( x1, y1 );
					paths.get(pathNum).cubicTo( x1, y2, x2, y1, x2, y2 );
				}
			}
			// Draw the paths
			for( Path path : paths ) {
				canvas.drawPath( path, paint );
			}
		}
	}

	private void clickCard(Person person) {
		selectParentFamily( person );
	}

	// Ask which family to display in the diagram if fulcrum has many parent families
	private void selectParentFamily( Person fulcrum ) { //, Family excluded
		List<Family> families = fulcrum.getParentFamilies(gc);
		//families.remove( excluded ); // No perché cambia la sequenza delle famiglie
		// todo Si potrebbe disabilitare la famiglia 'excluded', ma occorre un adapter
		if( families.size() > 1 ) {
			new AlertDialog.Builder(getContext()).setTitle( R.string.which_family )
					.setItems( U.elencoFamiglie(families), (dialog, which) -> {
						completeSelect( fulcrum, which );
					}).show();
		} else {
			completeSelect( fulcrum, 0 );
		}
	}
	// Complete above function
	private void completeSelect( Person fulcrum, int whichFamily ) {
		zoomValue = zoomBox.getRealZoom();
		Globale.individuo = fulcrum.getId();
		Globale.numFamiglia = whichFamily;
		graph.showFamily( Globale.numFamiglia );
		graph.startFrom( fulcrum );
		drawDiagram();
	}

	private float toDp(float pixels) {
		return pixels / density;
	}

	private int toPx(float dips) {
		return (int) (dips * density + 0.5f);
	}

	// Generate the 2 family (as child and as spouse) labels for contextual menu
	static String[] getFamilyLabels(Context c, Person person) {
		String[] labels = { null, null };
		List<Family> parentFams = person.getParentFamilies(gc);
		List<Family> spouseFams = person.getSpouseFamilies(gc);
		if( parentFams.size() > 0 )
			labels[0] = spouseFams.isEmpty() ? c.getString(R.string.family) : c.getString(R.string.family_as_child);
		if( spouseFams.size() > 0 )
			labels[1] = parentFams.isEmpty() ? c.getString(R.string.family) : c.getString(R.string.family_as_spouse);
		return labels;
	}

	private Person pers;
	private String idPersona;
	private Family parentFam; // Displayed family in which the person is child
	private Family spouseFam; // Selected family in which the person is spouse
	@Override
	public void onCreateContextMenu( @NonNull ContextMenu menu, @NonNull View vista, ContextMenu.ContextMenuInfo info ) {
		if( vista instanceof GraphicCard ) {
			pers = ((GraphicCard)vista).card.person;
			parentFam = ((GraphicCard)vista).card.parentFamily;
			spouseFam = ((GraphicUnitNode)vista.getParent()).unitNode.family;
		} else if( vista instanceof Asterisk ) {
			pers = ((Asterisk)vista).card.person;
			parentFam = ((Asterisk)vista).card.parentFamily;
			spouseFam = ((GraphicUnitNode)vista.getParent().getParent()).unitNode.family;
		}
		idPersona = pers.getId();
		String[] familyLabels = getFamilyLabels( getContext(), pers );

		if( idPersona.equals(Globale.individuo) && pers.getParentFamilies(gc).size() > 1 )
			menu.add(0, -1, 0, R.string.diagram );
		if( !idPersona.equals(Globale.individuo) )
			menu.add(0, 0, 0, R.string.card );
		if( familyLabels[0] != null )
			menu.add(0, 1, 0, familyLabels[0] );
		if( familyLabels[1] != null )
			menu.add(0, 2, 0, familyLabels[1] );
		menu.add(0, 3, 0, R.string.new_relative);
		if( U.ciSonoIndividuiCollegabili(pers) )
			menu.add(0, 4, 0, R.string.link_person);
		menu.add(0, 5, 0, R.string.modify);
		if( !pers.getParentFamilies(gc).isEmpty() || !pers.getSpouseFamilies(gc).isEmpty() )
			menu.add(0, 6, 0, R.string.unlink);
		menu.add(0, 7, 0, R.string.delete);
		if( popup != null )
			popup.setVisibility(View.INVISIBLE);
	}

	@Override
	public boolean onContextItemSelected( MenuItem item ) {
		CharSequence[] parenti = { getText(R.string.parent), getText(R.string.sibling), getText(R.string.spouse), getText(R.string.child) };
		int id = item.getItemId();
		if( id == -1 ) { // Diagramma per fulcro figlio in più famiglie
			if( pers.getParentFamilies(gc).size() > 2 ) // Più di due famiglie
				selectParentFamily( pers );
			else // Due famiglie
				completeSelect( pers, Globale.numFamiglia == 0 ? 1 : 0 );
		} else if( id == 0 ) { // Apri scheda individuo
			Memoria.setPrimo( pers );
			startActivity( new Intent(getContext(), Individuo.class) );
		} else if( id == 1 ) { // Famiglia come figlio
			if( idPersona.equals(Globale.individuo) ) { // Se è fulcro apre direttamente la famiglia
				Memoria.setPrimo( parentFam );
				startActivity( new Intent( getContext(), Famiglia.class ) );
			} else
				U.qualiGenitoriMostrare( getContext(), pers, 2 );
		} else if( id == 2 ) { // Famiglia come coniuge
			U.qualiConiugiMostrare( getContext(), pers,
					idPersona.equals(Globale.individuo) ? spouseFam : null ); // Se è fulcro apre direttamente la famiglia
		} else if( id == 3 ) { // Collega persona nuova
			if( Globale.preferenze.esperto ) {
				DialogFragment dialog = new NuovoParente(pers, parentFam, spouseFam, true, null);
				dialog.show(getActivity().getSupportFragmentManager(), "scegli");
			} else {
				new AlertDialog.Builder( getContext() ).setItems( parenti, ( dialog, quale ) -> {
					Intent intento = new Intent( getContext(), EditaIndividuo.class );
					intento.putExtra( "idIndividuo", idPersona );
					intento.putExtra( "relazione", quale + 1 );
					if( U.controllaMultiMatrimoni(intento,getContext(),null) ) // aggiunge 'idFamiglia' o 'collocazione'
						return; // se perno è sposo in più famiglie, chiede a chi aggiungere un coniuge o un figlio
					startActivity( intento );
				}).show();
			}
		} else if( id == 4 ) { // Collega persona esistente
			if( Globale.preferenze.esperto ) {
				DialogFragment dialog = new NuovoParente(pers, parentFam, spouseFam, false, Diagram.this);
				dialog.show(getActivity().getSupportFragmentManager(), "scegli");
			} else {
				new AlertDialog.Builder(getContext()).setItems( parenti, (dialog, quale) -> {
					Intent intento = new Intent( getContext(), Principe.class );
					intento.putExtra( "idIndividuo", idPersona );
					intento.putExtra( "anagrafeScegliParente", true );
					intento.putExtra( "relazione", quale + 1 );
					if( U.controllaMultiMatrimoni(intento, getContext(), Diagram.this) )
						return;
					startActivityForResult( intento,1401 );
				}).show();
			}
		} else if( id == 5 ) { // Modifica
			Intent intento = new Intent( getContext(), EditaIndividuo.class );
			intento.putExtra( "idIndividuo", idPersona );
			startActivity( intento );
		} else if( id == 6 ) { // Scollega
			Family[] famiglie = Anagrafe.scollega( idPersona );
			ripristina();
			U.aggiornaDate( pers );
			U.controllaFamiglieVuote(getContext(), this::ripristina, false, famiglie);
			U.salvaJson( false, famiglie );
		} else if( id == 7 ) { // Elimina
			new AlertDialog.Builder(getContext()).setMessage(R.string.really_delete_person)
					.setPositiveButton(R.string.delete, (dialog, i) -> {
						Family[] famiglie = Anagrafe.eliminaPersona(getContext(), idPersona);
						ripristina();
						U.controllaFamiglieVuote(getContext(), this::ripristina, false, famiglie);
					}).setNeutralButton(R.string.cancel, null).show();
		} else
			return false;
		return true;
	}
	private void ripristina() {
		forceDraw = true;
		onStart();
	}

	// Aggiunge il parente che è stata scelto in Anagrafe
	@Override
	public void onActivityResult( int requestCode, int resultCode, Intent data ) {
		if( resultCode == AppCompatActivity.RESULT_OK && requestCode == 1401 ) {
			Object[] modificati = EditaIndividuo.aggiungiParente(
					data.getStringExtra("idIndividuo"), // corrisponde a 'idPersona', il quale però si annulla in caso di cambio di configurazione
					data.getStringExtra("idParente"),
					data.getStringExtra("idFamiglia"),
					data.getIntExtra("relazione", 0),
					data.getStringExtra("collocazione") );
			U.salvaJson( true, modificati );
		}
	}
}
