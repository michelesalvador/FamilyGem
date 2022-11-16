package app.familygem;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Speech bubble with a hint appearing above the FAB
 * */
public class SpeechBubble {

	private final View bubble;

	public SpeechBubble(Context context, int textId) {
		this(context, context.getString(textId));
	}

	public SpeechBubble(Context context, String testo) {
		Activity activity = (Activity)context;
		bubble = activity.getLayoutInflater().inflate(R.layout.fabuloso, null);
		bubble.setVisibility(View.INVISIBLE);
		((LinearLayout)activity.findViewById(R.id.fab_box)).addView(bubble, 0,
				new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
		((TextView) bubble.findViewById(R.id.fabuloso_text)).setText(testo);
		bubble.setOnTouchListener((view, event) -> {
			hide();
			return true;
		});
		activity.findViewById(R.id.fab).setOnTouchListener((view, event) -> {
			hide();
			//view.performClick();
			return false; // To execute click later
		});
	}

	public void show() {
		new Handler( Looper.myLooper()).postDelayed( () -> { // appears after one second
			bubble.setVisibility( View.VISIBLE );
		}, 1000);
	}

	public void hide() {
		bubble.setVisibility( View.INVISIBLE );
	}
}
