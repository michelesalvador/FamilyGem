package app.familygem;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.text.TextUtilsCompat;
import androidx.core.view.ViewCompat;
import java.util.Locale;

public class DiagramSettings extends BaseActivity {

	private SeekBar ancestors;
	private SeekBar uncles;
	private SeekBar siblings;
	private SeekBar cousins;
	private LinearLayout indicator;
	private AnimatorSet anim;
	private final boolean leftToRight = TextUtilsCompat.getLayoutDirectionFromLocale(Locale.getDefault()) == ViewCompat.LAYOUT_DIRECTION_LTR;

	@Override
	protected void onCreate( Bundle bundle ) {
		super.onCreate( bundle );
		setContentView( R.layout.diagram_settings );
		indicator = findViewById( R.id.settings_indicator );

		// Number of ancestors
		ancestors = findViewById(R.id.settings_ancestors);
		ancestors.setProgress(decode(Global.settings.diagram.ancestors));
		ancestors.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
			@Override
			public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
				if( i < uncles.getProgress() ) {
					uncles.setProgress(i);
					Global.settings.diagram.uncles = convert(i);
				}
				if( i == 0 && siblings.getProgress() > 0 ) {
					siblings.setProgress(0);
					Global.settings.diagram.siblings = 0;
				}
				if( i == 0 && cousins.getProgress() > 0 ) {
					cousins.setProgress(0);
					Global.settings.diagram.cousins = 0;
				}
				indicator(seekBar);
			}
			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {}
			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {
				Global.settings.diagram.ancestors = convert(seekBar.getProgress());
				save();
			}
		});

		// Number of uncles, linked to ancestors
		uncles = findViewById(R.id.settings_great_uncles);
		uncles.setProgress(decode(Global.settings.diagram.uncles));
		uncles.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
			@Override
			public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
				if( i > ancestors.getProgress() ) {
					ancestors.setProgress(i);
					Global.settings.diagram.ancestors = convert(i);
				}
				indicator(seekBar);
			}
			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {}
			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {
				Global.settings.diagram.uncles = convert(seekBar.getProgress());
				save();
			}
		});

		// Display siblings
		SwitchCompat spouses = findViewById(R.id.settings_spouses);
		spouses.setChecked(Global.settings.diagram.spouses);
		spouses.setOnCheckedChangeListener((button, active) -> {
			Global.settings.diagram.spouses = active;
			save();
		});

		// Number of descendants
		SeekBar descendants = findViewById(R.id.settings_descendants);
		descendants.setProgress(decode(Global.settings.diagram.descendants));
		descendants.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
			@Override
			public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
				indicator(seekBar);
			}
			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {}
			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {
				Global.settings.diagram.descendants = convert(seekBar.getProgress());
				save();
			}
		});

		// Number of siblings and nephews
		siblings = findViewById(R.id.settings_siblings_nephews);
		siblings.setProgress(decode(Global.settings.diagram.siblings));
		siblings.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
			@Override
			public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
				if( i > 0 && ancestors.getProgress() == 0 ) {
					ancestors.setProgress(1);
					Global.settings.diagram.ancestors = 1;
				}
				indicator(seekBar);
			}
			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {}
			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {
				Global.settings.diagram.siblings = convert(seekBar.getProgress());
				save();
			}
		});

		// Number of uncles and cousins, linked to ancestors
		cousins = findViewById(R.id.settings_uncles_cousins);
		cousins.setProgress(decode(Global.settings.diagram.cousins));
		cousins.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
			@Override
			public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
				if( i > 0 && ancestors.getProgress() == 0 ) {
					ancestors.setProgress(1);
					Global.settings.diagram.ancestors = 1;
				}
				indicator(seekBar);
			}
			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {}
			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {
				Global.settings.diagram.cousins = convert(seekBar.getProgress());
				save();
			}
		});

		ObjectAnimator alphaIn = ObjectAnimator.ofFloat(indicator, View.ALPHA, 1);
		alphaIn.setDuration(0);
		ObjectAnimator alphaOut = ObjectAnimator.ofFloat(indicator, View.ALPHA, 1, 0);
		alphaOut.setStartDelay(2000);
		alphaOut.setDuration(500);
		anim = new AnimatorSet();
		anim.play(alphaIn);
		anim.play(alphaOut).after(alphaIn);
		indicator.setAlpha(0);
	}

	private void indicator(SeekBar seekBar) {
		int i = seekBar.getProgress();
		((TextView)indicator.findViewById(R.id.settings_indicator_text)).setText(String.valueOf(convert(i)));
		int width = seekBar.getWidth() - seekBar.getPaddingLeft() - seekBar.getPaddingRight();
		float x;
		if( leftToRight )
			x = seekBar.getX() + seekBar.getPaddingLeft() + width / 9f * i - indicator.getWidth() / 2f;
		else
			x = seekBar.getX() + seekBar.getWidth() + seekBar.getPaddingRight() - width / 9f * (i + 1) - indicator.getWidth() / 2f;
		indicator.setX(x);
		indicator.setY(seekBar.getY() - indicator.getHeight());
		anim.cancel();
		anim.start();
	}

	/**
	 * Value from preferences (1 2 3 4 5 10 20 50 100) to linear scale (1 2 3 4 5 6 7 8 9)
	 * */
	private int decode(int i ) {
		if( i == 100 ) return 9;
		else if( i == 50 ) return 8;
		else if( i == 20 ) return 7;
		else if( i == 10 ) return 6;
		else return i;
	}

	/**
	 * Linear scale value to exaggerated (scaled?) one
	 * */
	private int convert(int i ) {
		if( i == 6 ) return 10;
		else if( i == 7 ) return 20;
		else if( i == 8 ) return 50;
		else if( i == 9 ) return 100;
		else return i;
	}

	private void save() {
		Global.settings.save();
		Global.edited = true;
	}
}
