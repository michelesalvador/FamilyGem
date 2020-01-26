package app.familygem;

import android.graphics.Rect;
import android.os.Bundle;
import android.view.View;
import android.widget.RelativeLayout;
import androidx.appcompat.app.AppCompatActivity;
import com.otaliastudios.zoom.ZoomLayout;

public class Diagram extends AppCompatActivity {

	RelativeLayout box;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.diagram );
		box = findViewById( R.id.diagram_box );

		box.post( new Runnable() {
			public void run() {
/*
				// coordinate di una schedina
				LinearLayout genitori = findViewById( R.id.genitori );
				LinearLayout figli = findViewById( R.id.figli );
				View anno = findViewById( R.id.anno0);
				View schedina1 = findViewById( R.id.scheda1 );
				View schedina2 = findViewById( R.id.scheda2 );
				Log.d("Pisello schedina", anno.getBottom()
						+"\n"+ schedina1.getLeft() + "  "+ anno.getRight()
						+"\n"+ schedina1.getWidth() +"  "+ anno.getMeasuredWidth() );

				// Linea curva
				RelativeLayout.LayoutParams paramLinea = new RelativeLayout.LayoutParams( box.getWidth(), box.getHeight() );
				View linea1 = new Linea( genitori.getLeft()+anno.getLeft()+anno.getWidth()/2, genitori.getTop()+anno.getBottom(),
						genitori.getTop() + genitori.getHeight(),
						figli.getLeft()+schedina1.getLeft()+schedina1.getWidth()/2, figli.getTop()+schedina1.getTop() );
				//linea1.setId( generateViewId() );
				box.addView( linea1, paramLinea );

				View linea2 = new Linea( genitori.getLeft()+anno.getLeft()+anno.getWidth()/2, genitori.getTop()+anno.getBottom(),
						genitori.getTop() + genitori.getHeight(),
						figli.getLeft()+schedina2.getLeft()+schedina2.getWidth()/2, figli.getTop()+schedina2.getTop() );
				box.addView( linea2, paramLinea );

				disegnaLinea( anno, genitori, findViewById(R.id.scheda0) );*/
			}
		});
	}

	public void muovi( View vista ) {
		ZoomLayout zoom = findViewById( R.id.diagram_zoom );
		Rect margini = new Rect();
		vista.getDrawingRect( margini );
		box.offsetDescendantRectToMyCoords( vista, margini );
		s.l(  box.getWidth() +" x "+ box.getHeight() +"\n"+
				margini.exactCenterX() +"  "+ margini.exactCenterY() +"\n"+
				zoom.getZoom() +"  "+ zoom.getRealZoom()  );
		zoom.panTo( -margini.exactCenterX() + zoom.getWidth()/zoom.getRealZoom()/2, -margini.exactCenterY() + zoom.getHeight()/zoom.getRealZoom()/2, true );
		//startActivity( new Intent( Diagram.this, Officina.class ) );
		Officina.listaAttivita( this );
	}
}
