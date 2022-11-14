package app.familygem;

import android.app.Activity;
import android.content.Context;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import android.content.Intent;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import org.folg.gedcom.model.Media;
import org.folg.gedcom.model.Person;
import java.util.List;
import app.familygem.detail.ImageActivity;
import app.familygem.visitor.MediaListContainer;
import app.familygem.visitor.FindStack;

/**
 * Adapter for RecyclerView with media list
 * */
class MediaGalleryAdapter extends RecyclerView.Adapter<MediaGalleryAdapter.MediaViewHolder> {

	private List<MediaListContainer.MedCont> mediaList;
	private boolean details;

	MediaGalleryAdapter(List<MediaListContainer.MedCont> mediaList, boolean details) {
		this.mediaList = mediaList;
		this.details = details;
	}

	@Override
	public MediaViewHolder onCreateViewHolder(ViewGroup parent, int type ) {
		View view = LayoutInflater.from(parent.getContext()).inflate( R.layout.pezzo_media, parent, false );
		return new MediaViewHolder( view, details);
	}
	@Override
	public void onBindViewHolder(final MediaViewHolder holder, int position ) {
		holder.bind( position );
	}
	@Override
	public int getItemCount() {
		return mediaList.size();
	}
	@Override
	public long getItemId(int position) {
		return position;
	}
	@Override
	public int getItemViewType(int position) {
		return position;
	}

	class MediaViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
		View view;
		boolean details;
		Media media;
		Object container;
		ImageView imageView;
		TextView textView;
		TextView numberView;
		MediaViewHolder(View view, boolean details) {
			super(view);
			this.view = view;
			this.details = details;
			imageView = view.findViewById( R.id.media_img );
			textView = view.findViewById( R.id.media_testo );
			numberView = view.findViewById( R.id.media_num );
		}
		void bind(int position ) {
			media = mediaList.get( position ).media;
			container = mediaList.get( position ).container;
			if(details) {
				setupMedia( media, textView, numberView);
				view.setOnClickListener( this );
				((Activity) view.getContext()).registerForContextMenu(view);
				view.setTag( R.id.tag_object, media );
				view.setTag( R.id.tag_contenitore, container);
				// Register context menu
				final AppCompatActivity activity = (AppCompatActivity) view.getContext();
				if( view.getContext() instanceof IndividualPersonActivity) { // IndividualMediaFragment
					activity.getSupportFragmentManager()
							.findFragmentByTag( "android:switcher:" + R.id.schede_persona + ":0" )	// not guaranteed in the future
							.registerForContextMenu(view);
				} else if( view.getContext() instanceof Principal ) // GalleryFragment
					activity.getSupportFragmentManager().findFragmentById( R.id.contenitore_fragment ).registerForContextMenu(view);
				else	// in AppCompatActivity
					activity.registerForContextMenu(view);
			} else {
				RecyclerView.LayoutParams params = new RecyclerView.LayoutParams( RecyclerView.LayoutParams.WRAP_CONTENT, U.dpToPx(110) );
				int margin = U.dpToPx(5);
				params.setMargins( margin, margin, margin, margin );
				view.setLayoutParams( params );
				textView.setVisibility( View.GONE );
				numberView.setVisibility( View.GONE );
			}
			F.showImage( media, imageView, view.findViewById(R.id.media_circolo) );
		}
		@Override
		public void onClick( View v ) {
			AppCompatActivity activity = (AppCompatActivity) v.getContext();
			// Gallery in choose mode of the media object
			// Return the id of a media object to IndividualMediaFragment
			if( activity.getIntent().getBooleanExtra( "galleriaScegliMedia", false ) ) {
				Intent intent = new Intent();
				intent.putExtra( "idMedia", media.getId() );
				activity.setResult( Activity.RESULT_OK, intent );
				activity.finish();
			// Gallery in normal mode opens ImageActivity
			} else {
				Intent intent = new Intent( v.getContext(), ImageActivity.class );
				if( media.getId() != null ) { // all Media records
					Memory.setFirst( media );
				} else if( (activity instanceof IndividualPersonActivity && container instanceof Person) // top tier media in indi
						|| activity instanceof DetailActivity) { // normal opening in the DetailActivity
					Memory.add( media );
				} else { // from Gallery all the simple media, or from IndividualMediaFragment the media under multiple levels
					new FindStack( Global.gc, media );
					if( activity instanceof Principal ) // Only in the Gallery
						intent.putExtra( "daSolo", true ); // so then ImageActivity shows the pantry (?)
				}
				v.getContext().startActivity( intent );
			}
		}
	}

	static void setupMedia(Media media, TextView textView, TextView vistaNumero ) {
		String text = "";
		if( media.getTitle() != null )
			text = media.getTitle() + "\n";
		if( Global.settings.expert && media.getFile() != null ) {
			String file = media.getFile();
			file = file.replace( '\\', '/' );
			if( file.lastIndexOf('/') > -1 ) {
				if( file.length() > 1 && file.endsWith("/") ) // removes the last slash
					file = file.substring( 0, file.length()-1 );
				file = file.substring( file.lastIndexOf('/') + 1 );
			}
			text += file;
		}
		if( text.isEmpty() )
			textView.setVisibility( View.GONE );
		else {
			if( text.endsWith("\n") )
				text = text.substring( 0, text.length()-1 );
			textView.setText( text );
		}
		if( media.getId() != null ) {
			vistaNumero.setText( String.valueOf(GalleryFragment.popularity(media)) );
			vistaNumero.setVisibility( View.VISIBLE );
		} else
			vistaNumero.setVisibility( View.GONE );
	}

	/**
	 * This is just to create a RecyclerView with media icons that is transparent to clicks
	 * TODO prevents scrolling in Detail though
	 * */
	static class MediaIconsRecyclerView extends RecyclerView {
		public MediaIconsRecyclerView(Context context) {
			super(context);
		}
		public MediaIconsRecyclerView(Context context, AttributeSet attrs) {
			super(context, attrs);
		}
		public MediaIconsRecyclerView(Context context,AttributeSet attrs, int defStyleAttr) {
			super(context, attrs, defStyleAttr);
		}
		boolean details;
		public MediaIconsRecyclerView(Context context, boolean details) {
			super(context);
			this.details = details;
		}
		@Override
		public boolean onTouchEvent( MotionEvent e ) {
			super.onTouchEvent( e );
			return details; // when false the grid does not intercept the click
		}
	}
}