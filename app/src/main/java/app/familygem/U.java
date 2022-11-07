// Static methods used all across the app

package app.familygem;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.fragment.app.Fragment;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.textfield.TextInputLayout;
import com.google.gson.JsonPrimitive;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import org.apache.commons.io.FileUtils;
import org.folg.gedcom.model.Change;
import org.folg.gedcom.model.ChildRef;
import org.folg.gedcom.model.DateTime;
import org.folg.gedcom.model.Family;
import org.folg.gedcom.model.Header;
import org.folg.gedcom.model.ParentFamilyRef;
import org.folg.gedcom.model.PersonFamilyCommonContainer;
import org.folg.gedcom.model.Repository;
import org.folg.gedcom.model.RepositoryRef;
import org.folg.gedcom.model.SpouseFamilyRef;
import org.folg.gedcom.model.SpouseRef;
import org.folg.gedcom.model.Submitter;
import org.folg.gedcom.parser.ModelParser;
import org.joda.time.Days;
import org.joda.time.LocalDate;
import org.folg.gedcom.model.EventFact;
import org.folg.gedcom.model.ExtensionContainer;
import org.folg.gedcom.model.Gedcom;
import org.folg.gedcom.model.GedcomTag;
import org.folg.gedcom.model.Media;
import org.folg.gedcom.model.MediaContainer;
import org.folg.gedcom.model.Name;
import org.folg.gedcom.model.Note;
import org.folg.gedcom.model.NoteContainer;
import org.folg.gedcom.model.NoteRef;
import org.folg.gedcom.model.Person;
import org.folg.gedcom.model.Source;
import org.folg.gedcom.model.SourceCitation;
import org.folg.gedcom.model.SourceCitationContainer;
import org.folg.gedcom.parser.JsonParser;
import org.joda.time.Months;
import org.joda.time.Years;
import app.familygem.constant.Format;
import app.familygem.constant.Gender;
import app.familygem.detail.RepositoryRefActivity;
import app.familygem.detail.AuthorActivity;
import app.familygem.detail.ChangesActivity;
import app.familygem.detail.SourceCitationActivity;
import app.familygem.detail.FamilyActivity;
import app.familygem.detail.SourceActivity;
import app.familygem.detail.ImageActivity;
import app.familygem.detail.NoteActivity;
import app.familygem.visitor.MediaContainers;
import app.familygem.visitor.NoteContainers;
import app.familygem.visitor.ListOfSourceCitations;
import app.familygem.visitor.MediaListContainer;
import app.familygem.visitor.NoteReferences;
import app.familygem.visitor.FindStack;

public class U {

	public static String s(int id) {
		return Global.context.getString(id);
	}

	/**
	 * To use where it happens that 'Global.gc' could be null to reload it
	 * */
	static void ensureGlobalGedcomNotNull(Gedcom gc) {
		if( gc == null )
			Global.gc = TreesActivity.readJson(Global.settings.openTree);
	}

	// Id of the main person of a GEDCOM or null
	static String getRootId(Gedcom gedcom, Settings.Tree tree) {
		if( tree.root != null ) {
			Person root = gedcom.getPerson(tree.root);
			if( root != null )
				return root.getId();
		}
		return trovaRadice(gedcom);
	}

	// restituisce l'id della Person iniziale di un Gedcom
	// Todo Integrate into getRootId(Gedcom,Tree) ???
	static String trovaRadice(Gedcom gc) {
		if( gc.getHeader() != null )
			if( valoreTag(gc.getHeader().getExtensions(), "_ROOT") != null )
				return valoreTag(gc.getHeader().getExtensions(), "_ROOT");
		if( !gc.getPeople().isEmpty() )
			return gc.getPeople().get(0).getId();
		return null;
	}

	/**
	 * receives a Person and returns string with primary first and last name
	 * riceve una Person e restituisce stringa con nome e cognome principale
	 * */
	static String properName(Person person) {
		return properName(person, false);
	}
	static String properName(Person person, boolean twoLines) {
		if( person != null && !person.getNames().isEmpty() )
			return firstAndLastName(person.getNames().get(0), twoLines ? "\n" : " ");
		return "[" + s(R.string.no_name) + "]";
	}

	// The given name of a person or something
	static String givenName(Person person) {
		if( person.getNames().isEmpty() ) {
			return "[" + s(R.string.no_name) + "]";
		} else {
			String given = "";
			Name name = person.getNames().get(0);
			if( name.getValue() != null ) {
				String value = name.getValue().trim();
				if( value.indexOf('/') == 0 && value.lastIndexOf('/') == 1 && value.length() > 2 ) // Suffix only
					given = value.substring(2);
				else if( value.indexOf('/') == 0 && value.lastIndexOf('/') > 1 ) // Surname only
					given = value.substring(1, value.lastIndexOf('/'));
				else if( value.indexOf('/') > 0 ) // Name and surname
					given = value.substring(0, value.indexOf('/'));
				else if( !value.isEmpty() ) // Name only
					given = value;
			} else if( name.getGiven() != null ) {
				given = name.getGiven();
			} else if( name.getSurname() != null ) {
				given = name.getSurname();
			}
			given = given.trim();
			return given.isEmpty() ? "[" + s(R.string.empty_name) + "]" : given;
		}
	}

	// riceve una Person e restituisce il titolo nobiliare
	static String titolo(Person p) {
		// GEDCOM standard INDI.TITL
		for( EventFact ef : p.getEventsFacts() )
			if( ef.getTag() != null && ef.getTag().equals("TITL") && ef.getValue() != null )
				return ef.getValue();
		// Così invece prende INDI.NAME._TYPE.TITL, vecchio metodo di org.folg.gedcom
		for( Name n : p.getNames() )
			if( n.getType() != null && n.getType().equals("TITL") && n.getValue() != null )
				return n.getValue();
		return "";
	}

	/**
	 * Returns the first and last name decorated with a Name
	 * Restituisce il nome e cognome addobbato di un Name
	 * */
	static String firstAndLastName(Name n, String divider) {
		String completo = "";
		if( n.getValue() != null ) {
			String grezzo = n.getValue().trim();
			int slashPos = grezzo.indexOf('/');
			int lastSlashPos = grezzo.lastIndexOf('/');
			if( slashPos > -1 ) // Se c'è un cognome tra '/'
				completo = grezzo.substring(0, slashPos).trim(); // nome
			else // Oppure è solo nome senza cognome
				completo = grezzo;
			if( n.getNickname() != null )
				completo += divider + "\"" + n.getNickname() + "\"";
			if( slashPos < lastSlashPos )
				completo += divider + grezzo.substring(slashPos + 1, lastSlashPos).trim(); // cognome
			if( lastSlashPos > -1 && grezzo.length() - 1 > lastSlashPos )
				completo += " " + grezzo.substring(lastSlashPos + 1).trim(); // dopo il cognome
		} else {
			if( n.getPrefix() != null )
				completo = n.getPrefix();
			if( n.getGiven() != null )
				completo += " " + n.getGiven();
			if( n.getNickname() != null )
				completo += divider + "\"" + n.getNickname() + "\"";
			if( n.getSurname() != null )
				completo += divider + n.getSurname();
			if( n.getSuffix() != null )
				completo += " " + n.getSuffix();
		}
		completo = completo.trim();
		return completo.isEmpty() ? "[" + s(R.string.empty_name) + "]" : completo;
	}

	// Return the surname of a person, optionally lowercase for comparaison. Can return null.
	static String surname(Person person) {
		return surname(person, false);
	}
	static String surname(Person person, boolean lowerCase) {
		String surname = null;
		if( !person.getNames().isEmpty() ) {
			Name name = person.getNames().get(0);
			String value = name.getValue();
			if( value != null && value.lastIndexOf('/') - value.indexOf('/') > 1  ) //value.indexOf('/') < value.lastIndexOf('/')
				surname = value.substring(value.indexOf('/') + 1, value.lastIndexOf('/'));
			else if( name.getSurname() != null )
				surname = name.getSurname();
		}
		if( lowerCase && surname != null )
			surname = surname.toLowerCase();
		return surname;
	}

