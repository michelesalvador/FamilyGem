// Fumetto con un suggerimento che compare sopra al FAB

package app.familygem;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

public class SpeechBubble {

	private final View baloon;

	public SpeechBubble(Context context, int textId) {
		this(context, context.getString(textId));
	}

	public SpeechBubble(Context context, String testo) {
		Activity attivita = (Activity)context;
		baloon = attivita.getLayoutInflater().inflate(R.layout.fabuloso, null);
		baloon.setVisibility(View.INVISIBLE);
		((LinearLayout)attivita.findViewById(R.id.fab_box)).addView(baloon, 0,
				new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
		((TextView)baloon.findViewById(R.id.fabuloso_text)).setText(testo);
		baloon.setOnTouchListener((vista, evento) -> {
			hide();
			return true;
		});
		attivita.findViewById(R.id.fab).setOnTouchListener((vista, evento) -> {
			hide();
			//vista.performClick();
			return false; // Per eseguire il click dopo
		});
	}

	public void show() {
		new Handler( Looper.myLooper()).postDelayed( () -> { // compare dopo un secondo
			baloon.setVisibility( View.VISIBLE );
		}, 1000);
	}

	public void hide() {
		baloon.setVisibility( View.INVISIBLE );
	}
}
