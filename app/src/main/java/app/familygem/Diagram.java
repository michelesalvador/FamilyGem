package app.familygem;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.Intent;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
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
import androidx.core.text.TextUtilsCompat;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import org.folg.gedcom.model.Family;
import org.folg.gedcom.model.Person;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import app.familygem.constants.Gender;
import app.familygem.constants.Relation;
import app.familygem.dettaglio.Famiglia;
import graph.gedcom.Bond;
import graph.gedcom.CurveLine;
import graph.gedcom.FamilyNode;
import graph.gedcom.Metric;
import graph.gedcom.PersonNode;
import graph.gedcom.Graph;
import graph.gedcom.Line;
import static graph.gedcom.Util.*;
import static app.familygem.Global.gc;

public class Diagram extends Fragment {

	private Graph graph;
	private MoveLayout moveLayout;
	private RelativeLayout box;
	private GraphicPerson fulcrumView;
	private Person fulcrum;
	private FulcrumGlow glow;
	private Lines lines;
	private Lines backLines;
	private float density;
	private int STROKE;
	private final int GLOW_SPACE = 35; // Space to display glow, in dp
	private View popup; // Suggestion balloon
	boolean forceDraw;
	private Timer timer;
	private boolean play;
	private AnimatorSet animator;
	private boolean printPDF; // We are exporting a PDF
	private final boolean leftToRight = TextUtilsCompat.getLayoutDirectionFromLocale(Locale.getDefault()) == ViewCompat.LAYOUT_DIRECTION_LTR;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle state) {
		density = getResources().getDisplayMetrics().density;
		STROKE = toPx(2);

		getActivity().findViewById(R.id.toolbar).setVisibility(View.GONE); // Necessario in caso di backPressed dopo onActivityresult
		final View view = inflater.inflate(R.layout.diagram, container, false);
		view.findViewById(R.id.diagram_hamburger).setOnClickListener(v -> {
			DrawerLayout scatolissima = getActivity().findViewById(R.id.scatolissima);
			scatolissima.openDrawer(GravityCompat.START);
		});
		view.findViewById(R.id.diagram_options).setOnClickListener(vista -> {
			PopupMenu opzioni = new PopupMenu(getContext(), vista);
			Menu menu = opzioni.getMenu();
			menu.add(0, 0, 0, R.string.diagram_settings);
			if( gc.getPeople().size() > 0 )
				menu.add(0, 1, 0, R.string.export_pdf);
			opzioni.show();
			opzioni.setOnMenuItemClickListener(item -> {
				switch( item.getItemId() ) {
					case 0: // Diagram settings
						startActivity(new Intent(getContext(), DiagramSettings.class));
						break;
					case 1: // Export PDF
						F.salvaDocumento(null, this, Global.settings.openTree, "application/pdf", "pdf", 903);
						break;
					default:
						return false;
				}
				return true;
			});
		});

		moveLayout = view.findViewById(R.id.diagram_frame);
		moveLayout.leftToRight = leftToRight;
		box = view.findViewById(R.id.diagram_box);
		//box.setBackgroundColor(0x22ff0000);
		graph = new Graph(Global.gc); // Create a diagram model
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
		if( forceDraw || (fulcrum != null && !fulcrum.getId().equals(Global.indi)) // TODO andrebbe testato
				|| (graph != null && graph.whichFamily != Global.familyNum) ) {
			forceDraw = false;
			box.removeAllViews();
			box.setAlpha(0);

			String[] ids = {Global.indi, Global.settings.getCurrentTree().root, U.trovaRadice(gc)};
			for( String id : ids ) {
				fulcrum = gc.getPerson(id);
				if( fulcrum != null )
					break;
			}
			// Empty diagram
			if( fulcrum == null ) {
				View button = LayoutInflater.from(getContext()).inflate(R.layout.diagram_button, null);
				button.findViewById(R.id.diagram_new).setOnClickListener(v ->
						startActivity(new Intent(getContext(), EditaIndividuo.class)
								.putExtra("idIndividuo", "TIZIO_NUOVO")
						)
				);
				new SuggestionBalloon(getContext(), button, R.string.new_person);
				if( !Global.settings.expert )
					((View)moveLayout.getParent()).findViewById(R.id.diagram_options).setVisibility(View.GONE);
			} else {
				Global.indi = fulcrum.getId(); // Casomai lo ribadisce
				graph.maxAncestors(Global.settings.diagram.ancestors)
						.maxGreatUncles(Global.settings.diagram.uncles)
						.displaySpouses(Global.settings.diagram.spouses)
						.maxDescendants(Global.settings.diagram.descendants)
						.maxSiblingsNephews(Global.settings.diagram.siblings)
						.maxUnclesCousins(Global.settings.diagram.cousins)
						.showFamily(Global.familyNum)
						.startFrom(fulcrum);
				drawDiagram();
			}
		}
	}

	// Put a view under the suggestion balloon
	class SuggestionBalloon extends ConstraintLayout {
		SuggestionBalloon(Context context, View childView, int suggestion) {
			super(context);
			View view = getLayoutInflater().inflate(R.layout.popup, this, true);
			box.addView(view);
			//setBackgroundColor(0x330066FF);
			LayoutParams nodeParams = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
			nodeParams.topToBottom = R.id.popup_fumetto;
			nodeParams.startToStart = LayoutParams.PARENT_ID;
			nodeParams.endToEnd = LayoutParams.PARENT_ID;
			addView(childView, nodeParams);
			popup = view.findViewById(R.id.popup_fumetto);
			((TextView)popup.findViewById(R.id.popup_testo)).setText(suggestion);
			popup.setVisibility(INVISIBLE);
			popup.setOnTouchListener((v, e) -> {
				if( e.getAction() == MotionEvent.ACTION_DOWN ) {
					v.setVisibility(INVISIBLE);
					return true;
				}
				return false;
			});
			postDelayed(() -> {
				moveLayout.childWidth = box.getWidth();
				moveLayout.childHeight = box.getHeight();
				moveLayout.displayAll();
				animator.start();
			}, 100);
			popup.postDelayed(() -> popup.setVisibility(VISIBLE), 1000);
		}
		@Override
		public void invalidate() {
			if( printPDF ) {
				popup.setVisibility(GONE);
				if( glow != null ) glow.setVisibility(GONE);
			}
		}
	}

	// Diagram initialized the first time and clicking on a card
	void drawDiagram() {

		// Place various type of graphic nodes in the box taking them from the list of nodes
		for( PersonNode personNode : graph.getPersonNodes() ) {
			if( personNode.person.getId().equals(Global.indi) && !personNode.isFulcrumNode() )
				box.addView(new Asterisk(getContext(), personNode));
			else if( personNode.mini )
				box.addView(new GraphicMiniCard(getContext(), personNode));
			else
				box.addView(new GraphicPerson(getContext(), personNode));
		}

		// Only one person in the diagram
		if( gc.getPeople().size() == 1 && gc.getFamilies().size() == 0 && !printPDF ) {

			// Put the card under the suggestion balloon
			View singleNode = box.getChildAt(0);
			box.removeView(singleNode);
			singleNode.setId(R.id.tag_fulcrum);
			ConstraintLayout popupLayout = new SuggestionBalloon(getContext(), singleNode, R.string.long_press_menu);

			// Add the glow to the fulcrum card
			if( fulcrumView != null ) {
				box.post(() -> {
					ConstraintLayout.LayoutParams glowParams = new ConstraintLayout.LayoutParams(
							singleNode.getWidth() + toPx(GLOW_SPACE * 2), singleNode.getHeight() + toPx(GLOW_SPACE * 2));
					glowParams.topToTop = R.id.tag_fulcrum;
					glowParams.bottomToBottom = R.id.tag_fulcrum;
					glowParams.startToStart = ConstraintLayout.LayoutParams.PARENT_ID;
					glowParams.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID;
					fulcrumView.metric.width = toDp(singleNode.getWidth());
					fulcrumView.metric.height = toDp(singleNode.getHeight());
					popupLayout.addView(new FulcrumGlow(getContext()), 0, glowParams);
				});
			}

		} else { // Two or more persons in the diagram or PDF print

			box.postDelayed( () -> {
				// Get the dimensions of each node converting from pixel to dip
				for( int i = 0; i < box.getChildCount(); i++ ) {
					View nodeView = box.getChildAt( i );
					GraphicMetric graphic = (GraphicMetric)nodeView;
					// GraphicPerson can be larger because of VistaTesto, the child has the correct width
					graphic.metric.width = toDp(graphic.getChildAt(0).getWidth());
					graphic.metric.height = toDp(graphic.getChildAt(0).getHeight());
				}
				graph.initNodes(); // Initialize nodes and lines

				// Add bond nodes
				for( Bond bond : graph.getBonds() ) {
					box.addView(new GraphicBond(getContext(), bond));
				}

				graph.placeNodes(); // Calculate first raw position

				// Add the lines
				lines = new Lines(getContext(), graph.getLines(), null);
				box.addView(lines, 0);
				backLines = new Lines(getContext(), graph.getBackLines(), new DashPathEffect(new float[]{toPx(4), toPx(4)}, 0));
				box.addView(backLines, 0);

				// Add the glow
				PersonNode fulcrumNode = (PersonNode)fulcrumView.metric;
				RelativeLayout.LayoutParams glowParams = new RelativeLayout.LayoutParams(
						toPx(fulcrumNode.width + GLOW_SPACE * 2), toPx(fulcrumNode.height + GLOW_SPACE * 2));
				glowParams.rightMargin = -toPx(GLOW_SPACE);
				glowParams.bottomMargin = -toPx(GLOW_SPACE);
				box.addView(new FulcrumGlow(getContext()), 0, glowParams);

				play = true;
				timer = new Timer();
				TimerTask task = new TimerTask() {
					@Override
					public void run() {
						getActivity().runOnUiThread(() -> {
							if( play ) {
								play = graph.playNodes(); // Check if there is still some nodes to move
								displaceDiagram();
							}
						});
						if( !play ) { // Animation is complete
							timer.cancel();
						}
					}
				};
				moveLayout.virgin = true;
				timer.scheduleAtFixedRate(task, 0, 40); // 40 milliseconds = 25 fps

				animator.start();
			}, 100);
		}
	}

	// Update visible position of nodes and lines
	void displaceDiagram() {
		if( moveLayout.scaleDetector.isInProgress() )
			return;
		// Position of the nodes from dips to pixels
		for( int i = 0; i < box.getChildCount(); i++ ) {
			View nodeView = box.getChildAt(i);
			if( nodeView instanceof GraphicMetric ) {
				GraphicMetric graphicNode = (GraphicMetric)nodeView;
				RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams)graphicNode.getLayoutParams();
				if( leftToRight ) params.leftMargin = toPx(graphicNode.metric.x);
				else params.rightMargin = toPx(graphicNode.metric.x);
				params.topMargin = toPx(graphicNode.metric.y);
			}
		}
		// The glow follows fulcrum
		RelativeLayout.LayoutParams glowParams = (RelativeLayout.LayoutParams)glow.getLayoutParams();
		if( leftToRight ) glowParams.leftMargin = toPx(fulcrumView.metric.x - GLOW_SPACE);
		else glowParams.rightMargin = toPx(fulcrumView.metric.x - GLOW_SPACE);
		glowParams.topMargin = toPx(fulcrumView.metric.y - GLOW_SPACE);

		moveLayout.childWidth = toPx(graph.getWidth()) + box.getPaddingStart() * 2;
		moveLayout.childHeight = toPx(graph.getHeight()) + box.getPaddingTop() * 2;

		// Update lines
		lines.invalidate();
		backLines.invalidate();

		// Pan to fulcrum
		if( moveLayout.virgin ) {
			float scale = moveLayout.minimumScale();
			float padding = box.getPaddingTop() * scale;
			moveLayout.panTo((int)(leftToRight ? toPx(fulcrumView.metric.centerX()) * scale - moveLayout.width / 2 + padding
							: moveLayout.width / 2 - toPx(fulcrumView.metric.centerX()) * scale - padding),
					(int)(toPx(fulcrumView.metric.centerY()) * scale - moveLayout.height / 2 + padding));
		} else {
			moveLayout.keepPositionResizing();
		}
		box.requestLayout();
	}

	// The glow around fulcrum card
	class FulcrumGlow extends View {
		Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
		BlurMaskFilter bmf = new BlurMaskFilter(toPx(25), BlurMaskFilter.Blur.NORMAL);
		int extend = 5; // draw a rectangle a little bigger
		FulcrumGlow(Context context) {
			super(context == null ? Global.context : context);
			glow = this;
		}
		@Override
		protected void onDraw(Canvas canvas) {
			paint.setColor(getResources().getColor(R.color.evidenzia));
			paint.setMaskFilter(bmf);
			setLayerType(View.LAYER_TYPE_SOFTWARE, paint);
			canvas.drawRect(toPx(GLOW_SPACE - extend), toPx(GLOW_SPACE - extend),
					toPx(fulcrumView.metric.width + GLOW_SPACE + extend),
					toPx(fulcrumView.metric.height + GLOW_SPACE + extend), paint);
		}
		@Override
		public void invalidate() {
			if( printPDF ) {
				setVisibility(GONE);
			}
		}
	}

	// Node with one person or one bond
	abstract class GraphicMetric extends RelativeLayout {
		Metric metric;
		GraphicMetric(Context context, Metric metric) {
			super(context);
			this.metric = metric;
			setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
		}
	}

	// Card of a person
	class GraphicPerson extends GraphicMetric {
		ImageView background;
		GraphicPerson(Context context, PersonNode personNode) {
			super(context, personNode);
			Person person = personNode.person;
			View view = getLayoutInflater().inflate(R.layout.diagram_card, this, true);
			View border = view.findViewById(R.id.card_border);
			if( Gender.isMale(person) )
				border.setBackgroundResource(R.drawable.casella_bordo_maschio);
			else if( Gender.isFemale(person) )
				border.setBackgroundResource(R.drawable.casella_bordo_femmina);
			background = view.findViewById(R.id.card_background);
			if( personNode.isFulcrumNode() ) {
				background.setBackgroundResource(R.drawable.casella_sfondo_evidente);
				fulcrumView = this;
			} else if( personNode.acquired ) {
				background.setBackgroundResource(R.drawable.casella_sfondo_sposo);
			}
			F.unaFoto( Global.gc, person, view.findViewById( R.id.card_photo ) );
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
			String dati = U.twoDates(person, true);
			if( dati.isEmpty() ) vistaDati.setVisibility(View.GONE);
			else vistaDati.setText(dati);
			if( !U.isDead(person) )
				view.findViewById(R.id.card_mourn).setVisibility(View.GONE);
			registerForContextMenu(this);
			setOnClickListener( v -> {
				if( person.getId().equals(Global.indi) ) {
					Memoria.setPrimo( person );
					startActivity( new Intent(getContext(), Individuo.class) );
				} else {
					clickCard( person );
				}
			});
		}
		@Override
		public void invalidate() {
			// Change background color for PDF export
			if( printPDF && ((PersonNode)metric).acquired ) {
				background.setBackgroundResource(R.drawable.casella_sfondo_sposo_stampa);
			}
		}
	}

	// Marriage with eventual year and vertical line
	class GraphicBond extends GraphicMetric {
		View hearth;
		GraphicBond(Context context, Bond bond) {
			super(context, bond);
			RelativeLayout bondLayout = new RelativeLayout(context);
			//bondLayout.setBackgroundColor(0x44ff00ff);
			addView( bondLayout, new LayoutParams(toPx(bond.width), toPx(bond.height)) );
			FamilyNode familyNode = bond.familyNode;
			if( bond.marriageDate == null ) {
				hearth = new View(context);
				hearth.setBackgroundResource(R.drawable.diagram_hearth);
				int diameter = toPx(familyNode.mini ? MINI_HEARTH_DIAMETER : HEARTH_DIAMETER);
				LayoutParams hearthParams = new LayoutParams(diameter, diameter);
				hearthParams.topMargin = toPx(familyNode.centerRelY()) - diameter / 2;
				hearthParams.addRule(CENTER_HORIZONTAL);
				bondLayout.addView(hearth, hearthParams);
			} else {
				TextView year = new TextView( context );
				year.setBackgroundResource(R.drawable.diagram_year_oval);
				year.setGravity(Gravity.CENTER);
				year.setText(new Datatore(bond.marriageDate).writeDate(true));
				year.setTextSize(13f);
				LayoutParams yearParams = new LayoutParams(LayoutParams.MATCH_PARENT, toPx(MARRIAGE_HEIGHT));
				yearParams.topMargin = toPx(bond.centerRelY() - MARRIAGE_HEIGHT / 2);
				bondLayout.addView(year, yearParams);
			}
			setOnClickListener( view -> {
				Memoria.setPrimo( familyNode.spouseFamily );
				startActivity( new Intent( context, Famiglia.class ) );
			});
		}
		@Override
		public void invalidate() {
			if( printPDF && hearth != null ) {
				hearth.setBackgroundResource(R.drawable.diagram_hearth_print);
			}
		}
	}

	// Little ancestry or progeny card
	class GraphicMiniCard extends GraphicMetric {
		RelativeLayout layout;
		GraphicMiniCard(Context context, PersonNode personNode) {
			super(context, personNode);
			View miniCard = getLayoutInflater().inflate(R.layout.diagram_minicard, this, true);
			TextView miniCardText = miniCard.findViewById(R.id.minicard_text);
			miniCardText.setText(personNode.amount > 100 ? "100+" : String.valueOf(personNode.amount));
			Gender sex = Gender.getGender(personNode.person);
			if( sex == Gender.MALE )
				miniCardText.setBackgroundResource(R.drawable.casella_bordo_maschio);
			else if( sex == Gender.FEMALE )
				miniCardText.setBackgroundResource(R.drawable.casella_bordo_femmina);
			if( personNode.acquired ) {
				layout = miniCard.findViewById(R.id.minicard);
				layout.setBackgroundResource(R.drawable.casella_sfondo_sposo);
			}
			miniCard.setOnClickListener(view -> clickCard(personNode.person));
		}
		@Override
		public void invalidate() {
			if( printPDF && layout != null ) {
				layout.setBackgroundResource(R.drawable.casella_sfondo_sposo_stampa);
			}
		}
	}

	// Replacement for another person who is actually fulcrum
	class Asterisk extends GraphicMetric {
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
		List<Set<Line>> lineGroups;
		Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
		List<Path> paths = new ArrayList<>(); // Each path contains many lines
		//int[] colors = {Color.WHITE, Color.RED, Color.CYAN, Color.MAGENTA, Color.GREEN, Color.BLACK, Color.YELLOW, Color.BLUE};
		public Lines(Context context, List<Set<Line>> lineGroups, DashPathEffect effect) {
			super(context == null ? Global.context : context);
			//setBackgroundColor(0x330000ff);
			this.lineGroups = lineGroups;
			paint.setPathEffect(effect);
			paint.setStyle(Paint.Style.STROKE);
			paint.setStrokeWidth(STROKE);
		}
		@Override
		public void invalidate() {
			paint.setColor(getResources().getColor(printPDF ? R.color.lineeDiagrammaStampa : R.color.lineeDiagrammaSchermo));
			for( Path path : paths ){
				path.rewind();
			}
			float width = toPx(graph.getWidth());
			int pathNum = 0; // index of paths
			// Put the lines in one or more paths
			for( Set<Line> lineGroup : lineGroups ) {
				if( pathNum >= paths.size() )
					paths.add(new Path());
				Path path = paths.get(pathNum);
				for( Line line : lineGroup ) {
					float x1 = toPx(line.x1), y1 = toPx(line.y1), x2 = toPx(line.x2), y2 = toPx(line.y2);
					if( !leftToRight ) {
						x1 = width - x1;
						x2 = width - x2;
					}
					path.moveTo(x1, y1);
					if( line instanceof CurveLine ) {
						path.cubicTo(x1, y2, x2, y1, x2, y2);
					} else { // Horizontal or vertical line
						path.lineTo(x2, y2);
					}
				}
				pathNum++;
			}
			// Update this view size
			RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams)getLayoutParams();
			params.width = toPx(graph.getWidth());
			params.height = toPx(graph.getHeight());
			requestLayout();
		}
		@Override
		protected void onDraw(Canvas canvas) {
			if( graph.needMaxBitmap() ) {
				int maxBitmapWidth = canvas.getMaximumBitmapWidth() // is 16384 on emulators, 4096 on my physical devices
						- STROKE * 4; // the space actually occupied by the line is a little bit larger
				int maxBitmapHeight = canvas.getMaximumBitmapHeight() - STROKE * 4;
				graph.setMaxBitmap((int)toDp(maxBitmapWidth), (int)toDp(maxBitmapHeight));
			}
			// Draw the paths
			//int p = 0;
			for( Path path : paths) {
				//paint.setColor(colors[p % colors.length]);
				canvas.drawPath(path, paint);
				//p++;
			}
		}
	}

	@Override
	public void onPause() {
		super.onPause();
		if( timer != null ) {
			timer.cancel();
		}
	}

	private void clickCard(Person person) {
		timer.cancel();
		selectParentFamily(person);
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
		Global.indi = fulcrum.getId();
		Global.familyNum = whichFamily;
		graph.showFamily(Global.familyNum);
		graph.startFrom(fulcrum);
		box.removeAllViews();
		box.setAlpha(0);
		drawDiagram();
	}

	private float toDp(float pixels) {
		return pixels / density;
	}

	private int toPx(float dips) {
		return (int) (dips * density + 0.5f);
	}

	// Generate the 2 family (as child and as partner) labels for contextual menu
	static String[] getFamilyLabels(Context context, Person person, Family family) {
		String[] labels = { null, null };
		List<Family> parentFams = person.getParentFamilies(gc);
		List<Family> spouseFams = person.getSpouseFamilies(gc);
		if( parentFams.size() > 0 )
			labels[0] = spouseFams.isEmpty() ? context.getString(R.string.family)
					: context.getString(R.string.family_as, Famiglia.getRole(person, null, Relation.CHILD, true).toLowerCase());
		if( family == null && spouseFams.size() == 1 )
			family = spouseFams.get(0);
		if( spouseFams.size() > 0 )
			labels[1] = parentFams.isEmpty() ? context.getString(R.string.family)
					: context.getString(R.string.family_as, Famiglia.getRole(person, family, Relation.PARTNER, true).toLowerCase());
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
			personNode = (PersonNode)((GraphicPerson)vista).metric;
		else if( vista instanceof Asterisk )
			personNode = (PersonNode)((Asterisk)vista).metric;
		pers = personNode.person;
		if( personNode.origin != null )
			parentFam = personNode.origin.spouseFamily;
		spouseFam = personNode.spouseFamily;
		idPersona = pers.getId();
		String[] familyLabels = getFamilyLabels(getContext(), pers, spouseFam);

		if( idPersona.equals(Global.indi) && pers.getParentFamilies(gc).size() > 1 )
			menu.add(0, -1, 0, R.string.diagram);
		if( !idPersona.equals(Global.indi) )
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
		CharSequence[] parenti = {getText(R.string.parent), getText(R.string.sibling),
				getText(R.string.partner), getText(R.string.child)};
		int id = item.getItemId();
		if( id == -1 ) { // Diagramma per fulcro figlio in più famiglie
			if( pers.getParentFamilies(gc).size() > 2 ) // Più di due famiglie
				selectParentFamily(pers);
			else // Due famiglie
				completeSelect(pers, Global.familyNum == 0 ? 1 : 0);
		} else if( id == 0 ) { // Apri scheda individuo
			Memoria.setPrimo(pers);
			startActivity(new Intent(getContext(), Individuo.class));
		} else if( id == 1 ) { // Famiglia come figlio
			if( idPersona.equals(Global.indi) ) { // Se è fulcro apre direttamente la famiglia
				Memoria.setPrimo(parentFam);
				startActivity(new Intent(getContext(), Famiglia.class));
			} else
				U.qualiGenitoriMostrare(getContext(), pers, 2);
		} else if( id == 2 ) { // Famiglia come coniuge
			U.qualiConiugiMostrare(getContext(), pers,
					idPersona.equals(Global.indi) ? spouseFam : null); // Se è fulcro apre direttamente la famiglia
		} else if( id == 3 ) { // Collega persona nuova
			if( Global.settings.expert ) {
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
			if( Global.settings.expert ) {
				DialogFragment dialog = new NuovoParente(pers, parentFam, spouseFam, false, Diagram.this);
				dialog.show(getActivity().getSupportFragmentManager(), "scegli");
			} else {
				new AlertDialog.Builder(getContext()).setItems(parenti, (dialog, quale) -> {
					Intent intento = new Intent(getContext(), Principal.class);
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
			U.salvaJson(true, (Object[])modificateArr);
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
				printPDF = true;
				for( int i = 0; i < box.getChildCount(); i++ ) {
					box.getChildAt(i).invalidate();
				}
				fulcrumView.findViewById(R.id.card_background).setBackgroundResource(R.drawable.casella_sfondo_base);
				// Create PDF
				PdfDocument document = new PdfDocument();
				PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(box.getWidth(), box.getHeight(), 1).create();
				PdfDocument.Page page = document.startPage( pageInfo );
				box.draw( page.getCanvas() );
				document.finishPage(page);
				printPDF = false;
				// Write PDF
				Uri uri = data.getData();
				try {
					OutputStream out = getContext().getContentResolver().openOutputStream(uri);
					document.writeTo(out);
					out.flush();
					out.close();
				} catch( Exception e ) {
					Toast.makeText(getContext(), e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
					return;
				}
				Toast.makeText(getContext(), R.string.pdf_exported_ok, Toast.LENGTH_LONG).show();
			}
		}
	}
}