	// Riceve una person e trova se è morto o seppellito
	static boolean isDead(Person person) {
		for( EventFact eventFact : person.getEventsFacts() ) {
			if( eventFact.getTag().equals("DEAT") || eventFact.getTag().equals("BURI") )
				return true;
		}
		return false;
	}

	// Check whether a family has a marriage event of type 'marriage'
	public static boolean areMarried(Family family) {
		if( family != null ) {
			for( EventFact eventFact : family.getEventsFacts() ) {
				String tag = eventFact.getTag();
				if( tag.equals("MARR") ) {
					String type = eventFact.getType();
					if( type == null || type.isEmpty() || type.equals("marriage")
							|| type.equals("civil") || type.equals("religious") || type.equals("common law") )
						return true;
				} else if( tag.equals("MARB") || tag.equals("MARC") || tag.equals("MARL") || tag.equals("MARS") )
					return true;
			}
		}
		return false;
	}

	/** Write the basic dates of a person's life with the age.
	 * @param person The dude to investigate
	 * @param vertical Dates and age can be written on multiple lines
	 * @return A string with date of birth an death
	 */
	static String twoDates(Person person, boolean vertical) {
		String text = "";
		String endYear = "";
		GedcomDateConverter start = null, end = null;
		boolean ageBelow = false;
		List<EventFact> facts = person.getEventsFacts();
		// Birth date
		for( EventFact fact : facts ) {
			if( fact.getTag() != null && fact.getTag().equals("BIRT") && fact.getDate() != null ) {
				start = new GedcomDateConverter(fact.getDate());
				text = start.writeDate(false);
				break;
			}
		}
		// Death date
		for( EventFact fact : facts ) {
			if( fact.getTag() != null && fact.getTag().equals("DEAT") && fact.getDate() != null ) {
				end = new GedcomDateConverter(fact.getDate());
				endYear = end.writeDate(false);
				if( !text.isEmpty() && !endYear.isEmpty() ) {
					if( vertical && (text.length() > 7 || endYear.length() > 7) ) {
						text += "\n";
						ageBelow = true;
					} else {
						text += " – ";
					}
				}
				text += endYear;
				break;
			}
		}
		// Otherwise find the first available date
		if( text.isEmpty() ) {
			for( EventFact fact : facts ) {
				if( fact.getDate() != null ) {
					return new GedcomDateConverter(fact.getDate()).writeDate(false);
				}
			}
		}
		// Add the age between parentheses
		if( start != null && start.isSingleKind() && !start.data1.isFormat(Format.D_M) ) {
			LocalDate startDate = new LocalDate(start.data1.date); // Converted to joda time
			// If the person is still alive the end is now
			LocalDate now = LocalDate.now();
			if( end == null && (startDate.isBefore(now) || startDate.isEqual(now))
					&& Years.yearsBetween(startDate, now).getYears() <= 120 && !isDead(person) ) {
				end = new GedcomDateConverter(now.toDate());
				endYear = end.writeDate(false);
			}
			if( end != null && end.isSingleKind() && !end.data1.isFormat(Format.D_M) && !endYear.isEmpty() ) { // Plausible dates
				LocalDate endDate = new LocalDate(end.data1.date);
				if( startDate.isBefore(endDate) || startDate.isEqual(endDate) ) {
					String units = "";
					int age = Years.yearsBetween(startDate, endDate).getYears();
					if( age < 2 ) {
						// Without day and/or month the years start at 1 January
						age = Months.monthsBetween(startDate, endDate).getMonths();
						units = " " + Global.context.getText(R.string.months);
						if( age < 2 ) {
							age = Days.daysBetween(startDate, endDate).getDays();
							units = " " + Global.context.getText(R.string.days);
						}
					}
					if( ageBelow )
						text += "\n";
					else
						text += " ";
					text += "(" + age + units + ")";
				}
			}
		}
		return text;
	}

	// Write the two main places of a person (initial – final) or null
	static String twoPlaces(Person person) {
		List<EventFact> facts = person.getEventsFacts();
		// One single event
		if( facts.size() == 1 ) {
			String place = facts.get(0).getPlace();
			if( place != null )
				return stripCommas(place);
		} // Sex and another event
		else if( facts.size() == 2 && ("SEX".equals(facts.get(0).getTag()) || "SEX".equals(facts.get(1).getTag())) ) {
			String place;
			if( "SEX".equals(facts.get(0).getTag()) )
				place = facts.get(1).getPlace();
			else
				place = facts.get(0).getPlace();
			if( place != null )
				return stripCommas(place);
		} // Multiple events
		else if( facts.size() >= 2 ) {
			String[] places = new String[7];
			for( EventFact ef : facts ) {
				String place = ef.getPlace();
				if( place != null ) {
					switch( ef.getTag() ) {
						case "BIRT":
							places[0] = place;
							break;
						case "BAPM":
							places[1] = place;
							break;
						case "DEAT":
							places[4] = place;
							break;
						case "CREM":
							places[5] = place;
							break;
						case "BURI":
							places[6] = place;
							break;
						default:
							if( places[2] == null ) // First of other events
								places[2] = place;
							if( !place.equals(places[2]) )
								places[3] = place; // Last of other events
					}
				}
			}
			String text = null;
			int i;
			// Write initial place
			for( i = 0; i < places.length; i++ ) {
				String place = places[i];
				if( place != null ) {
					text = stripCommas(place);
					break;
				}
			}
			// Priority to death event as final place
			if( text != null && i < 4 && places[4] != null ) {
				String place = stripCommas(places[4]);
				if( !place.equals(text) )
					text += " – " + place;
			} else {
				for( int j = places.length - 1; j > i; j-- ) {
					String place = places[j];
					if( place != null ) {
						place = stripCommas(place);
						if( !place.equals(text) ) {
							text += " – " + place;
							break;
						}
					}
				}
			}
			return text;
		}
		return null;
	}

	// riceve un luogo stile Gedcom e restituisce il primo nome tra le virgole
	private static String stripCommas(String place) {
		// salta le virgole iniziali per luoghi type ',,,England'
		int start = 0;
		for( char c : place.toCharArray() ) {
			if( c != ',' && c != ' ' )
				break;
			start++;
		}
		place = place.substring(start);
		if( place.indexOf(",") > 0 )
			place = place.substring(0, place.indexOf(","));
		return place;
	}

	/**
	 * Extracts only numbers from a string that can also contain letters
	 * Estrae i soli numeri da una stringa che può contenere anche lettere
	 * */
	static int extractNum(String id) {
		//return Integer.parseInt( id.replaceAll("\\D+","") );	// synthetic but slow //sintetico ma lento
		int num = 0;
		int x = 1;
		for( int i = id.length() - 1; i >= 0; --i ) {
			int c = id.charAt(i);
			if( c > 47 && c < 58 ) {
				num += (c - 48) * x;
				x *= 10; //to convert positional notation into a base-10 representation
			}
		}
		return num;
	}

	// Genera il nuovo id seguente a quelli già esistenti
	static int max;
	public static String newID(Gedcom gc, Class classe) {
		max = 0;
		String pre = "";
		if( classe == Note.class ) {
			pre = "N";
			for( Note n : gc.getNotes() )
				calcolaMax(n);
		} else if( classe == Submitter.class ) {
			pre = "U";
			for( Submitter a : gc.getSubmitters() )
				calcolaMax(a);
		} else if( classe == Repository.class ) {
			pre = "R";
			for( Repository r : gc.getRepositories() )
				calcolaMax(r);
		} else if( classe == Media.class ) {
			pre = "M";
			for( Media m : gc.getMedia() )
				calcolaMax(m);
		} else if( classe == Source.class ) {
			pre = "S";
			for( Source f : gc.getSources() )
				calcolaMax(f);
		} else if( classe == Person.class ) {
			pre = "I";
			for( Person p : gc.getPeople() )
				calcolaMax(p);
		} else if( classe == Family.class ) {
			pre = "F";
			for( Family f : gc.getFamilies() )
				calcolaMax(f);
		}
		return pre + (max + 1);
	}

