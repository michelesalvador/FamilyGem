package app.familygem.list;

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
import app.familygem.Dettaglio;
import app.familygem.F;
import app.familygem.Global;
import app.familygem.ProfileActivity;
import app.familygem.Memory;
import app.familygem.Principal;
import app.familygem.R;
import app.familygem.U;
import app.familygem.constant.Choice;
import app.familygem.detail.Immagine;
import app.familygem.visitor.ListaMediaContenitore;
import app.familygem.visitor.TrovaPila;

// Adapter for the RecyclerView of media gallery
public class GalleryAdapter extends RecyclerView.Adapter<GalleryAdapter.gestoreVistaMedia> {

	private List<ListaMediaContenitore.MedCont> listaMedia;
	private boolean dettagli;

	public GalleryAdapter(List<ListaMediaContenitore.MedCont> listaMedia, boolean dettagli) {
		this.listaMedia = listaMedia;
		this.dettagli = dettagli;
	}

	@Override
	public gestoreVistaMedia onCreateViewHolder(ViewGroup parent, int tipo) {
		View vista = LayoutInflater.from(parent.getContext()).inflate(R.layout.pezzo_media, parent, false);
		return new gestoreVistaMedia(vista, dettagli);
	}
	@Override
	public void onBindViewHolder(final gestoreVistaMedia gestore, int posizione) {
		gestore.setta(posizione);
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
		void setta(int posizione) {
			media = listaMedia.get(posizione).media;
			contenitore = listaMedia.get(posizione).contenitore;
			if( dettagli ) {
				arredaMedia(media, vistaTesto, vistaNumero);
				vista.setOnClickListener(this);
				((Activity)vista.getContext()).registerForContextMenu(vista);
				vista.setTag(R.id.tag_oggetto, media);
				vista.setTag(R.id.tag_contenitore, contenitore);
				// Registra menu contestuale
				final AppCompatActivity activity = (AppCompatActivity)vista.getContext();
				if( vista.getContext() instanceof ProfileActivity ) { // ProfileMediaFragment
					activity.getSupportFragmentManager()
							.findFragmentByTag( "android:switcher:" + R.id.profile_pager + ":0" ) // non garantito in futuro
							.registerForContextMenu( vista );
				} else if( vista.getContext() instanceof Principal ) // GalleryFragment
					activity.getSupportFragmentManager().findFragmentById(R.id.contenitore_fragment).registerForContextMenu(vista);
				else // nelle AppCompatActivity
					activity.registerForContextMenu(vista);
			} else {
				RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(RecyclerView.LayoutParams.WRAP_CONTENT, U.dpToPx(110));
				int margin = U.dpToPx(5);
				params.setMargins(margin, margin, margin, margin);
				vista.setLayoutParams(params);
				vistaTesto.setVisibility(View.GONE);
				vistaNumero.setVisibility(View.GONE);
			}
			F.paintMedia( media, vistaImmagine, vista.findViewById(R.id.media_circolo) );
		}
		@Override
		public void onClick(View v) {
			AppCompatActivity activity = (AppCompatActivity)v.getContext();
			// GalleryFragment in modalità scelta dell'oggetto media
			// Restituisce l'id di un oggetto media a ProfileMediaFragment
			if( activity.getIntent().getBooleanExtra(Choice.MEDIA, false) ) {
				Intent intent = new Intent();
				intent.putExtra("mediaId", media.getId());
				activity.setResult(Activity.RESULT_OK, intent);
				activity.finish();
			} else { // GalleryFragment in modalità normale apre Immagine
				Intent intento = new Intent(v.getContext(), Immagine.class);
				if( media.getId() != null ) { // tutti i Media record
					Memory.setPrimo(media);
				} else if( (activity instanceof ProfileActivity && contenitore instanceof Person) // media di primo livello nell'Indi
						|| activity instanceof Dettaglio ) { // normale apertura nei Dettagli
					Memory.aggiungi(media);
				} else { // da GalleryFragment tutti i media semplici, o da ProfileMediaFragment i media sotto molteplici livelli
					new TrovaPila(Global.gc, media);
					if( activity instanceof Principal ) // Solo in GalleryFragment
						intento.putExtra("daSolo", true); // così poi Immagine mostra la dispensa
				}
				v.getContext().startActivity(intento);
			}
		}
	}

	public static void arredaMedia(Media media, TextView vistaTesto, TextView vistaNumero) {
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
			vistaNumero.setText( String.valueOf(GalleryFragment.popolarita(media)) );
			vistaNumero.setVisibility( View.VISIBLE );
		} else
			vistaNumero.setVisibility( View.GONE );
	}

	// Questa serve solo per creare una RecyclerView con le iconcine dei media che risulti trasparente ai click
	// todo però impedisce lo scroll in Dettaglio
	public static class RiciclaVista extends RecyclerView {
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
