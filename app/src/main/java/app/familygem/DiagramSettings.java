package app.familygem;

import android.os.Bundle;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class DiagramSettings extends AppCompatActivity {

	TextView indicator;
	SeekBar ancestors;
	SeekBar uncles;

	@Override
	protected void onCreate( Bundle bandolo ) {
		super.onCreate( bandolo );
		setContentView( R.layout.diagram_settings );
		indicator = findViewById( R.id.settings_indicator );

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
				indicator(seekBar, i);
			}
			@Override
			public void onStartTrackingTouch( SeekBar seekBar ) {
				indicator.setVisibility( View.VISIBLE );
			}
			@Override
			public void onStopTrackingTouch( SeekBar seekBar ) {
				indicator.setVisibility( View.GONE );
				Globale.preferenze.diagram.ancestors = seekBar.getProgress();
				Globale.preferenze.salva();
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
				indicator(seekBar, i);
			}
			@Override
			public void onStartTrackingTouch( SeekBar seekBar ) {
				indicator.setVisibility( View.VISIBLE );
			}
			@Override
			public void onStopTrackingTouch( SeekBar seekBar ) {
				indicator.setVisibility( View.GONE );
				Globale.preferenze.diagram.uncles = seekBar.getProgress();
				Globale.preferenze.salva();
			}
		});

		// Display siblings
		Switch siblings = findViewById( R.id.settings_siblings );
		siblings.setChecked( Globale.preferenze.diagram.siblings );
		siblings.setOnCheckedChangeListener( new CompoundButton.OnCheckedChangeListener() {
			public void onCheckedChanged( CompoundButton button, boolean active ) {
				Globale.preferenze.diagram.siblings = active;
				Globale.preferenze.salva();
			}
		});

		// Number of descendants
		SeekBar descendants = findViewById( R.id.settings_descendants );
		descendants.setProgress( Globale.preferenze.diagram.descendants );
		descendants.setOnSeekBarChangeListener( new SeekBar.OnSeekBarChangeListener() {
			@Override
			public void onProgressChanged( SeekBar seekBar, int i, boolean b ) {
				indicator(seekBar, i);
			}
			@Override
			public void onStartTrackingTouch( SeekBar seekBar ) {
				indicator.setVisibility( View.VISIBLE );
			}
			@Override
			public void onStopTrackingTouch( SeekBar seekBar ) {
				indicator.setVisibility( View.GONE );
				Globale.preferenze.diagram.descendants = seekBar.getProgress();
				Globale.preferenze.salva();
			}
		});
	}

	private void indicator(SeekBar seekBar, int i) {
		int val = (i * (seekBar.getWidth() - 2 * seekBar.getThumbOffset())) / seekBar.getMax();
		indicator.setText("" + i);
		indicator.setX(seekBar.getX() + val + seekBar.getThumbOffset() / 2);
		indicator.setY(seekBar.getY() - indicator.getHeight() - 10);
	}
}