	private static void calcolaMax(Object object) {
		try {
			String idStringa = (String)object.getClass().getMethod("getId").invoke(object);
			int num = extractNum(idStringa);
			if( num > max ) max = num;
		} catch( Exception e ) {
			e.printStackTrace();
		}
	}

	/**
	* Copy text to clipboard
	* */
	static void copyToClipboard(CharSequence label, CharSequence text) {
		ClipboardManager clipboard = (ClipboardManager)Global.context.getSystemService(Context.CLIPBOARD_SERVICE);
		ClipData clip = ClipData.newPlainText(label, text);
		if( clipboard != null ) clipboard.setPrimaryClip(clip);
	}

	// Restituisce la lista di estensioni
	@SuppressWarnings("unchecked")
	public static List<Extension> findExtensions(ExtensionContainer contenitore) {
		if( contenitore.getExtension(ModelParser.MORE_TAGS_EXTENSION_KEY) != null ) {
			List<Extension> lista = new ArrayList<>();
			for( GedcomTag est : (List<GedcomTag>)contenitore.getExtension(ModelParser.MORE_TAGS_EXTENSION_KEY) ) {
				String testo = traverseExtension(est, 0);
				if( testo.endsWith("\n") )
					testo = testo.substring(0, testo.length() - 1);
				lista.add(new Extension(est.getTag(), testo, est));
			}
			return lista;
		}
		return Collections.emptyList();
	}

	/**
	 * Constructs a text with the recursive content of the extension
	 * */
	public static String traverseExtension(GedcomTag pacco, int grado) {
		StringBuilder testo = new StringBuilder();
		if( grado > 0 )
			testo.append(pacco.getTag()).append(" ");
		if( pacco.getValue() != null )
			testo.append(pacco.getValue()).append("\n");
		else if( pacco.getId() != null )
			testo.append(pacco.getId()).append("\n");
		else if( pacco.getRef() != null )
			testo.append(pacco.getRef()).append("\n");
		for( GedcomTag unPezzo : pacco.getChildren() )
			testo.append(traverseExtension(unPezzo, ++grado));
		return testo.toString();
	}

	public static void deleteExtension(GedcomTag estensione, Object contenitore, View vista) {
		if( contenitore instanceof ExtensionContainer ) { // IndividuoEventi
			ExtensionContainer exc = (ExtensionContainer)contenitore;
			@SuppressWarnings("unchecked")
			List<GedcomTag> lista = (List<GedcomTag>)exc.getExtension(ModelParser.MORE_TAGS_EXTENSION_KEY);
			lista.remove(estensione);
			if( lista.isEmpty() )
				exc.getExtensions().remove(ModelParser.MORE_TAGS_EXTENSION_KEY);
			if( exc.getExtensions().isEmpty() )
				exc.setExtensions(null);
		} else if( contenitore instanceof GedcomTag ) { // Dettaglio
			GedcomTag gt = (GedcomTag)contenitore;
			gt.getChildren().remove(estensione);
			if( gt.getChildren().isEmpty() )
				gt.setChildren(null);
		}
		Memory.setInstanceAndAllSubsequentToNull(estensione);
		if( vista != null )
			vista.setVisibility(View.GONE);
	}

	// Restituisce il valore di un determinato tag in una estensione (GedcomTag)
	@SuppressWarnings("unchecked")
	static String valoreTag(Map<String, Object> mappaEstensioni, String nomeTag) {
		for( Map.Entry<String, Object> estensione : mappaEstensioni.entrySet() ) {
			List<GedcomTag> listaTag = (ArrayList<GedcomTag>)estensione.getValue();
			for( GedcomTag unPezzo : listaTag ) {
				//l( unPezzo.getTag() +" "+ unPezzo.getValue() );
				if( unPezzo.getTag().equals(nomeTag) ) {
					if( unPezzo.getId() != null )
						return unPezzo.getId();
					else if( unPezzo.getRef() != null )
						return unPezzo.getRef();
					else
						return unPezzo.getValue();
				}
			}
		}
		return null;
	}

	// Metodi di creazione di elementi di lista

	/**
	 * Add a generic title-text entry to a Layout. Used seriously only by [ChangesActivity]
	 * */
	public static void place(LinearLayout scatola, String tit, String testo) {
		View vistaPezzo = LayoutInflater.from(scatola.getContext()).inflate(R.layout.pezzo_fatto, scatola, false);
		scatola.addView(vistaPezzo);
		((TextView)vistaPezzo.findViewById(R.id.fatto_titolo)).setText(tit);
		TextView vistaTesto = vistaPezzo.findViewById(R.id.fatto_testo);
		if( testo == null ) vistaTesto.setVisibility(View.GONE);
		else {
			vistaTesto.setText(testo);
			//((TextView)vistaPezzo.findViewById( R.id.fatto_edita )).setText( testo );
		}
		//((Activity)scatola.getContext()).registerForContextMenu( vistaPezzo );
	}

	// Compone il testo coi dettagli di un individuo e lo mette nella vista testo
	// inoltre restituisce lo stesso testo per Confrontatore
	static String details(Person person, TextView detailsView) {
		String dates = twoDates(person, false);
		String places = twoPlaces(person);
		if( dates.isEmpty() && places == null && detailsView != null ) {
			detailsView.setVisibility(View.GONE);
		} else {
			if( !dates.isEmpty() && places != null && (dates.length() >= 10 || places.length() >= 20) )
				dates += "\n" + places;
			else if( places != null )
				dates += "   " + places;
			if( detailsView != null ) {
				detailsView.setText(dates.trim());
				detailsView.setVisibility(View.VISIBLE);
			}
		}
		return dates.trim();
	}

	public static View placeIndividual(LinearLayout scatola, Person persona, String ruolo) {
		View vistaIndi = LayoutInflater.from(scatola.getContext()).inflate(R.layout.pezzo_individuo, scatola, false);
		scatola.addView(vistaIndi);
		TextView vistaRuolo = vistaIndi.findViewById(R.id.indi_ruolo);
		if( ruolo == null ) vistaRuolo.setVisibility(View.GONE);
		else vistaRuolo.setText(ruolo);
		TextView vistaNome = vistaIndi.findViewById(R.id.indi_nome);
		String nome = properName(persona);
		if( nome.isEmpty() && ruolo != null ) vistaNome.setVisibility(View.GONE);
		else vistaNome.setText(nome);
		TextView vistaTitolo = vistaIndi.findViewById(R.id.indi_titolo);
		String titolo = titolo(persona);
		if( titolo.isEmpty() ) vistaTitolo.setVisibility(View.GONE);
		else vistaTitolo.setText(titolo);
		details(persona, vistaIndi.findViewById(R.id.indi_dettagli));
		F.unaFoto(Global.gc, persona, vistaIndi.findViewById(R.id.indi_foto));
		if( !isDead(persona) )
			vistaIndi.findViewById(R.id.indi_lutto).setVisibility(View.GONE);
		if( Gender.isMale(persona) )
			vistaIndi.findViewById(R.id.indi_bordo).setBackgroundResource(R.drawable.casella_bordo_maschio);
		else if( Gender.isFemale(persona) )
			vistaIndi.findViewById(R.id.indi_bordo).setBackgroundResource(R.drawable.casella_bordo_femmina);
		vistaIndi.setTag(persona.getId());
		return vistaIndi;
	}

	// Place all the notes of an object
	public static void placeNotes(LinearLayout layout, Object container, boolean detailed) {
		for( final Note nota : ((NoteContainer)container).getAllNotes(Global.gc) ) {
			placeNote(layout, nota, detailed);
		}
	}

