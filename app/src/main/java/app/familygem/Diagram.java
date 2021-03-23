package app.familygem;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.Intent;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import com.otaliastudios.zoom.ZoomLayout;
import org.folg.gedcom.model.Family;
import org.folg.gedcom.model.Person;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import app.familygem.constants.Gender;
import app.familygem.dettaglio.Famiglia;
import graph.gedcom.FamilyNode;
import graph.gedcom.PersonNode;
import graph.gedcom.Graph;
import graph.gedcom.Line;
import graph.gedcom.Node;
import static graph.gedcom.Util.*;
import static app.familygem.Globale.gc;

public class Diagram extends Fragment {

	Graph graph;
	private ZoomLayout zoomBox;
	private RelativeLayout box;
	private GraphicPerson fulcrumView;
	private Person fulcrum;
	private FulcrumGlow glow;
	private Lines lines;
	private float zoomValue = 0.7f;
	private float density;
	private int STROKE;
	private final int GLOW_SPACE = 35; // Space to display glow, in dp
	private View popup; // Suggestion balloon
	public boolean forceDraw;
	private int linesColor;
	private Timer timer;
	private AnimatorSet animator;
	private boolean printPDF; // We are exporting a PDF

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle state) {
		density = getResources().getDisplayMetrics().density;
		STROKE = toPx(2);
		setStyle(false);

		getActivity().findViewById(R.id.toolbar).setVisibility(View.GONE); // Necessario in caso di backPressed dopo onActivityresult
		final View view = inflater.inflate(R.layout.diagram, container, false);
		view.findViewById(R.id.diagram_hamburger).setOnClickListener(v -> {
			DrawerLayout scatolissima = getActivity().findViewById(R.id.scatolissima);
			scatolissima.openDrawer(GravityCompat.START);
		});
		view.findViewById(R.id.diagram_options).setOnClickListener(vista -> {
			PopupMenu opzioni = new PopupMenu(getContext(), vista);
			Menu menu = opzioni.getMenu();
			menu.add(0, 0, 0, R.string.settings);
			if( gc.getPeople().size() > 0 )
				menu.add(0, 1, 0, R.string.export_pdf);
			opzioni.show();
			opzioni.setOnMenuItemClickListener(item -> {
				switch( item.getItemId() ) {
					case 0: // Diagram settings
						startActivity(new Intent(getContext(), DiagramSettings.class));
						break;
					case 1: // Export PDF
						setStyle(true);
						box.removeAllViews();
						printPDF = true;
						drawDiagram();
						F.salvaDocumento(null, this, Globale.preferenze.idAprendo, "application/pdf", "pdf", 903);
						break;
					default:
						return false;
				}
				return true;
			});
		});

		zoomBox = view.findViewById(R.id.diagram_zoom);
		box = view.findViewById(R.id.diagram_box);
		//box.setBackgroundColor(0x22ff0000);
		graph = new Graph(Globale.gc); // Create a diagram model
		forceDraw = true; // To be sure the diagram will be draw

		// Fade in animation
		ObjectAnimator alphaIn = ObjectAnimator.ofFloat(box, View.ALPHA, 1);
		alphaIn.setDuration(100);
		animator = new AnimatorSet();
		animator.play(alphaIn);

		return view;
	}

	// Individua il fulcro da cui partire, mostra eventuale bottone 'Crea la prima persona' oppure avvia il diagramma
	@Override
	public void onStart() {
		super.onStart();
		// Ragioni per cui bisogna proseguire, in particolare cose che sono cambiate
		if( forceDraw || (fulcrum != null && !fulcrum.getId().equals(Globale.individuo)) // TODO andrebbe testato
				|| (graph != null && graph.whichFamily != Globale.numFamiglia) ) {
			forceDraw = false;
			box.removeAllViews();

			String[] ids = {Globale.individuo, Globale.preferenze.alberoAperto().radice, U.trovaRadice(gc)};
			for( String id : ids ) {
				fulcrum = gc.getPerson(id);
				if( fulcrum != null )
					break;
			}
			// Empty diagram
			if( fulcrum == null ) {
				View button = LayoutInflater.from(getContext()).inflate( R.layout.diagram_button, null );
				button.findViewById( R.id.diagram_new ).setOnClickListener( v ->
					startActivity( new Intent( getContext(), EditaIndividuo.class )
						.putExtra( "idIndividuo", "TIZIO_NUOVO" )
					)
				);
				viewUnderBalloon( button, R.string.new_person );
				if( !Globale.preferenze.esperto )
					((View)zoomBox.getParent()).findViewById( R.id.diagram_options ).setVisibility( View.GONE );
			} else {
				Globale.individuo = fulcrum.getId(); // Casomai lo ribadisce
				graph.maxAncestors( Globale.preferenze.diagram.ancestors )
						.maxGreatUncles( Globale.preferenze.diagram.uncles )
						.displaySpouses( Globale.preferenze.diagram.spouses )
						.maxDescendants( Globale.preferenze.diagram.descendants )
						.maxSiblingsNephews( Globale.preferenze.diagram.siblings )
						.maxUnclesCousins( Globale.preferenze.diagram.cousins )
						.showFamily( Globale.numFamiglia )
						.startFrom( fulcrum );
				drawDiagram();
			}
		}
	}

	// Put a view under the suggestion balloon
	ConstraintLayout viewUnderBalloon( View view, int suggestion ) {
		View popupView = LayoutInflater.from(getContext()).inflate(R.layout.popup, box);
		ConstraintLayout popupLayout = popupView.findViewById( R.id.popup_layout );
		//popupLayout.setBackgroundColor( 0x66FF6600 );
		ConstraintLayout.LayoutParams nodeParams = new ConstraintLayout.LayoutParams(
				ConstraintLayout.LayoutParams.WRAP_CONTENT, ConstraintLayout.LayoutParams.WRAP_CONTENT);
		nodeParams.topToBottom = R.id.popup_fumetto;
		nodeParams.startToStart = ConstraintLayout.LayoutParams.PARENT_ID;
		nodeParams.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID;
		popupLayout.addView(view, nodeParams);
		popup = popupView.findViewById( R.id.popup_fumetto );
		((TextView)popup.findViewById( R.id.popup_testo )).setText( suggestion );
		popup.setVisibility( View.INVISIBLE );
		popup.setOnTouchListener( (v, e) -> {
			if( e.getAction() == MotionEvent.ACTION_DOWN ) {
				v.setVisibility( View.INVISIBLE );
				return true;
			}
			return false;
		});
		zoomBox.postDelayed( () -> {
			zoomBox.panTo( (zoomBox.getWidth()/zoomBox.getRealZoom() - box.getWidth()) / 2,
					(zoomBox.getHeight()/zoomBox.getRealZoom() - box.getHeight()) / 2, false);
			zoomBox.realZoomTo(1.3f, true);
			animator.start();
		}, 100);
		popup.postDelayed( () -> popup.setVisibility(View.VISIBLE), 1000 );
		return popupLayout;
	}

	// Diagram initialized the first time and clicking on a card
	void drawDiagram() {
		box.setAlpha(0);

		// Place various type of graphic nodes in the box taking them from the list of nodes
		for( Node node : graph.getNodes() ) {
			if( node instanceof PersonNode ) {
				PersonNode personNode = (PersonNode)node;
				if( personNode.person.getId().equals(Globale.individuo) && !personNode.isFulcrumNode() )
					box.addView(new Asterisk(getContext(), personNode));
				else if( personNode.mini )
					box.addView(new GraphicMiniCard(getContext(), personNode));
				else
					box.addView(new GraphicPerson(getContext(), personNode));
			}
		}

		// Only one person in the diagram
		if( gc.getPeople().size() == 1 && gc.getFamilies().size() == 0 && !printPDF ) {

			// Put the card under the suggestion balloon
			View singleNode = box.getChildAt(0);
			box.removeView(singleNode);
			singleNode.setId(R.id.tag_fulcrum);
			ConstraintLayout popupLayout = viewUnderBalloon(singleNode, R.string.long_press_menu);

			// Add the glow to the fulcrum card
			if( fulcrumView != null ) {
				box.post(() -> {
					ConstraintLayout.LayoutParams glowParams = new ConstraintLayout.LayoutParams(
							singleNode.getWidth() + toPx(GLOW_SPACE * 2), singleNode.getHeight() + toPx(GLOW_SPACE * 2));
					glowParams.topToTop = R.id.tag_fulcrum;
					glowParams.bottomToBottom = R.id.tag_fulcrum;
					glowParams.startToStart = ConstraintLayout.LayoutParams.PARENT_ID;
					glowParams.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID;
					fulcrumView.node.width = toDp(singleNode.getWidth());
					fulcrumView.node.height = toDp(singleNode.getHeight());
					popupLayout.addView(new FulcrumGlow(getContext()), 0, glowParams);
				});
			}

		} else { // Two or more persons in the diagram or PDF print

			box.postDelayed( () -> {
				// Get the dimensions of each node converting from pixel to dip
				for( int i = 0; i < box.getChildCount(); i++ ) {
					View nodeView = box.getChildAt( i );
					GraphicNode graphicNode = (GraphicNode) nodeView;
					// GraphicPerson can be larger because of VistaTesto, the child has the correct width
					graphicNode.node.width = toDp(graphicNode.getChildAt(0).getWidth());
					graphicNode.node.height = toDp(graphicNode.getChildAt(0).getHeight());
				}
				graph.initNodes(); // Initialize nodes and lines

				// Add marriage nodes
				for( Node node : graph.getNodes() ) {
					if( node instanceof FamilyNode )
						box.addView(new GraphicFamily(getContext(), (FamilyNode)node));
				}

				graph.placeNodes(); // Calculate first raw position

				// Add the lines
				lines = new Lines(getContext());
				RelativeLayout.LayoutParams paramLines = new RelativeLayout.LayoutParams(toPx(graph.getWidth()), toPx(graph.getHeight()));
				box.addView(lines, 0, paramLines);

				// Add the glow
				Node fulcrumNode = fulcrumView.node;
				RelativeLayout.LayoutParams glowParams = new RelativeLayout.LayoutParams(
						toPx(fulcrumNode.width + GLOW_SPACE * 2), toPx(fulcrumNode.height + GLOW_SPACE * 2));
				glowParams.rightMargin = -toPx(GLOW_SPACE);
				glowParams.bottomMargin = -toPx(GLOW_SPACE);
				box.addView(new FulcrumGlow(getContext()), 0, glowParams);

				displaceDiagram(); // First visible position of nodes, lines and glow

				// Pan to diagram fulcrum
				if( fulcrumView != null ) {
					zoomBox.post(() -> {
						int padding = box.getPaddingTop(); // box padding (50dp in px)
						zoomBox.realZoomTo(zoomValue, false); // Restore previous zoom
						zoomBox.panTo(-toPx(fulcrumNode.centerX()) + zoomBox.getWidth() / zoomBox.getRealZoom() / 2 - padding,
								-toPx(fulcrumNode.centerY()) + zoomBox.getHeight() / zoomBox.getRealZoom() / 2 - padding, false);
					});
				}
				animator.start();

				timer = new Timer();
				TimerTask task = new TimerTask() {
					@Override
					public void run() {
						getActivity().runOnUiThread(() -> {
							if( graph.playNodes() ) { // There is still some nodes to move
								displaceDiagram();
							} else { // Animation is complete
								timer.cancel();
								timer.purge();
							}
						});
					}
				};
				timer.scheduleAtFixedRate(task,0,40); // 40 = 25 fps

			}, 100);
		}
	}

	// Update visible position of nodes and lines
	void displaceDiagram() {
		// Position of the nodes from dips to pixels
		for( int i = 0; i < box.getChildCount(); i++ ) {
			View nodeView = box.getChildAt(i);
			if( nodeView instanceof GraphicNode ) {
				GraphicNode graphicNode = (GraphicNode)nodeView;
				RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams)graphicNode.getLayoutParams();
				params.leftMargin = toPx(graphicNode.node.x);
				params.topMargin = toPx(graphicNode.node.y);
				graphicNode.setLayoutParams(params);
			}
		}
		// The glow follows fulcrum
		RelativeLayout.LayoutParams glowParams = (RelativeLayout.LayoutParams)glow.getLayoutParams();
		glowParams.leftMargin = toPx(fulcrumView.node.x - GLOW_SPACE);
		glowParams.topMargin = toPx(fulcrumView.node.y - GLOW_SPACE);
		glow.setLayoutParams(glowParams);
		// Update lines
		lines.invalidate();
	}

	// Glow around fulcrum card
	class FulcrumGlow extends View {
		Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
		BlurMaskFilter bmf = new BlurMaskFilter(toPx(25), BlurMaskFilter.Blur.NORMAL);
		int extend = 5; // draw a rectangle a little bigger
		public FulcrumGlow(Context context) {
			super(context == null ? Globale.contesto : context);
			//setBackgroundColor( 0x330099FF );
			glow = this;
		}
		@Override
		protected void onDraw( Canvas canvas ) {
			paint.setColor(getResources().getColor(R.color.evidenzia));
			paint.setMaskFilter(bmf);
			setLayerType(View.LAYER_TYPE_SOFTWARE, paint);
			Node fulcrumNode = fulcrumView.node;
			canvas.drawRect(toPx(GLOW_SPACE - extend), toPx(GLOW_SPACE - extend),
					toPx(fulcrumNode.width + GLOW_SPACE + extend), toPx(fulcrumNode.height + GLOW_SPACE + extend), paint);
		}
	}

	// Node with one person or one marriage
	abstract class GraphicNode extends RelativeLayout {
		Node node;
		GraphicNode(Context context, Node node) {
			super(context);
			this.node = node;
			setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
			//setBackgroundColor( 0x5500ff00 );
		}
	}

	// Card of a person
	class GraphicPerson extends GraphicNode {
		public GraphicPerson(Context context, PersonNode personNode) {
			super(context, personNode);
			Person person = personNode.person;
			View view = getLayoutInflater().inflate(R.layout.diagram_card, this, true);
			View border = view.findViewById(R.id.card_border);
			if( Gender.isMale(person) )
				border.setBackgroundResource(R.drawable.casella_bordo_maschio);
			else if( Gender.isFemale(person) )
				border.setBackgroundResource(R.drawable.casella_bordo_femmina);
			ImageView background = view.findViewById(R.id.card_background);
			if( personNode.isFulcrumNode() ) {
				background.setBackgroundResource(R.drawable.casella_sfondo_evidente);
				fulcrumView = this;
			} else if( personNode.acquired ) {
				if( Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT )
					background.setBackgroundResource(R.drawable.casella_sfondo_sposo);
			}
			F.unaFoto( Globale.gc, person, view.findViewById( R.id.card_photo ) );
			TextView vistaNome = view.findViewById(R.id.card_name);
			String nome = U.epiteto(person);
			if( nome.isEmpty() && view.findViewById(R.id.card_photo).getVisibility()==View.VISIBLE )
				vistaNome.setVisibility( View.GONE );
			else vistaNome.setText( nome );
			TextView vistaTitolo = view.findViewById(R.id.card_title);
			String titolo = U.titolo( person );
			if( titolo.isEmpty() ) vistaTitolo.setVisibility(View.GONE);
			else vistaTitolo.setText(titolo);
			TextView vistaDati = view.findViewById(R.id.card_data);
			String dati = U.twoDates(person, false);
			if( dati.isEmpty() ) vistaDati.setVisibility(View.GONE);
			else vistaDati.setText(dati);
			if( !U.morto(person) )
				view.findViewById(R.id.card_mourn).setVisibility(View.GONE);
			registerForContextMenu(this);
			setOnClickListener( v -> {
				if( person.getId().equals(Globale.individuo) ) {
					Memoria.setPrimo( person );
					startActivity( new Intent(getContext(), Individuo.class) );
				} else {
					clickCard( person );
				}
			});
		}
	}

	// Marriage with eventual year and vertical line
	class GraphicFamily extends GraphicNode {
		GraphicFamily(Context context, FamilyNode familyNode) {
			super(context, familyNode);
			RelativeLayout familyLayout = new RelativeLayout(context);
			//familyLayout.setBackgroundColor(0x44ff00ff);
			addView( familyLayout, new LayoutParams(toPx(familyNode.width), toPx(familyNode.height)) );
			if( familyNode.hasChildren() ) {
				View verticaLine = new View(context);
				verticaLine.setBackgroundColor(linesColor);
				LayoutParams lineParams = new LayoutParams(STROKE, toPx(familyNode.height - familyNode.centerRelY()));
				lineParams.topMargin = toPx(familyNode.centerRelY());
				lineParams.addRule(CENTER_HORIZONTAL);
				familyLayout.addView(verticaLine, lineParams);
			}
			if( familyNode.marriageDate == null ) {
				View hearth = new View(context);
				if( Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT )
					hearth.setBackgroundResource(R.drawable.diagram_hearth);
				else
					hearth.setBackgroundColor(linesColor);
				int diameter = toPx(familyNode.mini ? MINI_HEARTH_DIAMETER : HEARTH_DIAMETER);
				LayoutParams hearthParams = new LayoutParams(diameter, diameter);
				hearthParams.topMargin = toPx(familyNode.centerRelY()) - diameter / 2;
				hearthParams.addRule(CENTER_HORIZONTAL);
				familyLayout.addView(hearth, hearthParams);
			} else {
				TextView year = new TextView( context );
				year.setBackgroundResource(R.drawable.diagramma_cerchio_anno);
				year.setGravity(Gravity.CENTER);
				year.setText(new Datatore(familyNode.marriageDate).writeDate(true));
				year.setTextSize(13f);
				familyLayout.addView(year, LayoutParams.MATCH_PARENT, toPx(MARRIAGE_HEIGHT));
			}
			setOnClickListener( view -> {
				Memoria.setPrimo( familyNode.spouseFamily );
				startActivity( new Intent( context, Famiglia.class ) );
			});
		}
	}

	// Little ancestry or progeny card
	class GraphicMiniCard extends GraphicNode {
		GraphicMiniCard(Context context, PersonNode personNode) {
			super(context, personNode);
			View graphicMiniCard = getLayoutInflater().inflate(R.layout.diagram_progeny, this, true);
			TextView miniCardText = graphicMiniCard.findViewById(R.id.progeny_number_text);
			miniCardText.setText(String.valueOf(personNode.amount));
			Gender sex = Gender.getGender(personNode.person);
			if( sex == Gender.MALE )
				miniCardText.setBackgroundResource(R.drawable.casella_bordo_maschio);
			else if( sex == Gender.FEMALE )
				miniCardText.setBackgroundResource(R.drawable.casella_bordo_femmina);
			graphicMiniCard.setOnClickListener(view -> clickCard(personNode.person));
		}
	}

	// Replacement for another person who is actually fulcrum
	class Asterisk extends GraphicNode {
		Asterisk(Context context, PersonNode personNode) {
			super(context, personNode);
			getLayoutInflater().inflate(R.layout.diagram_asterisk, this, true);
			registerForContextMenu(this);
			setOnClickListener( v -> {
				Memoria.setPrimo(personNode.person);
				startActivity(new Intent(getContext(), Individuo.class));
			});
		}
	}

	// Generate the view of lines connecting the cards
	class Lines extends View {
		Paint paint = new Paint( Paint.ANTI_ALIAS_FLAG );
		List<Path> paths = new ArrayList<>(); // Each path contains many lines
		int maxBitmapWidth;
		public Lines(Context context) {
			super(context == null ? Globale.contesto : context);
			//setBackgroundColor(0x330000ff);
			paint.setStyle( Paint.Style.STROKE );
			paint.setStrokeWidth( STROKE );
			paint.setColor( linesColor );
		}
		@Override
		public void invalidate() {
			if( maxBitmapWidth == 0 ) {
				postInvalidate(); // Requests another invalidate() that will call onDraw() again to fill 'maxBitmapWidth'
			} else {
				lines.paths.clear();
				paths.add(new Path());
				float restartX = 0; // (re)starting point of every path
				int pathNum = 0; // index of paths
				// Put the lines in one or more paths
				for( Line line : graph.getLines() ) {
					float x1 = toPx(line.x1), y1 = toPx(line.y1), x2 = toPx(line.x2), y2 = toPx(line.y2);
					float lineWidth = Math.max(x1, x2) - Math.min(x1, x2);
					if( lineWidth <= maxBitmapWidth ) {
						float pathRight = Math.max(x1, x2) - restartX;
						// Start another path
						if( pathRight > maxBitmapWidth ) {
							pathNum++;
							restartX = Math.min(x1, x2);
							paths.add(new Path());
						}
						paths.get(pathNum).moveTo(x1, y1);
						paths.get(pathNum).cubicTo(x1, y2, x2, y1, x2, y2);
					}
				}
				// Update view size
				RelativeLayout.LayoutParams paramLines = new RelativeLayout.LayoutParams(toPx(graph.getWidth()), toPx(graph.getHeight()));
				setLayoutParams(paramLines);
			}
		}
		@Override
		protected void onDraw(Canvas canvas) {
			if( maxBitmapWidth == 0 )
				maxBitmapWidth = canvas.getMaximumBitmapWidth() // is 16384 on emulators, 4096 on my physical devices
					- STROKE * 3; // the space actually occupied by the line is a little bit larger
			// Draw the paths
			for( Path path : paths ) {
				canvas.drawPath(path, paint);
			}
		}
	}

	@Override
	public void onPause() {
		super.onPause();
		if( timer != null ) {
			timer.cancel();
			timer.purge();
		}
	}

	private void clickCard(Person person) {
		timer.cancel();
		timer.purge();
		selectParentFamily( person );
	}

	// Ask which family to display in the diagram if fulcrum has many parent families
	private void selectParentFamily(Person fulcrum) {
		List<Family> families = fulcrum.getParentFamilies(gc);
		if( families.size() > 1 ) {
			new AlertDialog.Builder(getContext()).setTitle(R.string.which_family)
					.setItems(U.elencoFamiglie(families), (dialog, which) -> {
						completeSelect(fulcrum, which);
					}).show();
		} else {
			completeSelect(fulcrum, 0);
		}
	}
	// Complete above function
	private void completeSelect(Person fulcrum, int whichFamily) {
		zoomValue = zoomBox.getRealZoom();
		Globale.individuo = fulcrum.getId();
		Globale.numFamiglia = whichFamily;
		graph.showFamily(Globale.numFamiglia);
		graph.startFrom(fulcrum);
		box.removeAllViews();
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
	public void onCreateContextMenu(ContextMenu menu, View vista, ContextMenu.ContextMenuInfo info) {
		PersonNode personNode = null;
		if( vista instanceof GraphicPerson )
			personNode = (PersonNode)((GraphicPerson)vista).node;
		else if( vista instanceof Asterisk )
			personNode = (PersonNode)((Asterisk)vista).node;
		pers = personNode.person;
		if( personNode.origin != null )
			parentFam = personNode.origin.spouseFamily;
		spouseFam = personNode.spouseFamily;
		idPersona = pers.getId();
		String[] familyLabels = getFamilyLabels(getContext(), pers);

		if( idPersona.equals(Globale.individuo) && pers.getParentFamilies(gc).size() > 1 )
			menu.add(0, -1, 0, R.string.diagram);
		if( !idPersona.equals(Globale.individuo) )
			menu.add(0, 0, 0, R.string.card);
		if( familyLabels[0] != null )
			menu.add(0, 1, 0, familyLabels[0]);
		if( familyLabels[1] != null )
			menu.add(0, 2, 0, familyLabels[1]);
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
	public boolean onContextItemSelected(MenuItem item) {
		CharSequence[] parenti = {getText(R.string.parent), getText(R.string.sibling), getText(R.string.spouse), getText(R.string.child)};
		int id = item.getItemId();
		if( id == -1 ) { // Diagramma per fulcro figlio in più famiglie
			if( pers.getParentFamilies(gc).size() > 2 ) // Più di due famiglie
				selectParentFamily(pers);
			else // Due famiglie
				completeSelect(pers, Globale.numFamiglia == 0 ? 1 : 0);
		} else if( id == 0 ) { // Apri scheda individuo
			Memoria.setPrimo(pers);
			startActivity(new Intent(getContext(), Individuo.class));
		} else if( id == 1 ) { // Famiglia come figlio
			if( idPersona.equals(Globale.individuo) ) { // Se è fulcro apre direttamente la famiglia
				Memoria.setPrimo(parentFam);
				startActivity(new Intent(getContext(), Famiglia.class));
			} else
				U.qualiGenitoriMostrare(getContext(), pers, 2);
		} else if( id == 2 ) { // Famiglia come coniuge
			U.qualiConiugiMostrare(getContext(), pers,
					idPersona.equals(Globale.individuo) ? spouseFam : null); // Se è fulcro apre direttamente la famiglia
		} else if( id == 3 ) { // Collega persona nuova
			if( Globale.preferenze.esperto ) {
				DialogFragment dialog = new NuovoParente(pers, parentFam, spouseFam, true, null);
				dialog.show(getActivity().getSupportFragmentManager(), "scegli");
			} else {
				new AlertDialog.Builder(getContext()).setItems(parenti, (dialog, quale) -> {
					Intent intento = new Intent(getContext(), EditaIndividuo.class);
					intento.putExtra("idIndividuo", idPersona);
					intento.putExtra("relazione", quale + 1);
					if( U.controllaMultiMatrimoni(intento, getContext(), null) ) // aggiunge 'idFamiglia' o 'collocazione'
						return; // se perno è sposo in più famiglie, chiede a chi aggiungere un coniuge o un figlio
					startActivity(intento);
				}).show();
			}
		} else if( id == 4 ) { // Collega persona esistente
			if( Globale.preferenze.esperto ) {
				DialogFragment dialog = new NuovoParente(pers, parentFam, spouseFam, false, Diagram.this);
				dialog.show(getActivity().getSupportFragmentManager(), "scegli");
			} else {
				new AlertDialog.Builder(getContext()).setItems(parenti, (dialog, quale) -> {
					Intent intento = new Intent(getContext(), Principe.class);
					intento.putExtra("idIndividuo", idPersona);
					intento.putExtra("anagrafeScegliParente", true);
					intento.putExtra("relazione", quale + 1);
					if( U.controllaMultiMatrimoni(intento, getContext(), Diagram.this) )
						return;
					startActivityForResult(intento, 1401);
				}).show();
			}
		} else if( id == 5 ) { // Modifica
			Intent intento = new Intent(getContext(), EditaIndividuo.class);
			intento.putExtra("idIndividuo", idPersona);
			startActivity(intento);
		} else if( id == 6 ) { // Scollega
			/*  Todo ad esser precisi bisognerebbe usare Famiglia.scollega( sfr, sr )
				che rimuove esattamente il singolo link anziché tutti i link se una persona è linkata + volte nella stessa famiglia
			 */
			List<Family> modificate = new ArrayList<>();
			if( parentFam != null ) {
				Famiglia.scollega(idPersona, parentFam);
				modificate.add(parentFam);
			}
			if( spouseFam != null ) {
				Famiglia.scollega(idPersona, spouseFam);
				modificate.add(spouseFam);
			}
			ripristina();
			Family[] modificateArr = modificate.toArray(new Family[0]);
			U.controllaFamiglieVuote(getContext(), this::ripristina, false, modificateArr);
			U.aggiornaDate(pers);
			U.salvaJson(true, modificateArr);
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

	// Modify some style to export the PDF
	private void setStyle(boolean printing) {
		linesColor = getResources().getColor(
				printing ? R.color.lineeDiagrammaStampa : R.color.lineeDiagrammaSchermo);
		getActivity().setTheme(printing ? R.style.diagramPrint : R.style.diagramScreen);
	}

	@Override
	public void onActivityResult( int requestCode, int resultCode, Intent data ) {
		if( resultCode == AppCompatActivity.RESULT_OK ) {
			// Aggiunge il parente che è stata scelto in Anagrafe
			if( requestCode == 1401 ) {
				Object[] modificati = EditaIndividuo.aggiungiParente(
						data.getStringExtra("idIndividuo"), // corrisponde a 'idPersona', il quale però si annulla in caso di cambio di configurazione
						data.getStringExtra("idParente"),
						data.getStringExtra("idFamiglia"),
						data.getIntExtra("relazione", 0),
						data.getStringExtra("collocazione") );
				U.salvaJson( true, modificati );
			} // Export diagram to PDF
			else if( requestCode == 903 ) {
				// Stylize diagram for print
				fulcrumView.findViewById( R.id.card_background ).setBackgroundResource( R.drawable.casella_sfondo_base );
				if( glow != null )
					glow.setVisibility( View.GONE );
				int width = toPx(graph.getWidth() + 100); // 50dp di padding
				int height = toPx(graph.getHeight() + 100);
				// Create PDF
				PdfDocument document = new PdfDocument();
				PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(width, height, 1).create();
				PdfDocument.Page page = document.startPage( pageInfo );
				box.draw( page.getCanvas() );
				document.finishPage(page);
				// Write PDF
				Uri uri = data.getData();
				try {
					OutputStream out = getContext().getContentResolver().openOutputStream( uri );
					document.writeTo( out );
					out.flush();
					out.close();
				} catch (Exception e) {
					e.printStackTrace();
				}
				printPDF = false;
				setStyle( false ); // Reset for screen
				Toast.makeText(getContext(), R.string.pdf_exported_ok, Toast.LENGTH_LONG).show();
			}
		}
	}
}
