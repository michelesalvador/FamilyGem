package app.familygem;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Bundle;
import android.view.View;
import android.widget.RelativeLayout;
import java.util.ArrayList;
import java.util.List;

/*  Il path non deve superare una certa larghezza/altezza max, che però è differente in diversi device...
	credo sia multipli di 2048, in tutti gli emulatori è 16384
	lo dice il log: "Shape too large to be rendered into a texture (18436x2052, max=16384x16384)"
	Nei sistemi con Android da 5 indietro dice solo: "Path too large to be rendered into a texture"
	senza specificare il max, che però negli emulatori è sempre 16384.
	Invece nel mio L90 (che ha Android 5.0.2) anche se non lo dice ho dedotto che è il max è 4096.
	Anche nel cellu di Ari con Android 4.4.2 ho dedotto che il max è 4096.
	Il problema è ricavare la dimensione massima di un path specificatamente di ogni device.
	La soluzione è ricavare la dimensione massima di un path da 'canvas.getMaximumBitmapWidth()'
 */

public class BigPaths extends AppCompatActivity {

	final int basic = 2048; // la dimensione modulare minima di un path
	                        // le dimensioni massime di un path sono multipli di questo numero
	final int tot = 3; // numero totale di path disegnati
	RelativeLayout box;

	@Override
	protected void onCreate( Bundle savedInstanceState ) {
		super.onCreate( savedInstanceState );
		setContentView( R.layout.big_paths );
		box = findViewById( R.id.box );
		RelativeLayout.LayoutParams paramLines = new RelativeLayout.LayoutParams( basic * tot, basic );
		box.addView( new Lines(this), paramLines );
	}

	class Lines extends View {
		Paint paint = new Paint(); // Paint.ANTI_ALIAS_FLAG
		List<Path> paths = new ArrayList<>();
		public Lines( Context context ) {
			super(context);
			for( int i = 0; i < tot; i++ ) {
				paths.add( new Path() );
			}
		}
		@Override
		protected void onDraw( Canvas canvas) {
			s.l(canvas.isHardwareAccelerated(), canvas.getMaximumBitmapWidth(), canvas.getMaximumBitmapHeight() );
			for( int i = 0; i < tot; i++ ) {
				float x1 = 0, y1 = 0, x2 = basic * i, y2 = basic;
				paths.get(i).moveTo( x1, y1 );
				paths.get(i).cubicTo( x1, y2, x2, y1, x2, y2 );
				s.l("PATH", x2);
			}
			paint.setStyle( Paint.Style.STROKE );
			paint.setColor( Color.BLACK );
			//paint.setStrokeWidth(4);
			for( Path path : paths ) {
				canvas.drawPath( path, paint );
			}
		}
	}
}