	// Place a single note on a layout, with details or not
	static void placeNote(final LinearLayout layout, final Note note, boolean detailed) {
		final Context context = layout.getContext();
		View noteView = LayoutInflater.from(context).inflate(R.layout.pezzo_nota, layout, false);
		layout.addView(noteView);
		TextView textView = noteView.findViewById(R.id.nota_testo);
		textView.setText(note.getValue());
		int sourceCiteNum = note.getSourceCitations().size();
		TextView sourceCiteView = noteView.findViewById(R.id.nota_fonti);
		if( sourceCiteNum > 0 && detailed ) sourceCiteView.setText(String.valueOf(sourceCiteNum));
		else sourceCiteView.setVisibility(View.GONE);
		textView.setEllipsize(TextUtils.TruncateAt.END);
		if( detailed ) {
			textView.setMaxLines(10);
			noteView.setTag(R.id.tag_object, note);
			if( context instanceof IndividualPersonActivity) { // Fragment individuoEventi
				((AppCompatActivity)context).getSupportFragmentManager()
						.findFragmentByTag("android:switcher:" + R.id.schede_persona + ":1") // non garantito in futuro
						.registerForContextMenu(noteView);
			} else if( layout.getId() != R.id.dispensa_scatola ) // nelle AppCompatActivity tranne che nella dispensa
				((AppCompatActivity)context).registerForContextMenu(noteView);
			noteView.setOnClickListener(v -> {
				if( note.getId() != null )
					Memory.setFirst(note);
				else
					Memory.add(note);
				context.startActivity(new Intent(context, NoteActivity.class));
			});
		} else {
			textView.setMaxLines(3);
		}
	}

	static void disconnectNote(Note nota, Object contenitore, View vista) {
		List<NoteRef> lista = ((NoteContainer)contenitore).getNoteRefs();
		for( NoteRef ref : lista )
			if( ref.getNote(Global.gc).equals(nota) ) {
				lista.remove(ref);
				break;
			}
		((NoteContainer)contenitore).setNoteRefs(lista);
		if( vista != null )
			vista.setVisibility(View.GONE);
	}

	// Elimina una Nota inlinea o condivisa
	// Restituisce un array dei capostipiti modificati
	public static Object[] deleteNote(Note note, View view) {
		Set<Object> capi;
		if( note.getId() != null ) { // OBJECT note
			// Prima rimuove i ref alla nota con un bel Visitor
			NoteReferences eliminatoreNote = new NoteReferences(Global.gc, note.getId(), true);
			Global.gc.accept(eliminatoreNote);
			Global.gc.getNotes().remove(note); // ok la rimuove se è un'object note
			capi = eliminatoreNote.founders;
			if( Global.gc.getNotes().isEmpty() )
				Global.gc.setNotes(null);
		} else { // LOCAL note
			new FindStack(Global.gc, note);
			NoteContainer nc = (NoteContainer) Memory.getSecondToLastObject();
			nc.getNotes().remove(note); // rimuove solo se è una nota locale, non se object note
			if( nc.getNotes().isEmpty() )
				nc.setNotes(null);
			capi = new HashSet<>();
			capi.add(Memory.firstObject());
			Memory.clearStackAndRemove();
		}
		Memory.setInstanceAndAllSubsequentToNull(note);
		if( view != null )
			view.setVisibility(View.GONE);
		return capi.toArray();
	}

	// Elenca tutti i media di un object contenitore
	public static void placeMedia(LinearLayout layout, Object container, boolean detailed) {
		RecyclerView griglia = new MediaGalleryAdapter.RiciclaVista(layout.getContext(), detailed);
		griglia.setHasFixedSize(true);
		RecyclerView.LayoutManager gestoreLayout = new GridLayoutManager(layout.getContext(), detailed ? 2 : 3);
		griglia.setLayoutManager(gestoreLayout);
		List<MediaListContainer.MedCont> listaMedia = new ArrayList<>();
		for( Media med : ((MediaContainer)container).getAllMedia(Global.gc) )
			listaMedia.add(new MediaListContainer.MedCont(med, container));
		MediaGalleryAdapter adattatore = new MediaGalleryAdapter(listaMedia, detailed);
		griglia.setAdapter(adattatore);
		layout.addView(griglia);
	}

	// Di un object inserisce le citazioni alle fonti
	public static void placeSourceCitations(LinearLayout layout, Object container) {
		if( Global.settings.expert ) {
			List<SourceCitation> listaCitaFonti;
			if( container instanceof Note ) // Note non estende SourceCitationContainer
				listaCitaFonti = ((Note)container).getSourceCitations();
			else listaCitaFonti = ((SourceCitationContainer)container).getSourceCitations();
			for( final SourceCitation citaz : listaCitaFonti ) {
				View vistaCita = LayoutInflater.from(layout.getContext()).inflate(R.layout.pezzo_citazione_fonte, layout, false);
				layout.addView(vistaCita);
				if( citaz.getSource(Global.gc) != null ) // source CITATION
					((TextView)vistaCita.findViewById(R.id.fonte_testo)).setText(LibraryFragment.titoloFonte(citaz.getSource(Global.gc)));
				else // source NOTE, oppure Citazione di fonte che è stata eliminata
					vistaCita.findViewById(R.id.citazione_fonte).setVisibility(View.GONE);
				String t = "";
				if( citaz.getValue() != null ) t += citaz.getValue() + "\n";
				if( citaz.getPage() != null ) t += citaz.getPage() + "\n";
				if( citaz.getDate() != null ) t += citaz.getDate() + "\n";
				if( citaz.getText() != null ) t += citaz.getText() + "\n"; // vale sia per sourceNote che per sourceCitation
				TextView vistaTesto = vistaCita.findViewById(R.id.citazione_testo);
				if( t.isEmpty() ) vistaTesto.setVisibility(View.GONE);
				else vistaTesto.setText(t.substring(0, t.length() - 1));
				// Tutto il resto
				LinearLayout scatolaAltro = vistaCita.findViewById(R.id.citazione_note);
				placeNotes(scatolaAltro, citaz, false);
				placeMedia(scatolaAltro, citaz, false);
				vistaCita.setTag(R.id.tag_object, citaz);
				if( layout.getContext() instanceof IndividualPersonActivity) { // Fragment individuoEventi
					((AppCompatActivity)layout.getContext()).getSupportFragmentManager()
							.findFragmentByTag("android:switcher:" + R.id.schede_persona + ":1")
							.registerForContextMenu(vistaCita);
				} else // AppCompatActivity
					((AppCompatActivity)layout.getContext()).registerForContextMenu(vistaCita);

				vistaCita.setOnClickListener(v -> {
					Intent intent = new Intent(layout.getContext(), SourceCitationActivity.class);
					Memory.add(citaz);
					layout.getContext().startActivity(intent);
				});
			}
		}
	}

	// Inserisce nella scatola il richiamo ad una fonte, con dettagli o essenziale
	public static void placeSource(final LinearLayout layout, final Source source, boolean detailed) {
		View vistaFonte = LayoutInflater.from(layout.getContext()).inflate(R.layout.pezzo_fonte, layout, false);
		layout.addView(vistaFonte);
		TextView vistaTesto = vistaFonte.findViewById(R.id.fonte_testo);
		String txt = "";
		if( detailed ) {
			if( source.getTitle() != null )
				txt = source.getTitle() + "\n";
			else if( source.getAbbreviation() != null )
				txt = source.getAbbreviation() + "\n";
			if( source.getType() != null )
				txt += source.getType().replaceAll("\n", " ") + "\n";
			if( source.getPublicationFacts() != null )
				txt += source.getPublicationFacts().replaceAll("\n", " ") + "\n";
			if( source.getText() != null )
				txt += source.getText().replaceAll("\n", " ");
			if( txt.endsWith("\n") )
				txt = txt.substring(0, txt.length() - 1);
			LinearLayout scatolaAltro = vistaFonte.findViewById(R.id.fonte_scatola);
			placeNotes(scatolaAltro, source, false);
			placeMedia(scatolaAltro, source, false);
			vistaFonte.setTag(R.id.tag_object, source);
			((AppCompatActivity)layout.getContext()).registerForContextMenu(vistaFonte);
		} else {
			vistaTesto.setMaxLines(2);
			txt = LibraryFragment.titoloFonte(source);
		}
		vistaTesto.setText(txt);
		vistaFonte.setOnClickListener(v -> {
			Memory.setFirst(source);
			layout.getContext().startActivity(new Intent(layout.getContext(), SourceActivity.class));
		});
	}

