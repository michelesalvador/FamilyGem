package app.familygem;

import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.os.LocaleList;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.os.LocaleListCompat;

import org.xmlpull.v1.XmlPullParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class SettingsActivity extends BaseActivity {

    List<Language> languages;

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        setContentView(R.layout.opzioni);

        // Auto save
        SwitchCompat save = findViewById(R.id.opzioni_salva);
        save.setChecked(Global.settings.autoSave);
        save.setOnCheckedChangeListener((widget, active) -> {
            Global.settings.autoSave = active;
            Global.settings.save();
        });

        // Load tree at startup
        SwitchCompat load = findViewById(R.id.opzioni_carica);
        load.setChecked(Global.settings.loadTree);
        load.setOnCheckedChangeListener((widget, active) -> {
            Global.settings.loadTree = active;
            Global.settings.save();
        });

        // Expert mode
        SwitchCompat expert = findViewById(R.id.opzioni_esperto);
        expert.setChecked(Global.settings.expert);
        expert.setOnCheckedChangeListener((widget, active) -> {
            Global.settings.expert = active;
            Global.settings.save();
        });

        // Language picker
        languages = new ArrayList<>();
        languages.add(new Language(null, 0)); // System language
        try { // Gets languages from locales_config.xml
            XmlPullParser xpp = getResources().getXml(R.xml.locales_config);
            while (xpp.getEventType() != XmlPullParser.END_DOCUMENT) {
                if (xpp.getEventType() == XmlPullParser.START_TAG && xpp.getName().equals("locale")) {
                    String percent = xpp.getAttributeValue(null, "percent");
                    languages.add(new Language(xpp.getAttributeValue(0),
                            percent == null ? 100 : Integer.parseInt(percent)));
                }
                xpp.next();
            }
        } catch (Exception ignored) {
        }
        Collections.sort(languages);

        TextView textView = findViewById(R.id.opzioni_language);
        Language actual = getActualLanguage();
        textView.setText(actual.toString());
        String[] languageArray = new String[languages.size()];
        for (int i = 0; i < languages.size(); i++) {
            languageArray[i] = languages.get(i).toString();
        }
        textView.setOnClickListener(view -> new AlertDialog.Builder(view.getContext())
                .setSingleChoiceItems(languageArray, languages.indexOf(actual), (dialog, item) -> {
                    String code = languages.get(item).code;
                    // Sets app locale and store it for the future
                    LocaleListCompat appLocale = LocaleListCompat.forLanguageTags(code);
                    AppCompatDelegate.setApplicationLocales(appLocale);
                    // Updates app context configuration for this session only
                    Configuration configuration = getResources().getConfiguration();
                    if (code != null) {
                        configuration.setLocale(new Locale(code));
                    } else { // Takes the system locale
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            // Finds the first system locale supported by this app
                            Locale firstSupportedLocale = new Locale("en"); // English default language
                            LocaleList systemLocales = Resources.getSystem().getConfiguration().getLocales();
                            for (int i = 0; i < systemLocales.size(); i++) {
                                Locale sysLoc = systemLocales.get(i);
                                String tag = sysLoc.toLanguageTag();
                                if (languages.stream().anyMatch(lang -> lang.code != null && tag.startsWith(lang.code))) {
                                    firstSupportedLocale = new Locale(tag.substring(0, 2)); // Just the 2 chars language code
                                    break;
                                }
                            }
                            configuration.setLocale(firstSupportedLocale);
                        } else {
                            configuration.setLocale(Resources.getSystem().getConfiguration().locale);
                        }
                    }
                    Global.context = createConfigurationContext(configuration);
                    // Removes switches to force KitKat to update their language
                    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT) {
                        LinearLayout layout = findViewById(R.id.layout);
                        layout.removeView(save);
                        layout.removeView(load);
                        layout.removeView(expert);
                    }
                    dialog.dismiss();
                    if (code == null) recreate();
                }).show());

        // About
        findViewById(R.id.opzioni_lapide).setOnClickListener(view -> startActivity(
                new Intent(SettingsActivity.this, AboutActivity.class)
        ));
    }

    /**
     * Returns the actual Language of the app, otherwise the "system language"
     */
    private Language getActualLanguage() {
        Locale firstLocale = AppCompatDelegate.getApplicationLocales().get(0);
        if (firstLocale != null) {
            for (int i = 1; i < languages.size(); i++) {
                Language language = languages.get(i);
                if (firstLocale.toString().startsWith(language.code))
                    return language;
            }
        }
        return languages.get(0);
    }

    private class Language implements Comparable<Language> {
        String code;
        int percent;

        public Language(String code, int percent) {
            this.code = code;
            this.percent = percent;
        }

        @NonNull
        @Override
        public String toString() {
            if (code == null) {
                // Returns the string "System language" on the system locale, not on the app locale
                Configuration config = new Configuration(getResources().getConfiguration());
                config.setLocale(Resources.getSystem().getConfiguration().locale); // TODO: on API 33 is the app locale instead of the system locale
                return createConfigurationContext(config).getText(R.string.system_language).toString();
            } else {
                Locale locale = new Locale(code);
                String txt = locale.getDisplayLanguage(locale);
                txt = txt.substring(0, 1).toUpperCase() + txt.substring(1);
                if (percent < 100) {
                    txt += " (" + percent + "%)";
                }
                return txt;
            }
        }

        @Override
        public int compareTo(Language lang) {
            if (lang.code == null) {
                return 1;
            }
            return toString().compareTo(lang.toString());
        }
    }
}
