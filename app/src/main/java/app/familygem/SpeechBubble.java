package app.familygem;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Balloon with a hint that appears above the FAB.
 */
public class SpeechBubble {

    private final View balloon;

    public SpeechBubble(Context context, int textId) {
        this(context, context.getString(textId));
    }

    public SpeechBubble(Context context, String testo) {
        Activity attivita = (Activity)context;
        balloon = attivita.getLayoutInflater().inflate(R.layout.fabuloso, null);
        balloon.setVisibility(View.INVISIBLE);
        ((LinearLayout)attivita.findViewById(R.id.fab_box)).addView(balloon, 0,
                new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        ((TextView)balloon.findViewById(R.id.fabuloso_text)).setText(testo);
        balloon.setOnTouchListener((vista, evento) -> {
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
        new Handler(Looper.myLooper()).postDelayed(() -> { // compare dopo un secondo
            balloon.setVisibility(View.VISIBLE);
        }, 1000);
    }

    public void hide() {
        balloon.setVisibility(View.INVISIBLE);
    }
}