	// La view ritornata è usata da Condivisione
	public static View linkaPersona(LinearLayout scatola, Person p, int scheda) {
		View vistaPersona = LayoutInflater.from(scatola.getContext()).inflate(R.layout.pezzo_individuo_piccolo, scatola, false);
		scatola.addView(vistaPersona);
		F.unaFoto(Global.gc, p, vistaPersona.findViewById(R.id.collega_foto));
		((TextView)vistaPersona.findViewById(R.id.collega_nome)).setText(properName(p));
		String dati = twoDates(p, false);
		TextView vistaDettagli = vistaPersona.findViewById(R.id.collega_dati);
		if( dati.isEmpty() ) vistaDettagli.setVisibility(View.GONE);
		else vistaDettagli.setText(dati);
		if( !isDead(p) )
			vistaPersona.findViewById(R.id.collega_lutto).setVisibility(View.GONE);
		if( Gender.isMale(p) )
			vistaPersona.findViewById(R.id.collega_bordo).setBackgroundResource(R.drawable.casella_bordo_maschio);
		else if( Gender.isFemale(p) )
			vistaPersona.findViewById(R.id.collega_bordo).setBackgroundResource(R.drawable.casella_bordo_femmina);
		vistaPersona.setOnClickListener(v -> {
			Memory.setFirst(p);
			Intent intent = new Intent(scatola.getContext(), IndividualPersonActivity.class);
			intent.putExtra("scheda", scheda);
			scatola.getContext().startActivity(intent);
		});
		return vistaPersona;
	}

	static String testoFamiglia(Context contesto, Gedcom gc, Family fam, boolean unaLinea) {
		String testo = "";
		for( Person marito : fam.getHusbands(gc) )
			testo += properName(marito) + "\n";
		for( Person moglie : fam.getWives(gc) )
			testo += properName(moglie) + "\n";
		if( fam.getChildren(gc).size() == 1 ) {
			testo += properName(fam.getChildren(gc).get(0));
		} else if( fam.getChildren(gc).size() > 1 )
			testo += contesto.getString(R.string.num_children, fam.getChildren(gc).size());
		if( testo.endsWith("\n") ) testo = testo.substring(0, testo.length() - 1);
		if( unaLinea )
			testo = testo.replaceAll("\n", ", ");
		if( testo.isEmpty() )
			testo = "[" + contesto.getString(R.string.empty_family) + "]";
		return testo;
	}

	// Usato da dispensa
	static void linkaFamiglia(LinearLayout scatola, Family fam) {
		View vistaFamiglia = LayoutInflater.from(scatola.getContext()).inflate(R.layout.pezzo_famiglia_piccolo, scatola, false);
		scatola.addView(vistaFamiglia);
		((TextView)vistaFamiglia.findViewById(R.id.famiglia_testo)).setText(testoFamiglia(scatola.getContext(), Global.gc, fam, false));
		vistaFamiglia.setOnClickListener(v -> {
			Memory.setFirst(fam);
			scatola.getContext().startActivity(new Intent(scatola.getContext(), FamilyActivity.class));
		});
	}

	// Usato da dispensa
	static void linkaMedia(LinearLayout scatola, Media media) {
		View vistaMedia = LayoutInflater.from(scatola.getContext()).inflate(R.layout.pezzo_media, scatola, false);
		scatola.addView(vistaMedia);
		MediaGalleryAdapter.arredaMedia(media, vistaMedia.findViewById(R.id.media_testo), vistaMedia.findViewById(R.id.media_num));
		LinearLayout.LayoutParams parami = (LinearLayout.LayoutParams)vistaMedia.getLayoutParams();
		parami.height = dpToPx(80);
		F.showImage(media, vistaMedia.findViewById(R.id.media_img), vistaMedia.findViewById(R.id.media_circolo));
		vistaMedia.setOnClickListener(v -> {
			Memory.setFirst(media);
			scatola.getContext().startActivity(new Intent(scatola.getContext(), ImageActivity.class));
		});
	}

	// Aggiunge un autore al layout
	static void linkAutore(LinearLayout scatola, Submitter autor) {
		Context contesto = scatola.getContext();
		View vista = LayoutInflater.from(contesto).inflate(R.layout.pezzo_nota, scatola, false);
		scatola.addView(vista);
		TextView testoNota = vista.findViewById(R.id.nota_testo);
		testoNota.setText(autor.getName());
		vista.findViewById(R.id.nota_fonti).setVisibility(View.GONE);
		vista.setOnClickListener(v -> {
			Memory.setFirst(autor);
			contesto.startActivity(new Intent(contesto, AuthorActivity.class));
		});
	}

	/**
	 * Adds a generic container with one or more links to parent records to the layout // Aggiunge al layout un contenitore generico con uno o più collegamenti a record capostipiti
	 * */
	public static void putContainer(LinearLayout scatola, Object cosa, int tit) {
		View vista = LayoutInflater.from(scatola.getContext()).inflate(R.layout.dispensa, scatola, false);
		TextView vistaTit = vista.findViewById(R.id.dispensa_titolo);
		vistaTit.setText(tit);
		vistaTit.setBackground(AppCompatResources.getDrawable(scatola.getContext(), R.drawable.sghembo)); // per android 4
		scatola.addView(vista);
		LinearLayout dispensa = vista.findViewById(R.id.dispensa_scatola);
		if( cosa instanceof Object[] ) {
			for( Object o : (Object[])cosa )
				mettiQualsiasi(dispensa, o);
		} else
			mettiQualsiasi(dispensa, cosa);
	}

	// Riconosce il type di record e aggiunge il link appropriato alla scatola
	static void mettiQualsiasi(LinearLayout scatola, Object record) {
		if( record instanceof Person )
			linkaPersona(scatola, (Person)record, 1);
		else if( record instanceof Source )
			placeSource(scatola, (Source)record, false);
		else if( record instanceof Family )
			linkaFamiglia(scatola, (Family)record);
		else if( record instanceof Repository )
			RepositoryRefActivity.putRepository(scatola, (Repository)record);
		else if( record instanceof Note )
			placeNote(scatola, (Note)record, true);
		else if( record instanceof Media )
			linkaMedia(scatola, (Media)record);
		else if( record instanceof Submitter )
			linkAutore(scatola, (Submitter)record);
	}

	// Aggiunge al layout il pezzo con la data e tempo di Cambiamento
	public static View placeChangeDate(final LinearLayout layout, final Change change) {
		View changeView = null;
		if( change != null && Global.settings.expert ) {
			changeView = LayoutInflater.from(layout.getContext()).inflate(R.layout.pezzo_data_cambiamenti, layout, false);
			layout.addView(changeView);
			TextView textView = changeView.findViewById(R.id.cambi_testo);
			if( change.getDateTime() != null ) {
				String txt = "";
				if( change.getDateTime().getValue() != null )
					txt = new GedcomDateConverter(change.getDateTime().getValue()).writeDateLong();
				if( change.getDateTime().getTime() != null )
					txt += " - " + change.getDateTime().getTime();
				textView.setText(txt);
			}
			LinearLayout scatolaNote = changeView.findViewById(R.id.cambi_note);
			for( Extension altroTag : findExtensions(change) )
				place(scatolaNote, altroTag.name, altroTag.text);
			// Grazie al mio contributo la data cambiamento può avere delle note
			placeNotes(scatolaNote, change, false);
			changeView.setOnClickListener(v -> {
				Memory.add(change);
				layout.getContext().startActivity(new Intent(layout.getContext(), ChangesActivity.class));
			});
		}
		return changeView;
	}

