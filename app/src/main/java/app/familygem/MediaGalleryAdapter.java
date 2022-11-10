// Adattatore per RecyclerView con lista dei media

package app.familygem;

import android.app.Activity;
import android.content.Context;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import android.content.Intent;
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

class MediaGalleryAdapter extends RecyclerView.Adapter<MediaGalleryAdapter.gestoreVistaMedia> {

	private List<MediaListContainer.MedCont> listaMedia;
	private boolean dettagli;

	MediaGalleryAdapter(List<MediaListContainer.MedCont> listaMedia, boolean dettagli ) {
		this.listaMedia = listaMedia;
		this.dettagli = dettagli;
	}

	@Override
	public gestoreVistaMedia onCreateViewHolder( ViewGroup parent, int type ) {
		View vista = LayoutInflater.from(parent.getContext()).inflate( R.layout.pezzo_media, parent, false );
		return new gestoreVistaMedia( vista, dettagli );
	}
	@Override
	public void onBindViewHolder( final gestoreVistaMedia gestore, int posizione ) {
		gestore.setta( posizione );
	}
	@Override
	public int getItemCount() {
		return listaMedia.size();
	}
	@Override
	public long getItemId(int position) {
		return position;
	}
	@Override
	public int getItemViewType(int position) {
		return position;
	}

	class gestoreVistaMedia extends RecyclerView.ViewHolder implements View.OnClickListener {
		View vista;
		boolean dettagli;
		Media media;
		Object contenitore;
		ImageView vistaImmagine;
		TextView vistaTesto;
		TextView vistaNumero;
		gestoreVistaMedia( View vista, boolean dettagli ) {
			super(vista);
			this.vista = vista;
			this.dettagli = dettagli;
			vistaImmagine = vista.findViewById( R.id.media_img );
			vistaTesto = vista.findViewById( R.id.media_testo );
			vistaNumero = vista.findViewById( R.id.media_num );
		}
		void setta( int posizione ) {
			media = listaMedia.get( posizione ).media;
			contenitore = listaMedia.get( posizione ).container;
			if( dettagli ) {
				arredaMedia( media, vistaTesto, vistaNumero );
				vista.setOnClickListener( this );
				((Activity)vista.getContext()).registerForContextMenu( vista );
				vista.setTag( R.id.tag_object, media );
				vista.setTag( R.id.tag_contenitore, contenitore );
				// Registra menu contestuale
				final AppCompatActivity attiva = (AppCompatActivity) vista.getContext();
				if( vista.getContext() instanceof IndividualPersonActivity) { // Fragment individuoMedia
					attiva.getSupportFragmentManager()
							.findFragmentByTag( "android:switcher:" + R.id.schede_persona + ":0" )	// non garantito in futuro
							.registerForContextMenu( vista );
				} else if( vista.getContext() instanceof Principal ) // Fragment Galleria
					attiva.getSupportFragmentManager().findFragmentById( R.id.contenitore_fragment ).registerForContextMenu( vista );
				else	// nelle AppCompatActivity
					attiva.registerForContextMenu( vista );
			} else {
				RecyclerView.LayoutParams parami = new RecyclerView.LayoutParams( RecyclerView.LayoutParams.WRAP_CONTENT, U.dpToPx(110) );
				int margin = U.dpToPx(5);
				parami.setMargins( margin, margin, margin, margin );
				vista.setLayoutParams( parami );
				vistaTesto.setVisibility( View.GONE );
				vistaNumero.setVisibility( View.GONE );
			}
			F.showImage( media, vistaImmagine, vista.findViewById(R.id.media_circolo) );
		}
		@Override
		public void onClick( View v ) {
			AppCompatActivity attiva = (AppCompatActivity) v.getContext();
			// Galleria in modalità scelta dell'object media
			// Restituisce l'id di un object media a IndividuoMedia
			if( attiva.getIntent().getBooleanExtra( "galleriaScegliMedia", false ) ) {
				Intent intent = new Intent();
				intent.putExtra( "idMedia", media.getId() );
				attiva.setResult( Activity.RESULT_OK, intent );
				attiva.finish();
			// Galleria in modalità normale apre Immagine
			} else {
				Intent intent = new Intent( v.getContext(), ImageActivity.class );
				if( media.getId() != null ) { // tutti i Media record
					Memory.setFirst( media );
				} else if( (attiva instanceof IndividualPersonActivity && contenitore instanceof Person) // media di primo livello nell'Indi
						|| attiva instanceof DetailActivity) { // normale apertura nei Dettagli
					Memory.add( media );
				} else { // da Galleria tutti i media semplici, o da IndividuoMedia i media sotto molteplici livelli
					new FindStack( Global.gc, media );
					if( attiva instanceof Principal ) // Solo in Galleria
						intent.putExtra( "daSolo", true ); // così poi Immagine mostra la dispensa
				}
				v.getContext().startActivity( intent );
			}
		}
	}

	static void arredaMedia( Media media, TextView vistaTesto, TextView vistaNumero ) {
		String testo = "";
		if( media.getTitle() != null )
			testo = media.getTitle() + "\n";
		if( Global.settings.expert && media.getFile() != null ) {
			String file = media.getFile();
			file = file.replace( '\\', '/' );
			if( file.lastIndexOf('/') > -1 ) {
				if( file.length() > 1 && file.endsWith("/") ) // rimuove l'ultima barra
					file = file.substring( 0, file.length()-1 );
				file = file.substring( file.lastIndexOf('/') + 1 );
			}
			testo += file;
		}
		if( testo.isEmpty() )
			vistaTesto.setVisibility( View.GONE );
		else {
			if( testo.endsWith("\n") )
				testo = testo.substring( 0, testo.length()-1 );
			vistaTesto.setText( testo );
		}
		if( media.getId() != null ) {
			vistaNumero.setText( String.valueOf(GalleryFragment.popularity(media)) );
			vistaNumero.setVisibility( View.VISIBLE );
		} else
			vistaNumero.setVisibility( View.GONE );
	}

	// Questa serve solo per creare una RecyclerView con le iconcine dei media che risulti trasparente ai click
	// todo però impedisce lo scroll in Dettaglio
	static class RiciclaVista extends RecyclerView {
		boolean dettagli;
		public RiciclaVista( Context context, boolean dettagli) {
			super(context);
			this.dettagli = dettagli;
		}
		@Override
		public boolean onTouchEvent( MotionEvent e ) {
			super.onTouchEvent( e );
			return dettagli; // quando è false la griglia non intercetta il click
		}
	}
}