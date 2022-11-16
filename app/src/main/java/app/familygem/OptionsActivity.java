package app.familygem;

import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.os.LocaleListCompat;
import android.os.LocaleList;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Arrays;
import java.util.Locale;

public class OptionsActivity extends BaseActivity {

	Language[] languages = {
			new Language(null, 0), // System language
			new Language("cs", 100),
			new Language("de", 100),
			new Language("en", 100),
			new Language("eo", 100),
			new Language("es", 100),
			new Language("fa", 100),
			new Language("fr", 100),
			new Language("hr", 100),
			new Language("hu", 100),
			new Language("in", 100),
			new Language("it", 100),
			new Language("iw", 100),
			new Language("kn", 18),
			new Language("mr", 13),
			new Language("nb", 100),
			new Language("nl", 100),
			new Language("pl", 100),
			new Language("pt", 100),
			new Language("ru", 100),
			new Language("sk", 100),
			new Language("sr", 100),
			new Language("tr", 21),
			new Language("uk", 100)
	};

	@Override
	protected void onCreate(Bundle bundle) {
		super.onCreate(bundle);
		setContentView(R.layout.opzioni);

		// Auto save
		SwitchCompat save = findViewById(R.id.opzioni_salva);
		save.setChecked(Global.settings.autoSave);
		save.setOnCheckedChangeListener((button, isChecked) -> {
			Global.settings.autoSave = isChecked;
			Global.settings.save();
		});

		// Load tree at startup
		SwitchCompat load = findViewById(R.id.opzioni_carica);
		load.setChecked(Global.settings.loadTree);
		load.setOnCheckedChangeListener((button, isChecked) -> {
			Global.settings.loadTree = isChecked;
			Global.settings.save();
		});

		// Expert mode
		SwitchCompat expert = findViewById(R.id.opzioni_esperto);
		expert.setChecked(Global.settings.expert);
		expert.setOnCheckedChangeListener((button, isChecked) -> {
			Global.settings.expert = isChecked;
			Global.settings.save();
		});

		Arrays.sort(languages);
		TextView textView = findViewById(R.id.opzioni_language);
		Language actual = getActualLanguage();
		textView.setText(actual.toString());
		String[] languageArr = new String[languages.length];
		for( int i = 0; i < languages.length; i++ ) {
			languageArr[i] = languages[i].toString();
		}
		textView.setOnClickListener(view -> new AlertDialog.Builder(view.getContext())
				.setSingleChoiceItems(languageArr, Arrays.asList(languages).indexOf(actual), (dialog, item) -> {
					String code = languages[item].code;
					// Set app locale and store it for the future
					LocaleListCompat appLocale = LocaleListCompat.forLanguageTags(code);
					AppCompatDelegate.setApplicationLocales(appLocale);
					// Update app context configuration for this session only
					Configuration configuration = getResources().getConfiguration();
					if( code != null ) {
						configuration.setLocale(new Locale(code));
					} else { // Take the system locale
						if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.N ) {
							// Find the first system locale supported by this app
							Locale firstSupportedLocale = new Locale("en"); // English default language
							LocaleList systemLocales = Resources.getSystem().getConfiguration().getLocales();
							for( int i = 0; i < systemLocales.size(); i++ ) {
								Locale sysLoc = systemLocales.get(i);
								String tag = sysLoc.toLanguageTag();
								if( Arrays.stream(languages).anyMatch(lang -> lang.code != null && tag.startsWith(lang.code)) ) {
									firstSupportedLocale = new Locale(tag.substring(0, 2)); // Just the 2 chars language code
									break;
								}
							}
							configuration.setLocale(firstSupportedLocale);
						} else {
							configuration.setLocale(Resources.getSystem().getConfiguration().locale);
						}
					}
					getApplicationContext().getResources().updateConfiguration(configuration, null);
					// Remove switches to force KitKat to update their language
					if( Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT ) {
						LinearLayout layout = findViewById(R.id.layout);
						layout.removeView(save);
						layout.removeView(load);
						layout.removeView(expert);
					}
					dialog.dismiss();
					if( code == null ) recreate();
				}).show());

		findViewById(R.id.opzioni_lapide).setOnClickListener(view -> startActivity(
				new Intent(OptionsActivity.this, TombstoneActivity.class)
		));
	}

	/**
	 * Return the actual Language of the app, otherwise the "system language"
	 */
	private Language getActualLanguage() {
		Locale firstLocale = AppCompatDelegate.getApplicationLocales().get(0);
		if( firstLocale != null ) {
			for( int i = 1; i < languages.length; i++ ) {
				Language language = languages[i];
				if( firstLocale.toString().startsWith(language.code) )
					return language;
			}
		}
		return languages[0];
	}

	private class Language implements Comparable<Language> {
		String code;
		int percent;
		public Language(String code, int percent) {
			this.code = code;
			this.percent = percent;
		}
		@Override
		public String toString() {
			if( code == null ) {
				// Return the string "System language" on the system locale, not on the app locale
				Configuration config = new Configuration(getResources().getConfiguration());
				config.setLocale(Resources.getSystem().getConfiguration().locale);
				return createConfigurationContext(config).getText(R.string.system_language).toString();
			} else {
				Locale locale = new Locale(code);
				String txt = locale.getDisplayLanguage(locale);
				txt = txt.substring(0, 1).toUpperCase() + txt.substring(1);
				if( percent < 100 ) {
					txt += " (" + percent + "%)";
				}
				return txt;
			}
		}
		@Override
		public int compareTo(Language lang) {
			if( lang.code == null ) {
				return 1;
			}
			return toString().compareTo(lang.toString());
		}
	}
}