	// Chiede conferma di eliminare un elemento
	public static boolean preserva(Object cosa) {
		// todo Conferma elimina
		return false;
	}

	/** Save the tree.
	 * @param refresh Will refresh also other activities
	 * @param objects Record(s) of which update the change date
	 */
	public static void save(boolean refresh, Object... objects) {
		if( refresh )
			Global.edited = true;
		if( objects != null )
			updateChangeDate(objects);

		// al primo salvataggio marchia gli autori
		if( Global.settings.getCurrentTree().grade == 9 ) {
			for( Submitter autore : Global.gc.getSubmitters() )
				autore.putExtension("passed", true);
			Global.settings.getCurrentTree().grade = 10;
			Global.settings.save();
		}

		if( Global.settings.autoSave )
			saveJson(Global.gc, Global.settings.openTree);
		else { // mostra il tasto Salva
			Global.daSalvare = true;
			if( Global.principalView != null ) {
				NavigationView menu = Global.principalView.findViewById(R.id.menu);
				menu.getHeaderView(0).findViewById(R.id.menu_salva).setVisibility(View.VISIBLE);
			}
		}
	}

	// Update the change date of record(s)
	public static void updateChangeDate(Object... objects) {
		for( Object object : objects ) {
			try { // Se aggiornando non ha il metodo get/setChange, passa oltre silenziosamente
				Change change = (Change)object.getClass().getMethod("getChange").invoke(object);
				if( change == null ) // il record non ha ancora un CHAN
					change = new Change();
				change.setDateTime(actualDateTime());
				object.getClass().getMethod("setChange", Change.class).invoke(object, change);
				// Estensione con l'id della zona, una stringa type 'America/Sao_Paulo'
				change.putExtension("zone", TimeZone.getDefault().getID());
			} catch( Exception e ) {
			}
		}
	}

	// Return actual DateTime
	public static DateTime actualDateTime() {
		DateTime dateTime = new DateTime();
		Date now = new Date();
		dateTime.setValue(String.format(Locale.ENGLISH, "%te %<Tb %<tY", now));
		dateTime.setTime(String.format(Locale.ENGLISH, "%tT", now));
		return dateTime;
	}

