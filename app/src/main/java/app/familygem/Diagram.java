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
import android.view.Menu;
import android.view.MenuInflater;
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

	private Graph graph;
	private ZoomLayout zoomBox;
	private RelativeLayout box;
	private View fulcrumCard;
	private float zoomValue = 0.7f;

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
		setHasOptionsMenu(true);

		// Create a diagram model
		graph = new Graph( Globale.gc );
		graph.maxAncestors( Globale.preferenze.diagram.ancestors )
				.maxUncles( Globale.preferenze.diagram.uncles )
				.displaySiblings( Globale.preferenze.diagram.siblings )
				.maxDescendants( Globale.preferenze.diagram.descendants )
				.showFamily( getActivity().getIntent().getIntExtra("genitoriNum", 0) );
		getActivity().getIntent().putExtra( "genitoriNum", 0 ); // Reset the value for next calls
		drawDiagram();
		return view;
	}

	private void drawDiagram() {

		// Empty diagram
		if( !graph.startFrom(Globale.individuo,Globale.preferenze.alberoAperto().radice,U.trovaRadice(gc)) ) {
			Button button = new Button(getContext());
			button.setText( R.string.new_person );
			box.addView( button );
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
				box.addView( new GraphicUnitNode( getContext(), (UnitNode) node ) );
					//	,RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
			else if( node instanceof AncestryNode )
				box.addView( new GraphicAncestry(getContext(), (AncestryNode)node, false));
			else if( node instanceof ProgenyNode )
				box.addView( new GraphicProgeny(getContext(), (ProgenyNode) node));
		}

		box.post(new Runnable() {
			@Override
			public void run() {
				// Get the dimensions of various nodes
				for (int i = 0; i < box.getChildCount(); i++) {
					View nodeView = box.getChildAt( i );
					if( nodeView instanceof GraphicUnitNode) {
						// Get the bond width
						GraphicUnitNode graphicUnitNode = (GraphicUnitNode) nodeView;
						Bond bond = nodeView.findViewById( R.id.tag_bond );
						if( bond != null )
							graphicUnitNode.unitNode.bondWidth = bond.getWidth();
						// Get dimensions of each graphic card
						for( int c = 0; c < graphicUnitNode.getChildCount(); c++ ) {
							View cardView = graphicUnitNode.getChildAt( c );
							if( cardView instanceof GraphicCardBox) {
								GraphicCardBox graphicCard = (GraphicCardBox) cardView;
								graphicCard.card.width = cardView.getWidth();
								graphicCard.card.height = cardView.getHeight();
							}
						}
					} // Get dimensions of each ancestry node
					else if( nodeView instanceof GraphicAncestry) {
						GraphicAncestry graphicAncestry = (GraphicAncestry) nodeView;
						graphicAncestry.node.width = nodeView.getWidth();
						graphicAncestry.node.height = nodeView.getHeight();
						if( graphicAncestry.node.isCouple() ) {
							graphicAncestry.node.horizontalCenter =
									graphicAncestry.findViewById( R.id.ancestry_father ).getWidth() +
									graphicAncestry.findViewById( R.id.ancestry_connector ).getWidth() / 2;
						} else
							graphicAncestry.node.horizontalCenter = nodeView.getWidth() / 2;
					} // Get the dimensions of each progeny node
					else if( nodeView instanceof GraphicProgeny ) {
						GraphicProgeny graphicProgeny = (GraphicProgeny) nodeView;
						ProgenyNode progeny = graphicProgeny.progenyNode;
						progeny.width = graphicProgeny.getWidth();
						for(int p=0; p <graphicProgeny.getChildCount(); p++) {
							View miniCard = graphicProgeny.getChildAt(p);
							progeny.miniChildren.get(p).width = miniCard.getWidth();
						}
					}
				}

				// Let the graph calculate positions of Nodes and Lines
				graph.arrange();

				// Final position of the nodes
				for (int i = 0; i < box.getChildCount(); i++) {
					View nodeView = box.getChildAt( i );
					if( nodeView instanceof GraphicUnitNode) {
						GraphicUnitNode graphicUnitNode = (GraphicUnitNode) nodeView;
						RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) graphicUnitNode.getLayoutParams();
						params.leftMargin = graphicUnitNode.unitNode.x;
						params.topMargin = graphicUnitNode.unitNode.y;
						graphicUnitNode.setLayoutParams( params );
						// Bond height
						if( graphicUnitNode.unitNode.isCouple() ) {
							Bond bond = graphicUnitNode.findViewById( R.id.tag_bond  );
							RelativeLayout.LayoutParams bondParams = (RelativeLayout.LayoutParams) bond.getLayoutParams();
							if(graphicUnitNode.unitNode.hasChildren()) {
								bondParams.height = graphicUnitNode.unitNode.height / 2 + bond.getHeight() / 2;
							}
							bond.setLayoutParams( bondParams );
						}
					} else if( nodeView instanceof GraphicAncestry ) {
						AncestryNode ancestry = ((GraphicAncestry) nodeView).node;
						RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) nodeView.getLayoutParams();
						params.leftMargin = ancestry.x;
						params.topMargin = ancestry.y;
						nodeView.setLayoutParams(params);
					} else if( nodeView instanceof GraphicProgeny ) {
						ProgenyNode progeny = ((GraphicProgeny) nodeView).progenyNode;
						RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) nodeView.getLayoutParams();
						params.leftMargin = progeny.x;
						params.topMargin = progeny.y;
						nodeView.setLayoutParams( params );
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

	// Node with one person or couple + marriage
	class GraphicUnitNode extends RelativeLayout {
		UnitNode unitNode;
		GraphicUnitNode( final Context context, UnitNode unitNode ) {
			super(context);
			this.unitNode = unitNode;
			setClipChildren( false );
			//setBackgroundColor( 0x33FF00FF );
			if( unitNode.husband != null ) {
				addView( new GraphicCardBox(context,unitNode.husband,true) );
			}
			if( unitNode.wife != null ) {
				addView( new GraphicCardBox(context,unitNode.wife,false) );
			}
			if( unitNode.isCouple() ) {
				Bond bond = new Bond(getContext(), unitNode);
				LayoutParams bondParams = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
				bondParams.addRule( unitNode.hasChildren() ? ALIGN_PARENT_BOTTOM : CENTER_VERTICAL);
				bondParams.addRule( RIGHT_OF, R.id.tag_husband );
				if( unitNode.marriageDate != null ) {
					bondParams.leftMargin = -Util.TIC;
					bondParams.rightMargin = -Util.TIC;
				}
				addView( bond, bondParams );
			}
		}
	}

	// Container for one card with eventual ancestors above
	class GraphicCardBox extends RelativeLayout {
		IndiCard card;
		GraphicCardBox( Context context, IndiCard card, boolean husband ) {
			super(context);
			this.card = card;
			LayoutParams params = new LayoutParams( LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT );
			params.addRule( CENTER_VERTICAL );
			if (husband)
				setId( R.id.tag_husband );
			 else
				params.addRule( RIGHT_OF, R.id.tag_bond );
			setLayoutParams( params );
			GraphicCard graphicCard = new GraphicCard(context, card);
			graphicCard.setId( R.id.card );
			addView( graphicCard );
			setClipChildren( false );
			if( card.acquired && card.hasAncestry() ) {
				LayoutParams ancestryParams = new LayoutParams( LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT );
				ancestryParams.addRule( RelativeLayout.ABOVE, graphicCard.getId() );
				ancestryParams.addRule( RelativeLayout.CENTER_HORIZONTAL );
				addView( new GraphicAncestry(context, (AncestryNode)card.origin, true), ancestryParams);
			}
		}
	}

	// Card of a person
	class GraphicCard extends LinearLayout {
		IndiCard card;
		public GraphicCard( Context context, final IndiCard card ) {
			super(context);
			this.card = card;
			Person person = card.person;
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
			registerForContextMenu(this);
			setOnClickListener( new OnClickListener() {
				@Override
				public void onClick( View v ) {
					Person person = card.person;
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

	// Marriage with eventual year and vertical line
	class Bond extends FrameLayout {
		Bond( final Context context, UnitNode unitNode) {
			super(context);
			if (unitNode.hasChildren()) {
				View verticaLine = new View( context );
				verticaLine.setBackgroundColor( 0xffffffff );
				LayoutParams lineParams = new LayoutParams( LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT );
				lineParams.width = 4;
				lineParams.gravity = Gravity.CENTER_HORIZONTAL;
				addView( verticaLine, lineParams );
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
				year.setTextSize( 13f );
				addView( year, LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT );
			}
			setId( R.id.tag_bond );
			setOnClickListener( new View.OnClickListener() {
				@Override
				public void onClick( View view ) {
					//Memoria.setPrimo( famiglia ); // TODO famiglia?
					startActivity( new Intent( context, Famiglia.class ) );
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
			if( node.miniFather == null ) {
				testoAvi.setVisibility( View.GONE );
				RelativeLayout.LayoutParams param = (RelativeLayout.LayoutParams) testoAve.getLayoutParams();
				param.addRule( RelativeLayout.RIGHT_OF, 0 );
			} else {
				testoAvi.setText( String.valueOf(node.miniFather.ancestry) );
				testoAvi.setOnClickListener( new View.OnClickListener() {
					@Override
					public void onClick( View v ) {
						clickCard(node.miniFather.person);
					}
				});
			}
			if( node.miniMother == null ) {
				testoAve.setVisibility( View.GONE );
				RelativeLayout.LayoutParams param = (RelativeLayout.LayoutParams) findViewById( R.id.ancestry_connector ).getLayoutParams();
				param.addRule( RelativeLayout.RIGHT_OF, 0 );
			} else {
				testoAve.setText( String.valueOf(node.miniMother.ancestry) );
				testoAve.setOnClickListener( new View.OnClickListener() {
					@Override
					public void onClick( View v ) {
						clickCard(node.miniMother.person);
					}
				} );
			}
			if( acquired ) {
				setAlpha( 0.7f );
				View verticalLine = findViewById( R.id.ancestry_connector_vertical );
				LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) verticalLine.getLayoutParams();
				params.height = 45;
				verticalLine.setLayoutParams(params);
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
			params.topMargin = Util.GAP;
			params.gravity = Gravity.CENTER_HORIZONTAL;
			setLayoutParams( params );
			setClipChildren( false );
			//setBackgroundColor( 0x330000FF );
			for( int i = 0; i < progenyNode.miniChildren.size(); i++ ) {
				final MiniCard miniChild = progenyNode.miniChildren.get(i);
				View graphicMiniCard = getLayoutInflater().inflate( R.layout.diagram_progeny, this, false );
				((TextView)graphicMiniCard.findViewById( R.id.progeny_number ) ).setText( String.valueOf( miniChild.ancestry ) );
				int sex = U.sesso( miniChild.person );
				int background = R.drawable.casella_neutro;
				if( sex == 1 )
					background = R.drawable.casella_maschio;
				else if( sex == 2 )
					background = R.drawable.casella_femmina;
				graphicMiniCard.setBackgroundResource( background );
				if( i < progenyNode.miniChildren.size() - 1 ) {
					LayoutParams cardParams = (LayoutParams) graphicMiniCard.getLayoutParams();
					cardParams.rightMargin = Util.PLAY;
					graphicMiniCard.setLayoutParams( cardParams );
				}
				graphicMiniCard.setOnClickListener( new OnClickListener() {
					@Override
					public void onClick( View view ) {
						clickCard( miniChild.person );
					}
				});
				addView( graphicMiniCard );
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

	private void clickCard(Person person) {
		if( U.qualiGenitoriMostrare(getContext(), person, Principe.class) )
			return;
		zoomValue = zoomBox.getRealZoom();
		box.removeAllViews();
		Globale.individuo = person.getId();
		drawDiagram();
	}

	// Menu contestuale
	private String idPersona;
	private View vistaScelta;
	private Person pers;
	@Override
	public void onCreateContextMenu( @NonNull ContextMenu menu, @NonNull View vista, ContextMenu.ContextMenuInfo info ) {
		vistaScelta = vista;
		idPersona = ((GraphicCard)vista).card.person.getId();
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

	@Override
	public void onCreateOptionsMenu( Menu menu, MenuInflater inflater ) {
		menu.add( 0,0,0, "Diagram settings" );
	}

	@Override
	public boolean onOptionsItemSelected( MenuItem item ) {
		switch( item.getItemId() ) {
			case 0:
				startActivity( new Intent(getContext(), DiagramSettings.class) );
				return true;
			default:
				return false;
		}
	}
}
