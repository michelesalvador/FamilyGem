package app.familygem;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import androidx.documentfile.provider.DocumentFile;
import org.apache.commons.io.FileUtils;
import org.folg.gedcom.model.Gedcom;
import org.folg.gedcom.model.Header;
import org.folg.gedcom.model.Media;
import org.folg.gedcom.model.Name;
import org.folg.gedcom.model.Person;
import org.folg.gedcom.visitors.GedcomWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import app.familygem.visitor.ListaMedia;

// Utility class to export a tree as GEDCOM or ZIP backup
public class Exporter {

	private final Context context;
	private int treeId;
	private Gedcom gedcom;
	private Uri targetUri;
	public String errorMessage; // Message of possible error
	public String successMessage; // Message of the obtained result

	Exporter(Context context) {
		this.context = context;
	}

	// Open the Json tree and return true if successful
	public boolean openTree(int treeId) {
		this.treeId = treeId;
		gedcom = TreesActivity.openTemporaryGedcom(treeId, true);
		if( gedcom == null ) {
			return error(R.string.no_useful_data);
		}
		return true;
	}

	// Write only the GEDCOM in the URI
	public boolean exportGedcom(Uri targetUri) {
		this.targetUri = targetUri;
		aggiornaTestata(estraiNome(targetUri));
		ottimizzaGedcom();
		GedcomWriter writer = new GedcomWriter();
		File gedcomFile = new File(context.getCacheDir(), "temp.ged");
		try {
			writer.write(gedcom, gedcomFile);
			OutputStream out = context.getContentResolver().openOutputStream(targetUri);
			FileUtils.copyFile(gedcomFile, out);
			out.flush();
			out.close();
		} catch( Exception e ) {
			return error(e.getLocalizedMessage());
		}
		// Make the file visible from Windows
		// But it seems ineffective in KitKat where the file remains invisible
		context.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, targetUri));
		Global.gc = TreesActivity.readJson(treeId); // Reset changes
		return success(R.string.gedcom_exported_ok);
	}

	// Write the GEDCOM with the media in a ZIP file
	public boolean exportZippedGedcom(Uri targetUri) {
		this.targetUri = targetUri;
		// Create the GEDCOM file
		String title = Global.settings.getTree(treeId).title;
		String gedcomFileName = title.replaceAll("[\\\\/:*?\"<>|'$]", "_") + ".ged";
		aggiornaTestata(gedcomFileName);
		ottimizzaGedcom();
		GedcomWriter scrittore = new GedcomWriter();
		File fileGc = new File(context.getCacheDir(), gedcomFileName);
		try {
			scrittore.write(gedcom, fileGc);
		} catch( Exception e ) {
			return error(e.getLocalizedMessage());
		}
		DocumentFile gedcomDocument = DocumentFile.fromFile(fileGc);
		// Aggiunge il GEDCOM alla raccolta di file media
		Map<DocumentFile, Integer> raccolta = raccogliMedia();
		raccolta.put(gedcomDocument, 0);
		if( !creaFileZip(raccolta) )
			return false;
		context.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, targetUri));
		Global.gc = TreesActivity.readJson(treeId);
		return success(R.string.zip_exported_ok);
	}

	// Create a zipped file with the tree, settings and media
	public boolean exportZipBackup(String root, int grade, Uri targetUri) {
		this.targetUri = targetUri;
		// Media
		Map<DocumentFile, Integer> files = raccogliMedia();
		// Tree Json
		File fileTree = new File(context.getFilesDir(), treeId + ".json");
		files.put(DocumentFile.fromFile(fileTree), 1);
		// Json delle preferenze
		Settings.Tree tree = Global.settings.getTree(treeId);
		if( root == null ) root = tree.root;
		if( grade < 0 ) grade = tree.grade;
		// String titoloAlbero, String root, int grade possono arrivare diversi da Condividi
		Settings.ZippedTree settings = new Settings.ZippedTree(
				tree.title, tree.persons, tree.generations, root, tree.shares, grade);
		File fileSettings = settings.salva();
		files.put(DocumentFile.fromFile(fileSettings), 0);
		if( !creaFileZip(files) )
			return false;
		context.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, targetUri));
		return success(R.string.zip_exported_ok);
	}

	// Restituisce il numero di file media da allegare
	public int quantiFileMedia() {
		ListaMedia visitaMedia = new ListaMedia(gedcom, 0 );
		gedcom.accept( visitaMedia );
		int quantiFile = 0;
		for( Media med : visitaMedia.lista ) {
			if( F.percorsoMedia(treeId, med) != null || F.uriMedia(treeId, med ) != null )
				quantiFile++;
		}
		return quantiFile;
	}

	// Riceve l'id di un albero e restituisce una Map di DocumentFile dei media che riesce a rastrellare
	private Map<DocumentFile,Integer> raccogliMedia() {
		ListaMedia visitaMedia = new ListaMedia(gedcom, 0 );
		gedcom.accept( visitaMedia );
		/*  Capita che diversi Media puntino allo stesso file.
		*   E potrebbe anche capitare che diversi percorsi finiscano con nomi di file uguali,
		*   ad es. 'percorsoA/img.jpg' 'percorsoB/img.jpg'
		*   Bisogna evitare che nei media dello ZIP finiscano file con lo stesso nome.
		*   Questo loop crea una lista di percorsi con nome file univoci */
		Set<String> paths = new HashSet<>();
		Set<String> onlyFileNames = new HashSet<>(); // Nomi file di controllo
		for( Media med : visitaMedia.lista ) {
			String path = med.getFile();
			if( path != null && !path.isEmpty() ) {
				String fileName = path.replace('\\', '/');
				if( fileName.lastIndexOf('/') > -1 )
					fileName = fileName.substring(fileName.lastIndexOf('/') + 1);
				if( !onlyFileNames.contains(fileName) )
					paths.add(path);
				onlyFileNames.add(fileName);
			}
		}
		Map<DocumentFile, Integer> collezione = new HashMap<>();
		for( String path : paths ) {
			Media med = new Media();
			med.setFile(path);
			// Paths
			String percorsoMedia = F.percorsoMedia(treeId, med);
			if( percorsoMedia != null )
				collezione.put(DocumentFile.fromFile(new File(percorsoMedia)), 2); // todo canRead() ?
			else { // URIs
				Uri uriMedia = F.uriMedia(treeId, med);
				if( uriMedia != null )
					collezione.put(DocumentFile.fromSingleUri(context, uriMedia), 2);
			}
		}
		return collezione;
	}

	private void aggiornaTestata(String nomeFileGedcom) {
		Header testa = gedcom.getHeader();
		if( testa == null )
			gedcom.setHeader(NewTreeActivity.creaTestata(nomeFileGedcom));
		else {
			testa.setFile(nomeFileGedcom);
			testa.setDateTime(U.actualDateTime());
		}
	}

	// Migliora il GEDCOM per l'esportazione
	void ottimizzaGedcom() {
		// Value dei nomi da given e surname
		for( Person person : gedcom.getPeople() ) {
			for( Name n : person.getNames() )
				if( n.getValue() == null && (n.getPrefix() != null || n.getGiven() != null
						|| n.getSurname() != null || n.getSuffix() != null) ) {
					String epiteto = "";
					if( n.getPrefix() != null )
						epiteto = n.getPrefix();
					if( n.getGiven() != null )
						epiteto += " " + n.getGiven();
					if( n.getSurname() != null )
						epiteto += " /" + n.getSurname() + "/";
					if( n.getSuffix() != null )
						epiteto += " " + n.getSuffix();
					n.setValue(epiteto.trim());
				}
		}
	}

	// Estrae solo il nome del file da un URI
	private String estraiNome( Uri uri ) {
		// file://
		if( uri.getScheme() != null && uri.getScheme().equalsIgnoreCase("file") ) {
			return uri.getLastPathSegment();
		}
		// Cursore (di solito funziona questo)
		Cursor cursore = context.getContentResolver().query( uri, null, null, null, null);
		if( cursore != null && cursore.moveToFirst() ) {
			int indice = cursore.getColumnIndex( OpenableColumns.DISPLAY_NAME );
			String nomeFile = cursore.getString( indice );
			cursore.close();
			if( nomeFile != null ) return nomeFile;
		}
		// DocumentFile
		DocumentFile document = DocumentFile.fromSingleUri(context, targetUri );
		String nomeFile = document.getName();
		if( nomeFile != null ) return nomeFile;
		// Alla frutta
		return "tree.ged";
	}

	// Riceve la lista di DocumentFile e li mette in un file ZIP scritto nel targetUri
	// Restiuisce messaggio di errore o null se tutto a posto
	boolean creaFileZip(Map<DocumentFile, Integer> files) {
		byte[] buffer = new byte[128];
		try {
			ZipOutputStream zos = new ZipOutputStream(context.getContentResolver().openOutputStream(targetUri));
			for( Map.Entry<DocumentFile, Integer> fileTipo : files.entrySet() ) {
				DocumentFile file = fileTipo.getKey();
				InputStream input = context.getContentResolver().openInputStream(file.getUri());
				String nomeFile = file.getName();   // File che non vengono rinominati ('settings.json', 'famiglia.ged')
				if( fileTipo.getValue() == 1 )
					nomeFile = "tree.json";
				else if( fileTipo.getValue() == 2 )
					nomeFile = "media/" + file.getName();
				zos.putNextEntry(new ZipEntry(nomeFile));
				int read;
				while( (read = input.read(buffer)) != -1 ) {
					zos.write(buffer, 0, read);
				}
				zos.closeEntry();
				input.close();
			}
			zos.close();
		} catch( IOException e ) {
			return error(e.getLocalizedMessage());
		}
		return true;
	}

	public boolean success(int message) {
		successMessage = context.getString(message);
		return true;
	}

	public boolean error(int error) {
		return error(context.getString(error));
	}
	public boolean error(String error) {
		errorMessage = error;
		return false;
	}
}