	// Save the Json
	static void saveJson(Gedcom gedcom, int treeId) {
		Header h = gedcom.getHeader();
		// Solo se l'header è di Family Gem
		if( h != null && h.getGenerator() != null
				&& h.getGenerator().getValue() != null && h.getGenerator().getValue().equals("FAMILY_GEM") ) {
			// Aggiorna la data e l'ora
			h.setDateTime(actualDateTime());
			// Eventualmente aggiorna la versione di Family Gem
			if( (h.getGenerator().getVersion() != null && !h.getGenerator().getVersion().equals(BuildConfig.VERSION_NAME))
					|| h.getGenerator().getVersion() == null )
				h.getGenerator().setVersion(BuildConfig.VERSION_NAME);
		}
		try {
			FileUtils.writeStringToFile(
					new File(Global.context.getFilesDir(), treeId + ".json"),
					new JsonParser().toJson(gedcom), "UTF-8"
			);
		} catch( IOException e ) {
			Toast.makeText(Global.context, e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
		}
		new Notifier(Global.context, gedcom, treeId, Notifier.What.DEFAULT);
	}

	static int castJsonInt(Object unknown) {
		if( unknown instanceof Integer ) return (int)unknown;
		else return ((JsonPrimitive)unknown).getAsInt();
	}

	static String castJsonString(Object unknown) {
		if( unknown == null ) return null;
		else if( unknown instanceof String ) return (String)unknown;
		else return ((JsonPrimitive)unknown).getAsString();
	}

	static float pxToDp(float pixels) {
		return pixels / Global.context.getResources().getDisplayMetrics().density;
	}

	static int dpToPx(float dips) {
		return (int)(dips * Global.context.getResources().getDisplayMetrics().density + 0.5f);
	}

	// Valuta se ci sono individui collegabili rispetto a un individuo.
	// Usato per decidere se far comparire 'Collega persona esistente' nel menu
	static boolean containsConnectableIndividuals(Person person) {
		int total = Global.gc.getPeople().size();
		if( total > 0 && (Global.settings.expert // gli esperti possono sempre
				|| person == null) ) // in una famiglia vuota unRappresentanteDellaFamiglia è null
			return true;
		int kin = ListOfPeopleFragment.countRelatives(person);
		return total > kin + 1;
	}

	// Chiede se referenziare un autore nell'header
	static void autorePrincipale(Context contesto, final String idAutore) {
		final Header[] testa = {Global.gc.getHeader()};
		if( testa[0] == null || testa[0].getSubmitterRef() == null ) {
			new AlertDialog.Builder(contesto).setMessage(R.string.make_main_submitter)
					.setPositiveButton(android.R.string.yes, (dialog, id) -> {
						if( testa[0] == null ) {
							testa[0] = NewTree.createHeader(Global.settings.openTree + ".json");
							Global.gc.setHeader(testa[0]);
						}
						testa[0].setSubmitterRef(idAutore);
						save(true);
					}).setNegativeButton(R.string.no, null).show();
		}
	}

	// Restituisce il primo autore non passato
	static Submitter autoreFresco(Gedcom gc) {
		for( Submitter autore : gc.getSubmitters() ) {
			if( autore.getExtension("passed") == null )
				return autore;
		}
		return null;
	}

	/**
	 * Check if an author has participated in the shares, so as not to have them deleted
	 * */
	static boolean submitterHasShared(Submitter autore) {
		List<Settings.Share> condivisioni = Global.settings.getCurrentTree().shares;
		boolean inviatore = false;
		if( condivisioni != null )
			for( Settings.Share share : condivisioni )
				if( autore.getId().equals(share.submitter) )
					inviatore = true;
		return inviatore;
	}

	// Elenco di stringhe dei membri rappresentativi delle famiglie
	static String[] elencoFamiglie(List<Family> listaFamiglie) {
		List<String> famigliePerno = new ArrayList<>();
		for( Family fam : listaFamiglie ) {
			String etichetta = testoFamiglia(Global.context, Global.gc, fam, true);
			famigliePerno.add(etichetta);
		}
		return famigliePerno.toArray(new String[0]);
	}
	/** For a stud? anchor? reference? ("perno") who is a child in more than one family, ask which family to show
		@param thingToOpen what to open:
				0 diagram of the previous family, without asking which family (first click on Diagram)
				1 diagram possibly asking which family
				2 family possibly asking which family
	*/
	public static void askWhichParentsToShow(Context context, Person person, int thingToOpen) {
		if( person == null )
			finishParentSelection(context, null, 1, 0);
		else {
			List<Family> famiglie = person.getParentFamilies(Global.gc);
			if( famiglie.size() > 1 && thingToOpen > 0 ) {
				new AlertDialog.Builder(context).setTitle(R.string.which_family)
						.setItems(elencoFamiglie(famiglie), (dialog, quale) -> {
							finishParentSelection(context, person, thingToOpen, quale);
						}).show();
			} else
				finishParentSelection(context, person, thingToOpen, 0);
		}

	}
	private static void finishParentSelection(Context contesto, Person perno, int cosaAprire, int qualeFamiglia) {
		if( perno != null )
			Global.indi = perno.getId();
		if( cosaAprire > 0 ) // Viene impostata la famiglia da mostrare
			Global.familyNum = qualeFamiglia; // normalmente è la 0
		if( cosaAprire < 2 ) { // Mostra il diagramma
			if( contesto instanceof Principal ) { // Diagram, Anagrafe o Principal stesso
				FragmentManager fm = ((AppCompatActivity)contesto).getSupportFragmentManager();
				// Nome del frammento precedente nel backstack
				String previousName = fm.getBackStackEntryAt(fm.getBackStackEntryCount() - 1).getName();
				if( previousName != null && previousName.equals("diagram") )
					fm.popBackStack(); // Ricliccando su Diagram rimuove dalla storia il frammento di diagramma predente
				fm.beginTransaction().replace(R.id.contenitore_fragment, new Diagram()).addToBackStack("diagram").commit();
			} else { // Da individuo o da famiglia
				contesto.startActivity(new Intent(contesto, Principal.class));
			}
		} else { // Viene mostrata la famiglia
			Family family = perno.getParentFamilies(Global.gc).get(qualeFamiglia);
			if( contesto instanceof FamilyActivity) { // Passando di Famiglia in Famiglia non accumula attività nello stack
				Memory.replaceFirst(family);
				((Activity)contesto).recreate();
			} else {
				Memory.setFirst(family);
				contesto.startActivity(new Intent(contesto, FamilyActivity.class));
			}
		}
	}

	/**
	 * For an anchor ("perno") who has multiple marriages it asks which one to show
	 * */
	public static void askWhichSpouceToShow(Context contesto, Person perno, Family famiglia) {
		if( perno.getSpouseFamilies(Global.gc).size() > 1 && famiglia == null ) {
			new AlertDialog.Builder(contesto).setTitle(R.string.which_family)
					.setItems(elencoFamiglie(perno.getSpouseFamilies(Global.gc)), (dialog, quale) -> {
						concludiSceltaConiugi(contesto, perno, null, quale);
					}).show();
		} else {
			concludiSceltaConiugi(contesto, perno, famiglia, 0);
		}
	}
	private static void concludiSceltaConiugi(Context contesto, Person perno, Family famiglia, int quale) {
		Global.indi = perno.getId();
		famiglia = famiglia == null ? perno.getSpouseFamilies(Global.gc).get(quale) : famiglia;
		if( contesto instanceof FamilyActivity) {
			Memory.replaceFirst(famiglia);
			((Activity)contesto).recreate(); // Non accumula activity nello stack
		} else {
			Memory.setFirst(famiglia);
			contesto.startActivity(new Intent(contesto, FamilyActivity.class));
		}
	}

	// Usato per collegare una persona ad un'altra, solo in modalità inesperto
	// Verifica se il perno potrebbe avere o ha molteplici matrimoni e chiede a quale attaccare un coniuge o un figlio
	// È anche responsabile di settare 'idFamiglia' oppure 'collocazione'
	static boolean controllaMultiMatrimoni(Intent intent, Context contesto, Fragment frammento) {
		String idPerno = intent.getStringExtra("idIndividuo");
		Person perno = Global.gc.getPerson(idPerno);
		List<Family> famGenitori = perno.getParentFamilies(Global.gc);
		List<Family> famSposi = perno.getSpouseFamilies(Global.gc);
		int relazione = intent.getIntExtra("relazione", 0);
		ArrayAdapter<NewRelativeDialog.VoceFamiglia> adapter = new ArrayAdapter<>(contesto, android.R.layout.simple_list_item_1);

		// Genitori: esiste già una famiglia che abbia almeno uno spazio vuoto
		if( relazione == 1 && famGenitori.size() == 1
				&& (famGenitori.get(0).getHusbandRefs().isEmpty() || famGenitori.get(0).getWifeRefs().isEmpty()) )
			intent.putExtra("idFamiglia", famGenitori.get(0).getId()); // aggiunge 'idFamiglia' all'intent esistente
		// se questa famiglia è già piena di genitori, 'idFamiglia' rimane null
		// quindi verrà cercata la famiglia esistente del destinatario oppure si crearà una famiglia nuova

		// Genitori: esistono più famiglie
		if( relazione == 1 && famGenitori.size() > 1 ) {
			for( Family fam : famGenitori )
				if( fam.getHusbandRefs().isEmpty() || fam.getWifeRefs().isEmpty() )
					adapter.add(new NewRelativeDialog.VoceFamiglia(contesto, fam));
			if( adapter.getCount() == 1 )
				intent.putExtra("idFamiglia", adapter.getItem(0).famiglia.getId());
			else if( adapter.getCount() > 1 ) {
				new AlertDialog.Builder(contesto).setTitle(R.string.which_family_add_parent)
						.setAdapter(adapter, (dialog, quale) -> {
							intent.putExtra("idFamiglia", adapter.getItem(quale).famiglia.getId());
							concludiMultiMatrimoni(contesto, intent, frammento);
						}).show();
				return true;
			}
		}
		// Fratello
		else if( relazione == 2 && famGenitori.size() == 1 ) {
			intent.putExtra("idFamiglia", famGenitori.get(0).getId());
		} else if( relazione == 2 && famGenitori.size() > 1 ) {
			new AlertDialog.Builder(contesto).setTitle(R.string.which_family_add_sibling)
					.setItems(elencoFamiglie(famGenitori), (dialog, quale) -> {
						intent.putExtra("idFamiglia", famGenitori.get(quale).getId());
						concludiMultiMatrimoni(contesto, intent, frammento);
					}).show();
			return true;
		}
		// Coniuge
		else if( relazione == 3 && famSposi.size() == 1 ) {
			if( famSposi.get(0).getHusbandRefs().isEmpty() || famSposi.get(0).getWifeRefs().isEmpty() ) // Se c'è uno slot libero
				intent.putExtra("idFamiglia", famSposi.get(0).getId());
		} else if( relazione == 3 && famSposi.size() > 1 ) {
			for( Family fam : famSposi ) {
				if( fam.getHusbandRefs().isEmpty() || fam.getWifeRefs().isEmpty() )
					adapter.add(new NewRelativeDialog.VoceFamiglia(contesto, fam));
			}
			// Nel caso di zero famiglie papabili, idFamiglia rimane null
			if( adapter.getCount() == 1 ) {
				intent.putExtra("idFamiglia", adapter.getItem(0).famiglia.getId());
			} else if( adapter.getCount() > 1 ) {
				//adapter.add(new NuovoParente.VoceFamiglia(contesto,perno) );
				new AlertDialog.Builder(contesto).setTitle(R.string.which_family_add_spouse)
						.setAdapter(adapter, (dialog, quale) -> {
							intent.putExtra("idFamiglia", adapter.getItem(quale).famiglia.getId());
							concludiMultiMatrimoni(contesto, intent, frammento);
						}).show();
				return true;
			}
		}
		// Figlio: esiste già una famiglia con o senza figli
		else if( relazione == 4 && famSposi.size() == 1 ) {
			intent.putExtra("idFamiglia", famSposi.get(0).getId());
		} // Figlio: esistono molteplici famiglie coniugali
		else if( relazione == 4 && famSposi.size() > 1 ) {
			new AlertDialog.Builder(contesto).setTitle(R.string.which_family_add_child)
					.setItems(elencoFamiglie(famSposi), (dialog, quale) -> {
						intent.putExtra("idFamiglia", famSposi.get(quale).getId());
						concludiMultiMatrimoni(contesto, intent, frammento);
					}).show();
			return true;
		}
		// Non avendo trovato una famiglia di perno, dice ad Anagrafe di cercare di collocare perno nella famiglia del destinatario
		if( intent.getStringExtra("idFamiglia") == null && intent.getBooleanExtra("anagrafeScegliParente", false) )
			intent.putExtra("collocazione", "FAMIGLIA_ESISTENTE");
		return false;
	}

	// Conclusione della funzione precedente
	static void concludiMultiMatrimoni(Context contesto, Intent intent, Fragment frammento) {
		if( intent.getBooleanExtra("anagrafeScegliParente", false) ) {
			// apre Anagrafe
			if( frammento != null )
				frammento.startActivityForResult(intent, 1401);
			else
				((Activity)contesto).startActivityForResult(intent, 1401);
		} else // apre EditaIndividuo
			contesto.startActivity(intent);
	}

	// Controlla che una o più famiglie siano vuote e propone di eliminarle
	// 'ancheKo' dice di eseguire 'cheFare' anche cliccando Cancel o fuori dal dialogo
	static boolean controllaFamiglieVuote(Context contesto, Runnable cheFare, boolean ancheKo, Family... famiglie) {
		List<Family> vuote = new ArrayList<>();
		for( Family fam : famiglie ) {
			int membri = fam.getHusbandRefs().size() + fam.getWifeRefs().size() + fam.getChildRefs().size();
			if( membri <= 1 && fam.getEventsFacts().isEmpty() && fam.getAllMedia(Global.gc).isEmpty()
					&& fam.getAllNotes(Global.gc).isEmpty() && fam.getSourceCitations().isEmpty() ) {
				vuote.add(fam);
			}
		}
		if( vuote.size() > 0 ) {
			new AlertDialog.Builder(contesto).setMessage(R.string.empty_family_delete)
					.setPositiveButton(android.R.string.yes, (dialog, i) -> {
						for( Family fam : vuote )
							ChurchFragment.deleteFamily(fam); // Così capita di salvare più volte insieme... ma vabè
						if( cheFare != null ) cheFare.run();
					}).setNeutralButton(android.R.string.cancel, (dialog, i) -> {
						if( ancheKo ) cheFare.run();
					}).setOnCancelListener(dialog -> {
						if( ancheKo ) cheFare.run();
					}).show();
			return true;
		}
		return false;
	}

	// Display a dialog to edit the ID of any record
	public static void editId(Context context, ExtensionContainer record, Runnable refresh) {
		View view = ((Activity)context).getLayoutInflater().inflate(R.layout.id_editor, null);
		EditText inputField = view.findViewById(R.id.edit_id_input_field);
		try {
			String oldId = (String)record.getClass().getMethod("getId").invoke(record);
			inputField.setText(oldId);
			AlertDialog alertDialog = new AlertDialog.Builder(context)
					.setTitle(R.string.edit_id).setView(view)
					.setPositiveButton(R.string.save, (dialog, i) -> {
						String newId = inputField.getText().toString().trim();
						if( newId.equals(oldId) ) return;
						if( record instanceof Person ) {
							Person person = (Person)record;
							person.setId(newId);
							Set<PersonFamilyCommonContainer> modified = new HashSet<>();
							modified.add(person);
							for( Family family : Global.gc.getFamilies() ) {
								for( SpouseRef ref : family.getHusbandRefs() )
									if( oldId.equals(ref.getRef()) ) {
										ref.setRef(newId);
										modified.add(family);
									}
								for( SpouseRef ref : family.getWifeRefs() )
									if( oldId.equals(ref.getRef()) ) {
										ref.setRef(newId);
										modified.add(family);
									}
								for( ChildRef ref : family.getChildRefs() )
									if( oldId.equals(ref.getRef()) ) {
										ref.setRef(newId);
										modified.add(family);
									}
							}
							U.save(true, modified.toArray());
							Settings.Tree tree = Global.settings.getCurrentTree();
							if( oldId.equals(tree.root) ) {
								tree.root = newId;
								Global.settings.save();
							}
							Global.indi = newId;
						} else if( record instanceof Family ) {
							Family family = (Family)record;
							family.setId(newId);
							Set<PersonFamilyCommonContainer> modified = new HashSet<>();
							modified.add(family);
							for( Person person : Global.gc.getPeople() ) {
								for( ParentFamilyRef ref : person.getParentFamilyRefs() )
									if( oldId.equals(ref.getRef()) ) {
										ref.setRef(newId);
										modified.add(person);
									}
								for( SpouseFamilyRef ref : person.getSpouseFamilyRefs() )
									if( oldId.equals(ref.getRef()) ) {
										ref.setRef(newId);
										modified.add(person);
									}
							}
							U.save(true, modified.toArray());
						} else if( record instanceof Media ) {
							Media media = (Media)record;
							MediaContainers mediaContainers = new MediaContainers(Global.gc, media, newId);
							media.setId(newId);
							U.updateChangeDate(media);
							U.save(true, mediaContainers.containers.toArray());
						} else if( record instanceof Note ) {
							Note note = (Note)record;
							NoteContainers noteContainers = new NoteContainers(Global.gc, note, newId);
							note.setId(newId);
							U.updateChangeDate(note);
							U.save(true, noteContainers.containers.toArray());
						} else if( record instanceof Source ) {
							ListOfSourceCitations citations = new ListOfSourceCitations(Global.gc, oldId);
							for( ListOfSourceCitations.Triplet triple : citations.list)
								triple.citation.setRef(newId);
							Source source = (Source)record;
							source.setId(newId);
							U.updateChangeDate(source);
							U.save(true, citations.getProgenitors());
						} else if( record instanceof Repository ) {
							Set<Source> modified = new HashSet<>();
							for( Source source : Global.gc.getSources() ) {
								RepositoryRef repoRef = source.getRepositoryRef();
								if( repoRef != null && oldId.equals(repoRef.getRef()) ) {
									repoRef.setRef(newId);
									modified.add(source);
								}
							}
							Repository repo = (Repository)record;
							repo.setId( newId );
							U.updateChangeDate(repo);
							U.save(true, modified.toArray());
						} else if( record instanceof Submitter ) {
							for( Settings.Share share : Global.settings.getCurrentTree().shares )
								if( oldId.equals(share.submitter) )
									share.submitter = newId;
							Global.settings.save();
							Header header = Global.gc.getHeader();
							if( oldId.equals(header.getSubmitterRef()) )
								header.setSubmitterRef(newId);
							Submitter submitter = (Submitter)record;
							submitter.setId(newId);
							U.save(true, submitter);
						}
						Global.gc.createIndexes();
						refresh.run();
					}).setNeutralButton(R.string.cancel, null).show();
			// Focus
			view.postDelayed(() -> {
				inputField.requestFocus();
				inputField.setSelection(inputField.getText().length());
				InputMethodManager inputMethodManager = (InputMethodManager)context.getSystemService(Context.INPUT_METHOD_SERVICE);
				inputMethodManager.showSoftInput(inputField, InputMethodManager.SHOW_IMPLICIT);
			}, 300);
			// All other IDs
			Set<String> allIds = new HashSet<>();
			for( Person person : Global.gc.getPeople() )
				allIds.add(person.getId());
			for( Family family : Global.gc.getFamilies() )
				allIds.add(family.getId());
			for( Media media : Global.gc.getMedia() )
				allIds.add(media.getId());
			for( Note note : Global.gc.getNotes() )
				allIds.add(note.getId());
			for( Source source : Global.gc.getSources() )
				allIds.add(source.getId());
			for( Repository repo : Global.gc.getRepositories() )
				allIds.add(repo.getId());
			for( Submitter submitter : Global.gc.getSubmitters() )
				allIds.add(submitter.getId());
			allIds.remove(oldId);
			// Validation
			TextInputLayout inputLayout = view.findViewById(R.id.edit_id_input_layout);
			Button okButton = alertDialog.getButton(AlertDialog.BUTTON_POSITIVE);
			inputField.addTextChangedListener(new TextWatcher() {
				@Override
				public void beforeTextChanged(CharSequence s, int start, int count, int after) {
				}
				@Override
				public void onTextChanged(CharSequence sequence, int start, int before, int count) {
					String error = null;
					String proposal = sequence.toString().trim();
					if( allIds.contains(proposal) )
						error = context.getString(R.string.existing_id);
					else if( proposal.isEmpty() || proposal.matches("^[#].*|.*[@:!].*") )
						error = context.getString(R.string.invalid_id);
					inputLayout.setError(error);
					okButton.setEnabled(error == null);
				}
				@Override
				public void afterTextChanged(Editable e) {
				}
			});
			inputField.setOnEditorActionListener((textView, actionId, keyEvent) -> {
				if( actionId == EditorInfo.IME_ACTION_DONE && okButton.isEnabled() ) {
					okButton.performClick();
					return true;
				}
				return false;
			});
		} catch( Exception e ) {
		}
	}

	// Mostra un messaggio Toast anche da un thread collaterale
	static void toast(Activity activity, int message) {
		toast(activity, activity.getString(message));
	}

	static void toast(Activity activity, String message) {
		activity.runOnUiThread(() -> Toast.makeText(activity, message, Toast.LENGTH_LONG).show());
	}
}
