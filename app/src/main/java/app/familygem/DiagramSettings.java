package app.familygem;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

public class DiagramSettings extends AppCompatActivity {

	SeekBar ancestors;
	SeekBar uncles;
	SeekBar siblings;
	SeekBar cousins;
	LinearLayout indicator;
	AnimatorSet anima;

	@Override
	protected void onCreate( Bundle bandolo ) {
		super.onCreate( bandolo );
		setContentView( R.layout.diagram_settings );
		indicator = findViewById( R.id.settings_indicator );

		// Number of ancestors
		ancestors = findViewById(R.id.settings_ancestors);
		ancestors.setProgress(decodifica(Global.settings.diagram.ancestors));
		ancestors.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
			@Override
			public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
				if( i < uncles.getProgress() ) {
					uncles.setProgress(i);
					Global.settings.diagram.uncles = converti(i);
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
				Global.settings.diagram.ancestors = converti(seekBar.getProgress());
				salva();
			}
		});

		// Number of uncles, linked to ancestors
		uncles = findViewById(R.id.settings_great_uncles);
		uncles.setProgress(decodifica(Global.settings.diagram.uncles));
		uncles.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
			@Override
			public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
				if( i > ancestors.getProgress() ) {
					ancestors.setProgress(i);
					Global.settings.diagram.ancestors = converti(i);
				}
				indicator(seekBar);
			}
			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {}
			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {
				Global.settings.diagram.uncles = converti(seekBar.getProgress());
				salva();
			}
		});

		// Display siblings
		SwitchCompat spouses = findViewById(R.id.settings_spouses);
		spouses.setChecked(Global.settings.diagram.spouses);
		spouses.setOnCheckedChangeListener((button, active) -> {
			Global.settings.diagram.spouses = active;
			salva();
		});

		// Number of descendants
		SeekBar descendants = findViewById(R.id.settings_descendants);
		descendants.setProgress(decodifica(Global.settings.diagram.descendants));
		descendants.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
			@Override
			public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
				indicator(seekBar);
			}
			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {}
			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {
				Global.settings.diagram.descendants = converti(seekBar.getProgress());
				salva();
			}
		});

		// Number of siblings and nephews
		siblings = findViewById(R.id.settings_siblings_nephews);
		siblings.setProgress(decodifica(Global.settings.diagram.siblings));
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
				Global.settings.diagram.siblings = converti(seekBar.getProgress());
				salva();
			}
		});

		// Number of uncles and cousins, linked to ancestors
		cousins = findViewById(R.id.settings_uncles_cousins);
		cousins.setProgress(decodifica(Global.settings.diagram.cousins));
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
				Global.settings.diagram.cousins = converti(seekBar.getProgress());
				salva();
			}
		});

		ObjectAnimator alphaIn = ObjectAnimator.ofFloat(indicator, View.ALPHA, 1);
		alphaIn.setDuration(0);
		ObjectAnimator alphaOut = ObjectAnimator.ofFloat(indicator, View.ALPHA, 1, 0);
		alphaOut.setStartDelay(2000);
		alphaOut.setDuration(500);
		anima = new AnimatorSet();
		anima.play(alphaIn);
		anima.play(alphaOut).after(alphaIn);
		indicator.setAlpha(0);
	}

	private void indicator(SeekBar seekBar) {
		int i = seekBar.getProgress();
		((TextView)indicator.findViewById(R.id.settings_indicator_text)).setText(String.valueOf(converti(i)));
		int width = seekBar.getWidth() - seekBar.getPaddingLeft() - seekBar.getPaddingRight();
		float x = (seekBar.getX() + seekBar.getPaddingLeft() + width / 9f * i) - indicator.getWidth() / 2f;
		indicator.setX(x);
		indicator.setY(seekBar.getY() - indicator.getHeight());
		anima.cancel();
		anima.start();
	}

	// Valore dalle preferenze (1 2 3 4 5 10 20 50 100) alla scala lineare (1 2 3 4 5 6 7 8 9)
	private int decodifica( int i ) {
		if( i == 100 ) return 9;
		else if( i == 50 ) return 8;
		else if( i == 20 ) return 7;
		else if( i == 10 ) return 6;
		else return i;
	}

	// Valore delle scala lineare a quella esagerata
	private int converti( int i ) {
		if( i == 6 ) return 10;
		else if( i == 7 ) return 20;
		else if( i == 8 ) return 50;
		else if( i == 9 ) return 100;
		else return i;
	}

	private void salva() {
		Global.settings.save();
		Global.edited = true;
	}
}
