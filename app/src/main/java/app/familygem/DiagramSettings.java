package app.familygem;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.os.Bundle;
import android.view.View;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;

public class DiagramSettings extends AppCompatActivity {

	SeekBar ancestors;
	SeekBar uncles;
	TextView indicator;
	AnimatorSet anima;

	@Override
	protected void onCreate( Bundle bandolo ) {
		super.onCreate( bandolo );
		setContentView( R.layout.diagram_settings );
		indicator = findViewById( R.id.settings_indicator );
		indicator.setBackground( AppCompatResources.getDrawable(this, R.drawable.segnalino) );

		// Number of ancestors
		ancestors = findViewById( R.id.settings_ancestors );
		ancestors.setProgress( Globale.preferenze.diagram.ancestors );
		ancestors.setOnSeekBarChangeListener( new SeekBar.OnSeekBarChangeListener() {
			@Override
			public void onProgressChanged( SeekBar seekBar, int i, boolean b ) {
				if( i < uncles.getProgress() ) {
					uncles.setProgress( i );
					Globale.preferenze.diagram.uncles = i;
				}
				indicator(seekBar);
			}
			@Override
			public void onStartTrackingTouch( SeekBar seekBar ) {}
			@Override
			public void onStopTrackingTouch( SeekBar seekBar ) {
				Globale.preferenze.diagram.ancestors = seekBar.getProgress();
				salva();
			}
		});

		// Number of uncles, linked to ancestors
		uncles = findViewById( R.id.settings_uncles );
		uncles.setProgress( Globale.preferenze.diagram.uncles );
		uncles.setOnSeekBarChangeListener( new SeekBar.OnSeekBarChangeListener() {
			@Override
			public void onProgressChanged( SeekBar seekBar, int i, boolean b ) {
				if( i > ancestors.getProgress() ) {
					ancestors.setProgress( i );
					Globale.preferenze.diagram.ancestors = i;
				}
				indicator(seekBar);
			}
			@Override
			public void onStartTrackingTouch( SeekBar seekBar ) {}
			@Override
			public void onStopTrackingTouch( SeekBar seekBar ) {
				Globale.preferenze.diagram.uncles = seekBar.getProgress();
				salva();
			}
		});

		// Display siblings
		Switch siblings = findViewById( R.id.settings_siblings );
		siblings.setChecked( Globale.preferenze.diagram.siblings );
		siblings.setOnCheckedChangeListener( (button, active) -> {
			Globale.preferenze.diagram.siblings = active;
			salva();
		});

		// Number of descendants
		SeekBar descendants = findViewById( R.id.settings_descendants );
		descendants.setProgress( Globale.preferenze.diagram.descendants );
		descendants.setOnSeekBarChangeListener( new SeekBar.OnSeekBarChangeListener() {
			@Override
			public void onProgressChanged( SeekBar seekBar, int i, boolean b ) {
				indicator(seekBar);
			}
			@Override
			public void onStartTrackingTouch( SeekBar seekBar ) {}
			@Override
			public void onStopTrackingTouch( SeekBar seekBar ) {
				Globale.preferenze.diagram.descendants = seekBar.getProgress();
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
		indicator.setText( String.valueOf(i) );
		int width = seekBar.getWidth() - seekBar.getPaddingLeft() - seekBar.getPaddingRight();
		float x = (seekBar.getX() + seekBar.getPaddingLeft() + width / 5f * i) - indicator.getWidth() / 2f ;
		indicator.setX( x );
		indicator.setY(seekBar.getY() - indicator.getHeight());
		anima.cancel();
		anima.start();
	}

	private void salva() {
		Globale.preferenze.salva();
		Globale.editato = true;
	}
}
