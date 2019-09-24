// Classe per gestire le preferenze salvate in preferenze.json
package app.familygem;

import com.google.gson.Gson;
import org.apache.commons.io.FileUtils;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public class Armadio {

	List<Cassetto> alberi;
	public int idAprendo;
	boolean autoSalva;
	boolean caricaAlbero;
	public boolean esperto;

	void traghetta() { // todo questo traghettatore poi se ne può andare
		Cassetto alb = alberoAperto();
		if( alb.cartelle == null ) {
			alb.cartelle = new LinkedHashSet<>();
			alb.cartelle.add( alb.cartella );
			alb.cartella = null;
			salva();
		}
	}

	int max() {
		int num = 0;
		for( Cassetto c : alberi ) {
			if( c.id > num )
				num = c.id;
		}
		return num;
	}

	void aggiungi( Cassetto c ) {
		alberi.add( c );
	}

	void rinomina( int id, String nuovoNome ) {
		for( Cassetto c : alberi ) {
			if (c.id == id) {
				c.nome = nuovoNome;
				break;
			}
		}
		salva();
	}

	void elimina( int id ) {
		for( Cassetto c : alberi ) {
			if( c.id == id ) {
				alberi.remove( c );
				break;
			}
		}
		if( idAprendo == id ) {
			if( alberi.isEmpty() )
				idAprendo = 0;
			else
				idAprendo = alberi.get(0).id;
		}
		salva();
	}

	public void salva() {
		try {
			Gson gson = new Gson();
			String salvando = gson.toJson( this );
			FileUtils.writeStringToFile( new File( Globale.contesto.getFilesDir(), "preferenze.json"), salvando, "UTF-8" );
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	Cassetto alberoAperto() {
		for( Cassetto alb : alberi ) {
			if( alb.id == idAprendo )
				return alb;
		}
		return null;
	}

	Cassetto getAlbero( int idAlbero ) {
		for( Cassetto cass : alberi ) {
			if( cass.id == idAlbero )
				return cass;
		}
		return null;
	}

	/*void setStato( int grado ) {
		alberoAperto().grado = grado;
		salva();
	}
	int getGrado() {
		return alberoAperto().grado;
	}*/
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

	static class Cassetto {
		int id;
		String nome;   // todo -> titolo
		@Deprecated
		String cartella;
		LinkedHashSet<String> cartelle;
		int individui;
		int generazioni;
		int media;
		String radice;
		List<Invio> condivisioni; // dati identificativi delle condivisioni attraverso il tempo e lo spazio
		String radiceCondivisione; // id della Person radice dell'albero in Condivisione
		int grado; // grado della condivisione
		List<String> preferiti;

		Cassetto( int id, String nome, String cartella, int individui, int generazioni, String radice, List<Invio> condivisioni, int grado, List<String> preferiti ) {
			this.id = id;
			this.nome = nome;   // todo -> titolo
			cartelle = new LinkedHashSet<>();
			if( cartella != null )
				cartelle.add( cartella );
			this.individui = individui;
			this.generazioni = generazioni;
			this.radice = radice;
			this.condivisioni = condivisioni;
			this.grado = grado;
			this.preferiti = preferiti;
		}

		void aggiungiCondivisione( Invio invio ) {
			if( condivisioni == null )
				condivisioni = new ArrayList<>();
			condivisioni.add( invio );
		}
	}

	// I dati di una condivisione
	static class Invio {
		String data; // nel formato data compressa: AAAAMMGGhhmmss
		String submitter; // id del Submitter
		Invio( String data, String submitter ) {
			this.data = data;
			this.submitter = submitter;
		}
	}

	// Questa classe serve per creare il file 'settings.json' che contiene le info base di un albero zippato
	static class CassettoCondiviso {
		String titolo;
		int individui;
		int generazioni;
		String radice;
		List<Invio> condivisioni;
		int grado; // il grado di destinazione dell'albero zippato
		CassettoCondiviso( String titolo, int individui, int generazioni, String radice, List<Invio> condivisioni, int grado ) {
			this.titolo = titolo;
			this.individui = individui;
			this.generazioni = generazioni;
			this.radice = radice;
			this.condivisioni = condivisioni;
			this.grado = grado;
		}
		void salva() {
			try {
				Gson gson = new Gson();
				String salvando = gson.toJson( this );
				FileUtils.writeStringToFile( new File( Globale.contesto.getCacheDir(), "settings.json"), salvando, "UTF-8" );
			} catch (IOException e) {}
		}
	}
}