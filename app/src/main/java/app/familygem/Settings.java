// Class that represents the preferences saved in 'settings.json'

package app.familygem;

import android.widget.Toast;
import com.google.gson.Gson;
import org.apache.commons.io.FileUtils;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public class Settings {

	String referrer; // È 'start' appena installata l'app (cioè quando non esiste 'files/settings.json')
	                 // Se l'installazione proviene da una condivisione accoglie un dateId tipo '20191003215337'
	                 // Ben presto diventa null e rimane tale, a meno di cancellare tutti i dati
	List<Tree> trees;
	public int openTree; // Number of the tree currently opened. 0 means not a particular tree.
		// Must be consistent with the 'Global.gc' opened tree.
		// It is not reset by closing the tree, to be reused by 'Load last opened tree at startup'.
	boolean autoSave;
	boolean loadTree;
	public boolean expert;
	boolean shareAgreement;
	Diagram diagram;

	int max() {
		int num = 0;
		for( Tree c : trees ) {
			if( c.id > num )
				num = c.id;
		}
		return num;
	}

	void aggiungi(Tree c) {
		trees.add(c);
	}

	void rinomina(int id, String nuovoNome) {
		for( Tree c : trees ) {
			if( c.id == id ) {
				c.title = nuovoNome;
				break;
			}
		}
		save();
	}

	void deleteTree(int id) {
		for( Tree c : trees ) {
			if( c.id == id ) {
				trees.remove(c);
				break;
			}
		}
		if( id == openTree ) {
			openTree = 0;
		}
		save();
	}

	public void save() {
		try {
			Gson gson = new Gson();
			String json = gson.toJson(this);
			FileUtils.writeStringToFile(new File(Global.context.getFilesDir(), "settings.json"), json, "UTF-8");
		} catch( Exception e ) {
			Toast.makeText(Global.context, e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
		}
	}

	// The tree currently open
	Tree getCurrentTree() {
		for( Tree alb : trees ) {
			if( alb.id == openTree )
				return alb;
		}
		return null;
	}

	Tree getTree(int treeId) {
		/* Da quando ho installato Android Studio 4.0, quando compilo con minifyEnabled true
		   misteriosamente 'alberi' qui è null.
		   Però non è null se DOPO c'è 'trees = Global.settings.trees'
		   Davvero incomprensibile!
		*/
		if( trees == null ) {
			trees = Global.settings.trees;
		}
		if( trees != null )
			for( Tree tree : trees ) {
				if( tree.id == treeId ) {
					if( tree.uris == null ) // traghettatore inserito in Family Gem 0.7.15
						tree.uris = new LinkedHashSet<>();
					return tree;
				}
			}
		return null;
	}

	static class Diagram {
		int ancestors;
		int uncles;
		int descendants;
		int siblings;
		int cousins;
		boolean spouses;
	}

	void defaultDiagram() {
		diagram = new Diagram();
		diagram.ancestors = 3;
		diagram.uncles = 2;
		diagram.descendants = 3;
		diagram.siblings = 2;
		diagram.cousins = 1;
		diagram.spouses = true;
	}
/*
"grado":
0	albero creato da zero in Italia
	rimane 0 anche aggiungendo il submitter principale, condividendolo e ricevendo novità
9	albero spedito per la condivisione in attesa di marchiare con 'passato' tutti i submitter
10	albero ricevuto tramite condivisione in Australia
	non potrà mai più ritornare 0
20	albero ritornato in Italia dimostratosi un derivato da uno zero (o da uno 10).
	solo se è 10 può diventare 20. Se per caso perde lo status di derivato ritorna 10 (mai 0)
30	albero derivato da cui sono state estratte tutte le novità OPPURE privo di novità già all'arrivo (grigio). Eliminabile
*/

	static class Tree {
		int id;
		String title;
		LinkedHashSet<String> dirs;
		LinkedHashSet<String> uris;
		int persons;
		int generations;
		int media;
		String root;
		List<Share> shares; // dati identificativi delle condivisioni attraverso il tempo e lo spazio
		String shareRoot; // id della Person radice dell'albero in Condivisione
		int grade; // grado della condivisione

		Tree(int id, String title, String dir, int persons, int generations, String root, List<Share> shares, int grade) {
			this.id = id;
			this.title = title;
			dirs = new LinkedHashSet<>();
			if( dir != null )
				dirs.add(dir);
			uris = new LinkedHashSet<>();
			this.persons = persons;
			this.generations = generations;
			this.root = root;
			this.shares = shares;
			this.grade = grade;
		}

		void aggiungiCondivisione(Share share) {
			if( shares == null )
				shares = new ArrayList<>();
			shares.add(share);
		}
	}

	// The essential data of a share
	static class Share {
		String dateId; // on compressed date and time format: YYYYMMDDhhmmss
		String submitter; // Submitter id
		Share(String dateId, String submitter) {
			this.dateId = dateId;
			this.submitter = submitter;
		}
	}

	// Blueprint of the file 'settings.json' inside a backup, share or example ZIP file
	// It contains basic info of the zipped tree
	static class ZippedTree {
		String title;
		int persons;
		int generations;
		String root;
		List<Share> shares;
		int grade; // il grado di destinazione dell'albero zippato

		ZippedTree(String title, int persons, int generations, String root, List<Share> shares, int grade) {
			this.title = title;
			this.persons = persons;
			this.generations = generations;
			this.root = root;
			this.shares = shares;
			this.grade = grade;
		}

		File salva() {
			File fileSettaggi = new File(Global.context.getCacheDir(), "settings.json");
			Gson gson = new Gson();
			String salvando = gson.toJson(this);
			try {
				FileUtils.writeStringToFile(fileSettaggi, salvando, "UTF-8");
			} catch( Exception e ) {}
			return fileSettaggi;
		}
	}
}