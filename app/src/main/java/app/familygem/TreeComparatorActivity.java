package app.familygem;

import android.content.Intent;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.cardview.widget.CardView;
import org.folg.gedcom.model.Change;
import org.folg.gedcom.model.Family;
import org.folg.gedcom.model.Gedcom;
import org.folg.gedcom.model.Media;
import org.folg.gedcom.model.Note;
import org.folg.gedcom.model.Person;
import org.folg.gedcom.model.Repository;
import org.folg.gedcom.model.Source;
import org.folg.gedcom.model.Submitter;

/**
 * Activity to evaluate a record of the imported tree, with possible comparison with the corresponding record of the old tree
 * */
public class TreeComparatorActivity extends BaseActivity {

	Class clazz; // the ruling class of the activity
	int destiny;

	@Override
	protected void onCreate( Bundle bandolo ) {
		super.onCreate( bandolo );
		setContentView( R.layout.confronto );

		if( Comparison.getList().size() > 0 ) {

			int max;
			int position;
			if( Comparison.get().autoContinue) {
				max = Comparison.get().numChoices;
				position = Comparison.get().choicesMade;
			} else {
				max = Comparison.getList().size();
				position = getIntent().getIntExtra("posizione",0);
			}
			ProgressBar progressBar = findViewById( R.id.confronto_progresso );
			progressBar.setMax( max );
			progressBar.setProgress( position );
			((TextView)findViewById( R.id.confronto_stato )).setText( position+"/"+max );

			final Object o = Comparison.getFront(this).object;
			final Object o2 = Comparison.getFront(this).object2;
			if( o != null ) clazz = o.getClass();
			else clazz = o2.getClass();
			setupCard( Global.gc, R.id.confronto_vecchio, o );
			setupCard( Global.gc2, R.id.confronto_nuovo, o2 );

			destiny = 2;

			Button okButton = findViewById(R.id.confronto_bottone_ok);
			okButton.setBackground( AppCompatResources.getDrawable(getApplicationContext(),R.drawable.frecciona) );
			if( o == null ) {
				destiny = 1;
				okButton.setText( R.string.add );
				okButton.setBackgroundColor( 0xff00dd00 ); // getResources().getColor(R.color.evidenzia)
				okButton.setHeight( 30 ); // ineffective TODO this does not meet Material design guidelines for 48x48 dp touch targets
			} else if( o2 == null ) {
				destiny = 3;
				okButton.setText( R.string.delete );
				okButton.setBackgroundColor( 0xffff0000 );
			} else if( Comparison.getFront(this).canBothAddAndReplace) {
				// Another Add button
				Button addButton = new Button( this );
				addButton.setTextSize( TypedValue.COMPLEX_UNIT_SP,16 );
				addButton.setTextColor( 0xFFFFFFFF );
				LinearLayout.LayoutParams params = new LinearLayout.LayoutParams( LinearLayout.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT );
				params.rightMargin = 15;
				params.weight = 3;
				addButton.setLayoutParams( params );
				addButton.setText( R.string.add );
				addButton.setBackgroundColor( 0xff00dd00 );
				addButton.setOnClickListener( v -> {
					Comparison.getFront(this).destiny = 1;
					continueToNext();
				});
				(( LinearLayout)findViewById( R.id.confronto_bottoni )).addView( addButton, 1 );
			}

			// Continues automatically if there is no double action to choose
			if( Comparison.get().autoContinue && !Comparison.getFront(this).canBothAddAndReplace) {
				Comparison.getFront(this).destiny = destiny;
				continueToNext();
			}

			// Button to accept the new
			okButton.setOnClickListener( vista -> {
				Comparison.getFront(this).destiny = destiny;
				continueToNext();
			});

			findViewById(R.id.confronto_bottone_ignora ).setOnClickListener( v -> {
				Comparison.getFront(this).destiny = 0;
				continueToNext();
			});
		} else
			onBackPressed(); // Return to Compare
	}

