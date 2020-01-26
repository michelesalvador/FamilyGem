package app.familygem;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.folg.gedcom.model.Media;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ListaGriglia extends AppCompatActivity {

	@Override
	public void onCreate( Bundle bandolo ) {
		super.onCreate( bandolo );
		setContentView( R.layout.galleria );
		RecyclerView griglia = findViewById( R.id.galleria );
		//griglia.setNestedScrollingEnabled( false ); pareva dovesse supplire al caricamento asincrono
		griglia.setHasFixedSize(true); // dovrebbe aiutare

		// Crea una lista di Media dai file
		File dirMemoria = new File( getExternalFilesDir(null) +"/"+ Globale.preferenze.idAprendo );
		List<File> files = (List<File>) FileUtils.listFiles( dirMemoria, TrueFileFilter.INSTANCE, null );
		List<Media> listaMedia = new ArrayList<>();
		Media ftp = new Media();
		ftp.setFile( "ftp://ftp.genealogy.org/genealogy/GEDCOM/" );
		listaMedia.add( ftp );
		Media img = new Media();
		img.setFile( "https://www.familygem.app/img/vetrina.png?altro" );
		listaMedia.add( img );
		Media web = new Media();
		web.setFile( "http://www.geditcom.com/" );
		web.setTitle( "Pagina web" );
		listaMedia.add( web );
		int id = 1;
		for( File file : files ) {
			Media med = new Media();
			med.setFile( file.getAbsolutePath() );
			if( id < 10 ) {
				med.setId( "M00" + id );
				med.setTitle( "Titolo " + id );
				id++;
			}
			listaMedia.add( med );
		}
		Media vuoto = new Media();
		listaMedia.add( vuoto );
		Media vuoto2 = new Media();
		vuoto2.setTitle( "Tittolo" );
		listaMedia.add( vuoto2 );
		// Mappa linkata
		Map<Media,Object> mappaMedia = new LinkedHashMap<>();
		for( Media med : listaMedia )
			mappaMedia.put( med, null );

		/* Sistema con GridView più vecchio e meno potente di RecyclerView
		ArrayAdapter<String> adapter = new ArrayAdapter<String>( getContext(), android.R.layout.simple_list_item_1, aList );
		AdattatoreMedia adapter = new AdattatoreMedia( getContext(), listaMedia, true );
		griglia.setAdapter(adapter);
		griglia.setOnItemClickListener( new AdapterView.OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View v, int position, long id) {}
		});*/
		//RecyclerView.LayoutManager layoutManager = new LinearLayoutManager( this ); // lista semplice
		RecyclerView.LayoutManager layoutManager = new GridLayoutManager(this, 2 ); // lista grigliata
		griglia.setLayoutManager( layoutManager );
		AdattatoreGalleria adattatore = new AdattatoreGalleria( mappaMedia, true );
		griglia.setAdapter( adattatore );

		registerForContextMenu( griglia );
	}


	// Adattatore per RecyclerView con lista dei media
	class AdattatoreGalleria extends RecyclerView.Adapter<AdattatoreGalleria.gestoreVistaMedia> {
		//Map<Media,Object> lista;
		Object[] listaMedia;
		Object[] listaContenitori;
		boolean dettagli;

		AdattatoreGalleria( Map<Media,Object> lista, boolean dettagli ) {
			//this.lista = lista;
			listaMedia = lista.keySet().toArray();
			listaContenitori = lista.values().toArray();
			this.dettagli = dettagli;
		}

		// onCreateViewHolder viene chiamato una sola volta alla creazione della vista
		@Override
		public gestoreVistaMedia onCreateViewHolder( ViewGroup parent, int tipo ) {
			View vista = LayoutInflater.from(parent.getContext()).inflate( R.layout.pezzo_media, parent, false);
			final gestoreVistaMedia gestore = new gestoreVistaMedia( vista );
			//int posizione = gestore.getAdapterPosition(); // al momento della creazione è sempre -1
			//int posizione = gestore.getLayoutPosition() ; // idem
			//gestore.setta( posizione ); // quindi qui non è possibile popolare l'elemento
			return gestore;
		}

		// onBindViewHolder viene chiamato ogni volta che l'elemento ricompare nello schermo o cambia contenuto
		@Override
		public void onBindViewHolder( final gestoreVistaMedia gestore, int posizione ) {
			//s.l("onBindViewHolder " + posizione );
			// bisogna stare attenti popolare gestoreVistaMedia una volta sola
			if( gestore.media == null ) {
				//s.l("media is null " + posizione );
				gestore.setta( posizione );

				/*final Media media = lista.get(posizione);
				String testo = "";
				if( media.getTitle() != null )
					testo = media.getTitle() + "\n";
				if( Globale.preferenze.esperto && media.getFile() != null ) {
					String file = media.getFile();
					file = file.replace( '\\', '/' );
					if( file.lastIndexOf('/') > -1 )
						file = file.substring( file.lastIndexOf('/')+1 );
					testo += file;
				}
				if( testo.isEmpty() )
					gestore.vistaTesto.setVisibility( View.GONE );
				else {
					if( testo.endsWith("\n") )
						testo = testo.substring( 0, testo.length()-1 );
					gestore.vistaTesto.setText( testo );
				}

				//if( position < 10 )
				//if( gestore.getAdapterPosition() < 10 )
				if( media.getId() != null ) {
					gestore.vistaNumero.setText( media.getId() );
				} else
					gestore.vistaNumero.setVisibility( View.GONE );*/

				//holder.immagine.setImageURI( Uri.fromFile( f ) ); ok ma non può caricare le immagini a piena risoluzione
				//U.mostraMedia( gestore.immagine, media ); // fatica a caricare le immagini grosse
				/* Per un po' credevo di dover resettare tutti i parametri
				gestore.immagine.setScaleType( ImageView.ScaleType.CENTER_CROP );
				RelativeLayout.LayoutParams parami = new RelativeLayout.LayoutParams(
						RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT );
				gestore.immagine.setLayoutParams( parami );
				gestore.immagine.setImageResource( R.drawable.anna_salvador );*/
				//s.l( gestore.vista.getWidth() );
				//new U.MostraMedia( gestore.vistaImmagine, true ).execute( media );
			}
		}
		// Necessario
		@Override
		public int getItemCount() {
			return listaMedia.length;
		}
		// Questi due fanno gestire meglio il riciclo delle viste
		@Override
		public long getItemId( int position ) {
			return position;
		}
		@Override
		public int getItemViewType( int position ) {
			return position;
		}

		class gestoreVistaMedia extends RecyclerView.ViewHolder implements View.OnClickListener {
			View vista;
			Media media;
			ImageView vistaImmagine;
			TextView vistaTesto;
			TextView vistaNumero;
			gestoreVistaMedia( View vista ) {
				super(vista);
				this.vista = vista;
				vistaImmagine = vista.findViewById( R.id.media_img );
				vistaTesto = vista.findViewById( R.id.media_testo );
				vistaNumero = vista.findViewById( R.id.media_num );
				//int posizione = getAdapterPosition(); // anche qui al momento della creazione è sempre -1
			}
			void setta( int posizione ) {
				//media = lista.get( posizione );
				//media = (Media) lista.keySet().toArray()[posizione]; // ok ma forse rallenta
				media = (Media)listaMedia[posizione];
				Object contenitore = listaContenitori[posizione];
				if( dettagli ) {
					String testo = "";
					if( media.getTitle() != null )
						testo = media.getTitle() + "\n";
					if( Globale.preferenze.esperto && media.getFile() != null ) {
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
						vistaNumero.setText( media.getId() );
					} else
						vistaNumero.setVisibility( View.GONE );

					vista.setOnClickListener( this );
				}
				U.dipingiMedia( media, vistaImmagine, vista.findViewById(R.id.media_circolo) );
			}

			@Override // ok
			public void onClick( View v ) {
				Toast.makeText( v.getContext(), getAdapterPosition() +": "+ media.getFile(), Toast.LENGTH_LONG ).show();
			}
		}
	}


	// Adattatore per GridView
	public class AdattatoreMedia extends ArrayAdapter {
		private Context contesto;
		private List lista;
		private boolean dettagli;

		AdattatoreMedia( Context contesto, List lista, boolean dettagli ) {
			super( contesto, R.layout.pezzo_media, lista );
			this.contesto = contesto;
			this.lista = lista;
			this.dettagli = dettagli;
		}

		// create a new ImageView for each item referenced by the Adapter
		public View getView( int posizione, View vista, ViewGroup parent ) {
			gestoreVistaMedia gestore;
			// ViewHolder Pattern
			if( vista == null ) {
				vista = ((Activity)contesto).getLayoutInflater().inflate( R.layout.pezzo_media, parent, false);
				gestore = new gestoreVistaMedia();
				gestore.immagine = vista.findViewById( R.id.media_img );
				gestore.testo = vista.findViewById( R.id.media_testo );
				gestore.numero = vista.findViewById( R.id.media_num );
				vista.setTag( gestore );
			} else {
				gestore = (gestoreVistaMedia) vista.getTag();
			}
			final Media media = (Media)lista.get(posizione);
			U.mostraMedia( gestore.immagine, media );

			if( dettagli ) {
				String testo = "";
				if( media.getTitle() != null )
					testo = media.getTitle() + "\n";
				if( media.getFile() != null ) {
					String file = media.getFile();
					file = file.replace( '\\', '/' );
					if( file.lastIndexOf('/') > -1 )
						file = file.substring( file.lastIndexOf('/')+1 );
					testo += file;
				}
				gestore.testo.setText( testo );

				if( media.getId() != null )
					gestore.numero.setText( "12" );
				else gestore.numero.setVisibility( View.GONE );

				final AppCompatActivity attiva = (AppCompatActivity) contesto;
				vista.setOnClickListener( new View.OnClickListener() {
					public void onClick( View vista ) {
						// Galleria in modalità scelta dell'oggetto media
						// Restituisce l'id di un oggetto media a IndividuoMedia
						if( attiva.getIntent().getBooleanExtra( "galleriaScegliMedia", false ) ) {
							Intent intent = new Intent();
							intent.putExtra( "idMedia", media.getId() );
							attiva.setResult( Activity.RESULT_OK, intent );
							attiva.finish();
							// Galleria in modalità normale Apre Immagine
						} else {
							//Ponte.manda( media, "oggetto" );
							//Ponte.manda( contenitore, "contenitore" ); todo
							//contesto.startActivity( new Intent( contesto, Immagine.class ) );
						}
					}
				} );
			}
			return vista;
			/*ImageView imageView;
			if (convertView == null) {
				imageView = new ImageView(contesto);
				imageView.setLayoutParams(new GridView.LayoutParams(100, 100));
				imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
				//imageView.setPadding(8, 8, 8, 8);
			} else {
				imageView = (ImageView) convertView;
			}
			//imageView.setImageResource( mThumbIds[position] );
			return imageView;
			LayoutInflater inflater = (LayoutInflater) contesto.getSystemService(Activity.LAYOUT_INFLATER_SERVICE);
			View view = inflater.inflate(R.layout.cust_grid_album, null, true);
			ImageView iv = view.findViewById(R.id.cga_iv);
			TextView txtname = view.findViewById(R.id.cga_txt_name);
			//setting image
			Glide.with(context).load(AlCover.get(i)).placeholder(android.R.color.white).centerCrop().into(iv);
			//setting text
			txtname.setText(AlBuckName.get(i));
			return view;*/
		}
	}

	static class gestoreVistaMedia {
		ImageView immagine;
		TextView testo;
		TextView numero;
	}
}