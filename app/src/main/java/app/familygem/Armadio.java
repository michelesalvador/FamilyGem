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
	int idAprendo;
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
		salva();
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
			FileUtils.writeStringToFile( new File( Globale.contesto.getFilesDir(), "preferenze.json"), salvando );
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public Cassetto alberoAperto() {
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

	public static class Cassetto {
		int id;
		String nome;
		@Deprecated
		String cartella;
		LinkedHashSet<String> cartelle;
		int individui;
		int generazioni;
		int media;
		String radice;
		List<String> preferiti;

		Cassetto( int id, String nome, String cartella, int individui, int generazioni, String radice, List<String> preferiti ) {
			this.id = id;
			this.nome = nome;
			//this.cartella = cartella;
			cartelle = new LinkedHashSet<>();
			if( cartella != null )
				cartelle.add( cartella );
			this.individui = individui;
			this.generazioni = generazioni;
			this.radice = radice;
			this.preferiti = preferiti;
		}
	}
}