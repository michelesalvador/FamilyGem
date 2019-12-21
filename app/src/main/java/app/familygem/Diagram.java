package app.familygem;

import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import com.otaliastudios.zoom.ZoomLayout;
import org.folg.gedcom.model.Person;
import graph.gedcom.Card;
import graph.gedcom.Graph;
import graph.gedcom.Node;

public class Diagram extends Fragment {

	private Graph diagram;
	ZoomLayout zoomBox;
	RelativeLayout box;
	View fulcrumCard;
	float zoomValue;

	@Override
	public void onCreate( Bundle state ) {
		super.onCreate( state );
	}

	@Override
	public View onCreateView( LayoutInflater inflater, ViewGroup container, Bundle state) {

		((AppCompatActivity) getActivity()).getSupportActionBar().setTitle( Globale.preferenze.alberoAperto().nome );
		final View view = inflater.inflate( R.layout.diagram, container, false );

		zoomBox = view.findViewById( R.id.diagram_zoom );
		//zoomBox.setMinZoom( 1, ZoomApi.TYPE_ZOOM );
		box = view.findViewById( R.id.diagram_box );

		// Create a diagram model
		diagram = new Graph( Globale.gc );
		diagram.maxAncestors(2)
				.showFamily( getActivity().getIntent().getIntExtra("genitoriNum", 0) )
				.startFrom(Globale.individuo);
		getActivity().getIntent().putExtra( "genitoriNum", 0 ); // lo resetta per gli altri che hanno una sola parent family
		drawDiagram();
		return view;
	}

	void drawDiagram() {

		// Place graphic cards layouts in the box taking them from the list of cards
		for( Card card : diagram.getCards() ) {
			DiagramCard diagramCard = new DiagramCard(getContext(), card);
			box.addView( diagramCard );
		}

		box.post(new Runnable() {
			@Override
			public void run() {
				// Set dimensions of each graphic card
				for (int i = 0; i < box.getChildCount(); i++) {
					DiagramCard diagramCard = (DiagramCard) box.getChildAt(i);
					//s.l( diagramCard.getLayout().getWidth() );
					diagramCard.card.width = diagramCard.getWidth();
					diagramCard.card.height = diagramCard.getHeight();
				}

				// Let the diagram calculate positions of Nodes and Lines
				diagram.arrange();

				// Final position of the cards
				for (int i = 0; i < box.getChildCount(); i++) {
					DiagramCard diagramCard = (DiagramCard) box.getChildAt(i);
					RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) diagramCard.getLayoutParams();
					params.leftMargin = diagramCard.card.x;
					params.topMargin = diagramCard.card.y;
					diagramCard.setLayoutParams(params);
				}

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

	/*class DiagramNode extends RelativeLayout {
		String id;
		DiagramNode( Context context, Node node ) {
			super(context);
			id = egli.getId();
		}
	}*/

	public class DiagramCard extends LinearLayout {

		Card card;

		public DiagramCard( Context context, final Card card ) {
			super(context);
			this.card = card;
			Person person = card.getPerson();

			setLayoutParams( new LinearLayout.LayoutParams( LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT ) );
			setOrientation( LinearLayout.VERTICAL );
			setGravity( Gravity.CENTER_HORIZONTAL );
			View view = getLayoutInflater().inflate( R.layout.diagram_card, this, false );

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
			addView( view );
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
						if( U.qualiGenitoriMostrare(getContext(), person, Principe.class) )
							return;
						zoomValue = zoomBox.getRealZoom();
						box.removeAllViews();
						Globale.individuo = person.getId();
						diagram.restartFrom(Globale.individuo);
						drawDiagram();
					}
				}
			});
		}
	}
}