	void setupCard(Gedcom gc, int cardId, Object o ) {
		String tit = "";
		String txt = ""; //TODO give better names
		String data = "";
		CardView card = findViewById(cardId);
		ImageView imageView = card.findViewById( R.id.confronto_foto );
		if( o instanceof Note ) {
			setRecordTypeTextTo( R.string.shared_note );
			Note n = (Note) o;
			txt = n.getValue();
			data = dateHour( n.getChange() );
		}
		else if( o instanceof Submitter ) {
			setRecordTypeTextTo( R.string.submitter );
			Submitter s = (Submitter) o;
			tit = s.getName();
			if( s.getEmail() != null ) txt += s.getEmail() + "\n";
			if( s.getAddress() != null ) txt += DetailActivity.writeAddress(s.getAddress(), true);
			data = dateHour(s.getChange());
		}
		else if( o instanceof Repository ) {
			setRecordTypeTextTo( R.string.repository );
			Repository r = (Repository) o;
			tit = r.getName();
			if( r.getAddress() != null ) txt += DetailActivity.writeAddress(r.getAddress(), true) + "\n";
			if( r.getEmail() != null ) txt += r.getEmail();
			data = dateHour(r.getChange());
		}
		else if( o instanceof Media ) {
			setRecordTypeTextTo( R.string.shared_media );
			Media m = (Media) o;
			if(m.getTitle()!=null) tit = m.getTitle();
			txt = m.getFile();
			data = dateHour( m.getChange() );
			imageView.setVisibility( View.VISIBLE );
			F.showImage( m, imageView, null );
		}
		else if( o instanceof Source ) {
			setRecordTypeTextTo( R.string.source );
			Source f = (Source) o;
			if(f.getTitle()!=null) tit = f.getTitle();
			else if(f.getAbbreviation()!=null) tit = f.getAbbreviation();
			if(f.getAuthor()!=null) txt = f.getAuthor()+"\n";
			if(f.getPublicationFacts()!=null) txt += f.getPublicationFacts()+"\n";
			if(f.getText()!=null) txt += f.getText();
			data = dateHour( f.getChange() );
		}
		else if( o instanceof Person ) {
			setRecordTypeTextTo( R.string.person );
			Person p = (Person) o;
			tit = U.properName( p );
			txt = U.details( p, null );
			data = dateHour( p.getChange() );
			imageView.setVisibility( View.VISIBLE );
			F.showMainImageForPerson( gc, p, imageView );
		}
		else if( o instanceof Family ) {
			setRecordTypeTextTo( R.string.family );
			Family f = (Family) o;
			txt = U.testoFamiglia( this, gc, f, false );
			data = dateHour( f.getChange() );
		}
		TextView titleText = card.findViewById( R.id.confronto_titolo );
		if( tit == null || tit.isEmpty() )
			titleText.setVisibility( View.GONE );
		else
			titleText.setText( tit );

		TextView textTextView = card.findViewById( R.id.confronto_testo );
		if( txt.isEmpty() )
			textTextView.setVisibility( View.GONE );
		else {
			if( txt.endsWith( "\n" ) )
				txt = txt.substring( 0, txt.length() - 1 );
			textTextView.setText( txt );
		}

		View changesView = card.findViewById(R.id.confronto_data);
		if( data.isEmpty() )
			changesView.setVisibility(View.GONE);
		else
			((TextView)changesView.findViewById(R.id.cambi_testo)).setText(data);

		if( cardId == R.id.confronto_nuovo ) {
			card.setCardBackgroundColor(getResources().getColor(R.color.evidenziaMedio));
		}

		if( tit.isEmpty() && txt.isEmpty() && data.isEmpty() ) // todo do you mean object null?
			card.setVisibility( View.GONE );
	}

	/**
	 * Page title.
	 */
	void setRecordTypeTextTo(int string ) {
		TextView typeText = findViewById( R.id.confronto_tipo );
		typeText.setText( getString(string) );
	}

	String dateHour(Change change ) {
		String dateHour = "";
		if( change != null )
			dateHour = change.getDateTime().getValue() + " - " + change.getDateTime().getTime();
		return dateHour;
	}

	void continueToNext() {
		Intent intent = new Intent();
		if( getIntent().getIntExtra("posizione",0) == Comparison.getList().size() ) {
			// The comparisons are over
			intent.setClass( this, ConfirmationActivity.class );
		} else {
			// Next comparison
			intent.setClass( this, TreeComparatorActivity.class );
			intent.putExtra( "posizione", getIntent().getIntExtra("posizione",0) + 1 );
		}
		if( Comparison.get().autoContinue) {
			if( Comparison.getFront(this).canBothAddAndReplace)
				Comparison.get().choicesMade++;
			else
				finish(); // removes the current front from the stack
		}
		startActivity( intent );
	}

	@Override
	public boolean onOptionsItemSelected( MenuItem i ) {
		onBackPressed();
		return true;
	}

	@Override
	public void onBackPressed() {
		super.onBackPressed();
		if( Comparison.get().autoContinue)
			Comparison.get().choicesMade--;
	}
}