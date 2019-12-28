package app.familygem;

import android.app.backup.BackupAgentHelper;
import android.app.backup.FileBackupHelper;
import java.io.File;
import java.util.HashSet;
import java.util.Set;

public class AgenteBackup extends BackupAgentHelper {

	@Override
	public void onCreate () {

		Set<String> nomiFile = new HashSet<>();

		// Lista dei Json degli alberi + preferenze esistenti nello storage interno
		File[] listaFile = getFilesDir().listFiles();
		if( listaFile != null ) {
			for( File file : listaFile ) {
				if( file.isFile() && file.getName().endsWith( ".json" )  )
					nomiFile.add( file.getName() );
			}
		}
		// altrimenti si può usare 'fileList()' che lista i nomi di tutti i file in /data/data/files

		// Se non esistono file potrebbe esserci un backup da recuperare..
		// perciò crea la lista con tutti i nomi possibili di file recuperabili
		if( nomiFile.isEmpty() ) {
			for( int i = 1; i < 100; i++ ) {
				nomiFile.add( i + ".json" );
			}
			nomiFile.add( "preferenze.json" );
		}

		String[] arrayNomiFile = nomiFile.toArray(new String[0]);
		FileBackupHelper aiutante = new FileBackupHelper( this, arrayNomiFile );
		addHelper("alberi", aiutante);

		for(String str : arrayNomiFile)
			s.l(">",str);
		s.l("Fine onCreate.");
	}
}
