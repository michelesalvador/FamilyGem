package app.familygem;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import com.otaliastudios.zoom.ZoomLayout;

public class DiagrammaActivity extends AppCompatActivity {

	RelativeLayout scatolona;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.diagramma_activity);

		scatolona = findViewById( R.id.diagramma_scatolona );

		scatolona.post( new Runnable() {
			public void run() {

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
				RelativeLayout.LayoutParams paramLinea = new RelativeLayout.LayoutParams( scatolona.getWidth(), scatolona.getHeight() );
				//RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT );
				//paramLinea.leftMargin = 100;
				//paramLinea.topMargin = 100;
				View linea1 = new Linea( genitori.getLeft()+anno.getLeft()+anno.getWidth()/2, genitori.getTop()+anno.getBottom(),
						genitori.getTop() + genitori.getHeight(),
						figli.getLeft()+schedina1.getLeft()+schedina1.getWidth()/2, figli.getTop()+schedina1.getTop() );
				//linea1.setId( generateViewId() );
				scatolona.addView( linea1, paramLinea );

				View linea2 = new Linea( genitori.getLeft()+anno.getLeft()+anno.getWidth()/2, genitori.getTop()+anno.getBottom(),
						genitori.getTop() + genitori.getHeight(),
						figli.getLeft()+schedina2.getLeft()+schedina2.getWidth()/2, figli.getTop()+schedina2.getTop() );
				scatolona.addView( linea2, paramLinea );

				disegnaLinea( anno, genitori, findViewById(R.id.scheda0) );
				disegnaLinea( anno, genitori, findViewById(R.id.scheda3) );
			}
		});
	}

	public void muovi( View vista ) {
		ZoomLayout zoom = findViewById( R.id.diagramma_zoom );
		Rect margini = new Rect();
		vista.getDrawingRect( margini );
		scatolona.offsetDescendantRectToMyCoords( vista, margini );
		s.l(  scatolona.getWidth() +" x "+ scatolona.getHeight() +"\n"+
				margini.exactCenterX() +"  "+ margini.exactCenterY() +"\n"+
				zoom.getZoom() +"  "+ zoom.getRealZoom()  );
		zoom.panTo( -margini.exactCenterX() + zoom.getWidth()/zoom.getRealZoom()/2, -margini.exactCenterY() + zoom.getHeight()/zoom.getRealZoom()/2, true );
	}


	void disegnaLinea( View vistaInizio, View vistaMezzo, View vistaFine ) {
		// Coordinate assolute start
		Rect marginiInizio = new Rect();
		vistaInizio.getDrawingRect( marginiInizio );
		scatolona.offsetDescendantRectToMyCoords( vistaInizio, marginiInizio );
		// mezzo
		Rect marginiMezzo = new Rect();
		vistaMezzo.getDrawingRect( marginiMezzo );
		scatolona.offsetDescendantRectToMyCoords( vistaMezzo, marginiMezzo );
		// e end
		Rect marginiFine = new Rect();
		vistaFine.getDrawingRect( marginiFine );
		scatolona.offsetDescendantRectToMyCoords( vistaFine, marginiFine );
		View linea = new Linea( (int)marginiInizio.exactCenterX(), marginiInizio.bottom,
				marginiMezzo.bottom,
				(int)marginiFine.exactCenterX(), marginiFine.top );
		RelativeLayout.LayoutParams paramLinea = new RelativeLayout.LayoutParams( scatolona.getWidth(), scatolona.getHeight() );
		scatolona.addView( linea, paramLinea );

	}

	class Linea extends View {
		Paint paint = new Paint();
		Path path = new Path();
		int xInizio, yInizio, yMezzo, xFine, yFine;
		/*public Linea( int x1, int y1, int x2, int y2 ) {
			super( Globale.contesto );
			xInizio = x1;
			yInizio = y1;
			xFine = x2;
			yFine = y2;
		}*/
		public Linea( int x1, int y1, int y2, int x3, int y3 ) {
			super( Globale.contesto );
			xInizio = x1;
			yInizio = y1;
			yMezzo = y2;
			xFine = x3;
			yFine = y3;
		}
		@Override
		protected void onDraw( Canvas canvas ) {
			paint.setStyle( Paint.Style.STROKE );
			paint.setColor( Color.WHITE );
			paint.setStrokeWidth(3);
			path.moveTo( xInizio, yInizio );	// partenza necessario per evitare il ricciolo finale (?)
			//path.cubicTo( xInizio, yFine, xFine, yInizio, xFine, yFine );	// linea curva
			//int mezzAlto = (yFine - yInizio) / 2;
			path.lineTo( xInizio, yMezzo );
			path.lineTo( xFine, yMezzo );
			path.lineTo( xFine, yFine );
			canvas.drawPath( path, paint );
		}
	}

}
