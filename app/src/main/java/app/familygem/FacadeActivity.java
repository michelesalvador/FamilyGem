package app.familygem;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import org.apache.commons.net.ftp.FTPClient;

import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Locale;

public class FacadeActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        setContentView(R.layout.facciata);

        // Set app locale for application context and resources (localized gedcom.jar library)
        Locale locale = AppCompatDelegate.getApplicationLocales().get(0); // Find app locale, or null if not existing
        if (locale != null) {
            Configuration config = getResources().getConfiguration();
            config.setLocale(locale);
            getApplicationContext().getResources().updateConfiguration(config, null); // Change locale both for static methods and jar library
        }

		/*
		Opening after clicking on various types of links:
		https://www.familygem.app/share.php?tree=20190802224208
			Short message
			Clicked in Chrome in old Android opens the choice of the app including Family Gem to directly import the tree
			Normally opens the site sharing page
		intent: //www.familygem.app/condivisi/20200218134922.zip#Intent; scheme = https; end
			Official link on the site's sharing page
			it is the only one that seems to be sure that it works, in Chrome, in the browser inside Libero, in the L90 Browser
		https://www.familygem.app/condivisi/20190802224208.zip
			Direct URL to the zip
			It works in old android, in new ones simply the file is downloaded

		Apertura in seguito al click su vari tipi di link:
		https://www.familygem.app/share.php?tree=20190802224208
			Messaggio breve
			Cliccato in Chrome nei vecchi Android apre la scelta dell'app tra cui Family Gem per importare direttamente l'albero
			Normalmente apre la pagina di condivisione del sito
		intent://www.familygem.app/condivisi/20200218134922.zip#Intent;scheme=https;end
			Link ufficiale nella pagina di condivisione del sito
			è l'unico che sembra avere certezza di funzionare, in Chrome, nel browser interno a Libero, nel Browser L90
		https://www.familygem.app/condivisi/20190802224208.zip
			URL diretto allo zip
			Funziona nei vecchi android, nei nuovi semplicemente il file viene scaricato
		*/
        Intent intent = getIntent();
        Uri uri = intent.getData();
        // By opening the app from Task Manager, avoid re-importing a newly imported shared tree
        boolean fromHistory = (intent.getFlags() & Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) == Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY;
        if (uri != null && !fromHistory) {
            String dataId;
            if (uri.getPath().equals("/share.php")) // click on the first message received
                dataId = uri.getQueryParameter("tree");
            else if (uri.getLastPathSegment().endsWith(".zip")) // click on the invitation page
                dataId = uri.getLastPathSegment().replace(".zip", "");
            else {
                U.toast(this, R.string.cant_understand_uri);
                return;
            }
            if (!BuildConfig.utenteAruba.isEmpty()) {
                // It does not need to apply for permissions
                downloadShared(this, dataId, null);
            }
        } else {
            Intent treesIntent = new Intent(this, TreesActivity.class);
            // Open last tree at startup
            if (Global.settings.loadTree) {
                treesIntent.putExtra("apriAlberoAutomaticamente", true);
                treesIntent.setFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION); // perhaps ineffective but so be it
            }
            startActivity(treesIntent);
        }
    }

    /**
	 * Connects to the server and downloads the zip file to import it
	 * */
    static void downloadShared(Context context, String idData, View wheel) {
        if (wheel != null)
            wheel.setVisibility(View.VISIBLE);
        // A new Thread is needed to asynchronously download a file
        new Thread(() -> {
            try {
                FTPClient client = new FTPClient(); //TODO refactor to use Retrofit
                client.connect("89.46.104.211");
                client.enterLocalPassiveMode();
                client.login(BuildConfig.utenteAruba, BuildConfig.passwordAruba);
                // Todo: Maybe you could use the download manager so that you have the file also listed in 'Downloads'
                String zipPath = context.getExternalCacheDir() + "/" + idData + ".zip";
                FileOutputStream fos = new FileOutputStream(zipPath);
                String path = "/www.familygem.app/condivisi/" + idData + ".zip";
                InputStream input = client.retrieveFileStream(path);
                if (input != null) {
                    byte[] data = new byte[1024];
                    int count;
                    while ((count = input.read(data)) != -1) {
                        fos.write(data, 0, count);
                    }
                    fos.close();
                    if (client.completePendingCommand() && NewTree.unZip(context, zipPath, null)) {
                        //If the tree was downloaded with the install referrer // Se l'albero è stato scaricato con l'install referrer
                        if (Global.settings.referrer != null && Global.settings.referrer.equals(idData)) {
                            Global.settings.referrer = null;
                            Global.settings.save();
                        }
                    } else { // Failed decompression of downloaded ZIP (e.g. corrupted file)
                        downloadFailed(context, context.getString(R.string.backup_invalid), wheel);
                    }
                } else // Did not find the file on the server
                    downloadFailed(context, context.getString(R.string.something_wrong), wheel);
                client.logout();
                client.disconnect();
            } catch (Exception e) {
                downloadFailed(context, e.getLocalizedMessage(), wheel);
            }
        }).start();
    }

    /**
	 * Negative conclusion of the above method
	 * */
    static void downloadFailed(Context context, String message, View wheel) {
        U.toast((Activity) context, message);
        if (wheel != null)
            ((Activity) context).runOnUiThread(() -> wheel.setVisibility(View.GONE));
        else
            context.startActivity(new Intent(context, TreesActivity.class));
    }
}
