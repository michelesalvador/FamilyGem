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

/**
 * Static methods used all across the app
 * */
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

	/**
	 * Id of the main person of a GEDCOM or null
	 */
	static String getRootId(Gedcom gedcom, Settings.Tree tree) {
		if( tree.root != null ) {
			Person root = gedcom.getPerson(tree.root);
			if( root != null )
				return root.getId();
		}
		return findRoot(gedcom);
	}

	/**
	 * @return the id of the initial Person of a Gedcom
	 * Todo Integrate into {@link #getRootId(Gedcom, Settings.Tree)} ???
	 */
	static String findRoot(Gedcom gc) {
		if( gc.getHeader() != null )
			if( tagValue(gc.getHeader().getExtensions(), "_ROOT") != null )
				return tagValue(gc.getHeader().getExtensions(), "_ROOT");
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

	/**
	 * The given name of a person or something
	 */
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

	/**
	 * receives a Person and returns the title of nobility
	 */
	static String title(Person p) {
		// GEDCOM standard INDI.TITL
		for( EventFact ef : p.getEventsFacts() )
			if( ef.getTag() != null && ef.getTag().equals("TITL") && ef.getValue() != null )
				return ef.getValue();
		// So instead it takes INDI.NAME._TYPE.TITL, old method of org.folg.gedcom
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
		String fullName = "";
		if( n.getValue() != null ) {
			String raw = n.getValue().trim();
			int slashPos = raw.indexOf('/');
			int lastSlashPos = raw.lastIndexOf('/');
			if( slashPos > -1 ) // If there is a last name between '/'
				fullName = raw.substring(0, slashPos).trim(); // first name
			else // Or it's just a first name without a last name
				fullName = raw;
			if( n.getNickname() != null )
				fullName += divider + "\"" + n.getNickname() + "\"";
			if( slashPos < lastSlashPos )
				fullName += divider + raw.substring(slashPos + 1, lastSlashPos).trim(); // surname
			if( lastSlashPos > -1 && raw.length() - 1 > lastSlashPos )
				fullName += " " + raw.substring(lastSlashPos + 1).trim(); // after the surname
		} else {
			if( n.getPrefix() != null )
				fullName = n.getPrefix();
			if( n.getGiven() != null )
				fullName += " " + n.getGiven();
			if( n.getNickname() != null )
				fullName += divider + "\"" + n.getNickname() + "\"";
			if( n.getSurname() != null )
				fullName += divider + n.getSurname();
			if( n.getSuffix() != null )
				fullName += " " + n.getSuffix();
		}
		fullName = fullName.trim();
		return fullName.isEmpty() ? "[" + s(R.string.empty_name) + "]" : fullName;
	}

	/**
	 * Return the surname of a person, optionally lowercase for comparison. Can return null.
	 */
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

	/**
	 * Receives a person and finds out if he is dead or buried
	 */
	static boolean isDead(Person person) {
		for( EventFact eventFact : person.getEventsFacts() ) {
			if( eventFact.getTag().equals("DEAT") || eventFact.getTag().equals("BURI") )
				return true;
		}
		return false;
	}

	/**
	 * Check whether a family has a marriage event of type 'marriage'
	 */
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

	/**
	 * Write the two main places of a person (initial – final) or null
	 */
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

	/**
	 * gets a Gedcom-style location and returns the first name between the commas
	 */
	private static String stripCommas(String place) {
		// skip leading commas for places type ',,,England'
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

	static int max;
	/**
	 * Generate the new id following the existing ones
	 */
	public static String newID(Gedcom gc, Class clazz) {
		max = 0;
		String pre = "";
		if( clazz == Note.class ) {
			pre = "N";
			for( Note n : gc.getNotes() )
				calculateMax(n);
		} else if( clazz == Submitter.class ) {
			pre = "U";
			for( Submitter a : gc.getSubmitters() )
				calculateMax(a);
		} else if( clazz == Repository.class ) {
			pre = "R";
			for( Repository r : gc.getRepositories() )
				calculateMax(r);
		} else if( clazz == Media.class ) {
			pre = "M";
			for( Media m : gc.getMedia() )
				calculateMax(m);
		} else if( clazz == Source.class ) {
			pre = "S";
			for( Source f : gc.getSources() )
				calculateMax(f);
		} else if( clazz == Person.class ) {
			pre = "I";
			for( Person p : gc.getPeople() )
				calculateMax(p);
		} else if( clazz == Family.class ) {
			pre = "F";
			for( Family f : gc.getFamilies() )
				calculateMax(f);
		}
		return pre + (max + 1);
	}

	private static void calculateMax(Object object) {
		try {
			String idString = (String)object.getClass().getMethod("getId").invoke(object);
			int num = extractNum(idString);
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

	/**
	 * Returns the list of extensions
	 */
	@SuppressWarnings("unchecked")
	public static List<Extension> findExtensions(ExtensionContainer container) {
		if( container.getExtension(ModelParser.MORE_TAGS_EXTENSION_KEY) != null ) {
			List<Extension> list = new ArrayList<>();
			for( GedcomTag est : (List<GedcomTag>)container.getExtension(ModelParser.MORE_TAGS_EXTENSION_KEY) ) {
				String text = traverseExtension(est, 0);
				if( text.endsWith("\n") )
					text = text.substring(0, text.length() - 1);
				list.add(new Extension(est.getTag(), text, est));
			}
			return list;
		}
		return Collections.emptyList();
	}

	/**
	 * Constructs a text with the recursive content of the extension
	 * */
	public static String traverseExtension(GedcomTag tag, int grade) {
		StringBuilder text = new StringBuilder();
		if( grade > 0 )
			text.append(tag.getTag()).append(" ");
		if( tag.getValue() != null )
			text.append(tag.getValue()).append("\n");
		else if( tag.getId() != null )
			text.append(tag.getId()).append("\n");
		else if( tag.getRef() != null )
			text.append(tag.getRef()).append("\n");
		for( GedcomTag piece : tag.getChildren() )
			text.append(traverseExtension(piece, ++grade));
		return text.toString();
	}

	public static void deleteExtension(GedcomTag extension, Object container, View view) {
		if( container instanceof ExtensionContainer ) { // IndividualEventsFragment
			ExtensionContainer exc = (ExtensionContainer)container;
			@SuppressWarnings("unchecked")
			List<GedcomTag> list = (List<GedcomTag>)exc.getExtension(ModelParser.MORE_TAGS_EXTENSION_KEY);
			list.remove(extension);
			if( list.isEmpty() )
				exc.getExtensions().remove(ModelParser.MORE_TAGS_EXTENSION_KEY);
			if( exc.getExtensions().isEmpty() )
				exc.setExtensions(null);
		} else if( container instanceof GedcomTag ) { // DetailActivity
			GedcomTag gt = (GedcomTag)container;
			gt.getChildren().remove(extension);
			if( gt.getChildren().isEmpty() )
				gt.setChildren(null);
		}
		Memory.setInstanceAndAllSubsequentToNull(extension);
		if( view != null )
			view.setVisibility(View.GONE);
	}

	/**
	 * Returns the value of a given tag in an extension ({@link GedcomTag})
	 */
	@SuppressWarnings("unchecked")
	static String tagValue(Map<String, Object> extensionMap, String tagName) {
		for( Map.Entry<String, Object> extension : extensionMap.entrySet() ) {
			List<GedcomTag> tagList = (ArrayList<GedcomTag>)extension.getValue();
			for( GedcomTag piece : tagList ) {
				//l( piece.getTag() +" "+ piece.getValue() );
				if( piece.getTag().equals(tagName) ) {
					if( piece.getId() != null )
						return piece.getId();
					else if( piece.getRef() != null )
						return piece.getRef();
					else
						return piece.getValue();
				}
			}
		}
		return null;
	}

	// Methods of creating list elements

	/**
	 * Add a generic title-text entry to a Layout. Used seriously only by [ChangesActivity]
	 * */
	public static void place(LinearLayout layout, String tit, String text) {
		View pieceView = LayoutInflater.from(layout.getContext()).inflate(R.layout.pezzo_fatto, layout, false);
		layout.addView(pieceView);
		((TextView)pieceView.findViewById(R.id.fatto_titolo)).setText(tit);
		TextView textView = pieceView.findViewById(R.id.fatto_testo);
		if( text == null ) textView.setVisibility(View.GONE);
		else {
			textView.setText(text);
			//((TextView)pieceView.findViewById( R.id.fatto_edita )).setText( text );
		}
		//((Activity)layout.getContext()).registerForContextMenu( pieceView );
	}

	/**
	 * Composes text with details of an individual and places it in text view
	 * also returns the same text for {@link TreeComparatorActivity}
	 * */
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

	public static View placeIndividual(LinearLayout layout, Person person, String role) {
		View indiView = LayoutInflater.from(layout.getContext()).inflate(R.layout.pezzo_individuo, layout, false);
		layout.addView(indiView);
		TextView roleView = indiView.findViewById(R.id.indi_ruolo);
		if( role == null ) roleView.setVisibility(View.GONE);
		else roleView.setText(role);
		TextView nameView = indiView.findViewById(R.id.indi_nome);
		String name = properName(person);
		if( name.isEmpty() && role != null ) nameView.setVisibility(View.GONE);
		else nameView.setText(name);
		TextView titleView = indiView.findViewById(R.id.indi_titolo);
		String title = title(person);
		if( title.isEmpty() ) titleView.setVisibility(View.GONE);
		else titleView.setText(title);
		details(person, indiView.findViewById(R.id.indi_dettagli));
		F.showMainImageForPerson(Global.gc, person, indiView.findViewById(R.id.indi_foto));
		if( !isDead(person) )
			indiView.findViewById(R.id.indi_lutto).setVisibility(View.GONE);
		if( Gender.isMale(person) )
			indiView.findViewById(R.id.indi_bordo).setBackgroundResource(R.drawable.casella_bordo_maschio);
		else if( Gender.isFemale(person) )
			indiView.findViewById(R.id.indi_bordo).setBackgroundResource(R.drawable.casella_bordo_femmina);
		indiView.setTag(person.getId());
		return indiView;
	}

	/**
	 * Place all the notes of an object
	 */
	public static void placeNotes(LinearLayout layout, Object container, boolean detailed) {
		for( final Note nota : ((NoteContainer)container).getAllNotes(Global.gc) ) {
			placeNote(layout, nota, detailed);
		}
	}

	/**
	 * Place a single note on a layout, with details or not
	 */
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
			if( context instanceof IndividualPersonActivity) { // IndividualEventsFragment
				((AppCompatActivity)context).getSupportFragmentManager()
						.findFragmentByTag("android:switcher:" + R.id.schede_persona + ":1") // not guaranteed in the future
						.registerForContextMenu(noteView);
			} else if( layout.getId() != R.id.dispensa_scatola ) // in AppCompatActivities except in the pantry (??)
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

	static void disconnectNote(Note nota, Object container, View view) {
		List<NoteRef> list = ((NoteContainer)container).getNoteRefs();
		for( NoteRef ref : list )
			if( ref.getNote(Global.gc).equals(nota) ) {
				list.remove(ref);
				break;
			}
		((NoteContainer)container).setNoteRefs(list);
		if( view != null )
			view.setVisibility(View.GONE);
	}

	/**
	 * Delete an online or shared Note
	 * @return an array of modified parents
	 */
	public static Object[] deleteNote(Note note, View view) {
		Set<Object> heads;
		if( note.getId() != null ) { // OBJECT note
			// First remove the refs to the note with a nice Visitor
			NoteReferences noteEliminator = new NoteReferences(Global.gc, note.getId(), true);
			Global.gc.accept(noteEliminator);
			Global.gc.getNotes().remove(note); // ok removes it if it is an object note
			heads = noteEliminator.founders;
			if( Global.gc.getNotes().isEmpty() )
				Global.gc.setNotes(null);
		} else { // LOCAL note
			new FindStack(Global.gc, note);
			NoteContainer nc = (NoteContainer) Memory.getSecondToLastObject();
			nc.getNotes().remove(note); //only removes if it is a local note, not if object note
			if( nc.getNotes().isEmpty() )
				nc.setNotes(null);
			heads = new HashSet<>();
			heads.add(Memory.firstObject());
			Memory.clearStackAndRemove();
		}
		Memory.setInstanceAndAllSubsequentToNull(note);
		if( view != null )
			view.setVisibility(View.GONE);
		return heads.toArray();
	}

	/**
	 * List all media of a container object
	 */
	public static void placeMedia(LinearLayout layout, Object container, boolean detailed) {
		RecyclerView recyclerView = new MediaGalleryAdapter.MediaIconsRecyclerView(layout.getContext(), detailed);
		recyclerView.setHasFixedSize(true);
		RecyclerView.LayoutManager layoutManager = new GridLayoutManager(layout.getContext(), detailed ? 2 : 3);
		recyclerView.setLayoutManager(layoutManager);
		List<MediaListContainer.MedCont> listaMedia = new ArrayList<>();
		for( Media med : ((MediaContainer)container).getAllMedia(Global.gc) )
			listaMedia.add(new MediaListContainer.MedCont(med, container));
		MediaGalleryAdapter adapter = new MediaGalleryAdapter(listaMedia, detailed);
		recyclerView.setAdapter(adapter);
		layout.addView(recyclerView);
	}

	/**
	 * Of an object it inserts the citations to the sources //Di un object inserisce le citazioni alle fonti
	 */
	public static void placeSourceCitations(LinearLayout layout, Object container) {
		if( Global.settings.expert ) {
			List<SourceCitation> listOfSourceCitations;
			if( container instanceof Note ) // Notes does not extend SourceCitationContainer
				listOfSourceCitations = ((Note)container).getSourceCitations();
			else listOfSourceCitations = ((SourceCitationContainer)container).getSourceCitations();
			for( final SourceCitation citation : listOfSourceCitations ) {
				View citationView = LayoutInflater.from(layout.getContext()).inflate(R.layout.pezzo_citazione_fonte, layout, false);
				layout.addView(citationView);
				if( citation.getSource(Global.gc) != null ) // source CITATION
					((TextView)citationView.findViewById(R.id.fonte_testo)).setText(LibraryFragment.sourceTitle(citation.getSource(Global.gc)));
				else // source NOTE, or Source citation that has been deleted
					citationView.findViewById(R.id.citazione_fonte).setVisibility(View.GONE);
				String t = "";
				if( citation.getValue() != null ) t += citation.getValue() + "\n";
				if( citation.getPage() != null ) t += citation.getPage() + "\n";
				if( citation.getDate() != null ) t += citation.getDate() + "\n";
				if( citation.getText() != null ) t += citation.getText() + "\n"; // applies to both sourceNote and sourceCitation
				TextView textView = citationView.findViewById(R.id.citazione_testo);
				if( t.isEmpty() ) textView.setVisibility(View.GONE);
				else textView.setText(t.substring(0, t.length() - 1));
				// All the rest
				LinearLayout otherLayout = citationView.findViewById(R.id.citazione_note);
				placeNotes(otherLayout, citation, false);
				placeMedia(otherLayout, citation, false);
				citationView.setTag(R.id.tag_object, citation);
				if( layout.getContext() instanceof IndividualPersonActivity) { // IndividualEventsFragment
					((AppCompatActivity)layout.getContext()).getSupportFragmentManager()
							.findFragmentByTag("android:switcher:" + R.id.schede_persona + ":1")
							.registerForContextMenu(citationView);
				} else // AppCompatActivity
					((AppCompatActivity)layout.getContext()).registerForContextMenu(citationView);

				citationView.setOnClickListener(v -> {
					Intent intent = new Intent(layout.getContext(), SourceCitationActivity.class);
					Memory.add(citation);
					layout.getContext().startActivity(intent);
				});
			}
		}
	}

	/**
	 * Inserts the reference to a source, with details or essential, into the box
	 */
	public static void placeSource(final LinearLayout layout, final Source source, boolean detailed) {
		View sourceView = LayoutInflater.from(layout.getContext()).inflate(R.layout.pezzo_fonte, layout, false);
		layout.addView(sourceView);
		TextView textView = sourceView.findViewById(R.id.fonte_testo);
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
			LinearLayout otherLayout = sourceView.findViewById(R.id.fonte_scatola);
			placeNotes(otherLayout, source, false);
			placeMedia(otherLayout, source, false);
			sourceView.setTag(R.id.tag_object, source);
			((AppCompatActivity)layout.getContext()).registerForContextMenu(sourceView);
		} else {
			textView.setMaxLines(2);
			txt = LibraryFragment.sourceTitle(source);
		}
		textView.setText(txt);
		sourceView.setOnClickListener(v -> {
			Memory.setFirst(source);
			layout.getContext().startActivity(new Intent(layout.getContext(), SourceActivity.class));
		});
	}

	/**
	 * The returned view is used by {@link SharingActivity}
	 */
	public static View linkPerson(LinearLayout layout, Person p, int card) {
		View personView = LayoutInflater.from(layout.getContext()).inflate(R.layout.pezzo_individuo_piccolo, layout, false);
		layout.addView(personView);
		F.showMainImageForPerson(Global.gc, p, personView.findViewById(R.id.collega_foto));
		((TextView)personView.findViewById(R.id.collega_nome)).setText(properName(p));
		String dates = twoDates(p, false);
		TextView detailsView = personView.findViewById(R.id.collega_dati);
		if( dates.isEmpty() ) detailsView.setVisibility(View.GONE);
		else detailsView.setText(dates);
		if( !isDead(p) )
			personView.findViewById(R.id.collega_lutto).setVisibility(View.GONE);
		if( Gender.isMale(p) )
			personView.findViewById(R.id.collega_bordo).setBackgroundResource(R.drawable.casella_bordo_maschio);
		else if( Gender.isFemale(p) )
			personView.findViewById(R.id.collega_bordo).setBackgroundResource(R.drawable.casella_bordo_femmina);
		personView.setOnClickListener(v -> {
			Memory.setFirst(p);
			Intent intent = new Intent(layout.getContext(), IndividualPersonActivity.class);
			intent.putExtra("scheda", card);
			layout.getContext().startActivity(intent);
		});
		return personView;
	}

	static String familyText(Context context, Gedcom gc, Family fam, boolean oneLine) {
		StringBuilder text = new StringBuilder();
		for( Person husband : fam.getHusbands(gc) )
			text.append(properName(husband)).append("\n");
		for( Person wife : fam.getWives(gc) )
			text.append(properName(wife)).append("\n");
		if( fam.getChildren(gc).size() == 1 ) {
			text.append(properName(fam.getChildren(gc).get(0)));
		} else if( fam.getChildren(gc).size() > 1 )
			text.append(context.getString(R.string.num_children, fam.getChildren(gc).size()));
		if( text.toString().endsWith("\n") ) text.deleteCharAt(text.length() - 1);
		if( oneLine )
			text = new StringBuilder(text.toString().replaceAll("\n", ", "));
		if(text.length() == 0)
			text = new StringBuilder("[" + context.getString(R.string.empty_family) + "]");
		return text.toString();
	}

	/**
	 * Used by pantry (??)
	 */
	static void linkFamily(LinearLayout layout, Family fam) {
		View familyView = LayoutInflater.from(layout.getContext()).inflate(R.layout.pezzo_famiglia_piccolo, layout, false);
		layout.addView(familyView);
		((TextView)familyView.findViewById(R.id.famiglia_testo)).setText(familyText(layout.getContext(), Global.gc, fam, false));
		familyView.setOnClickListener(v -> {
			Memory.setFirst(fam);
			layout.getContext().startActivity(new Intent(layout.getContext(), FamilyActivity.class));
		});
	}

	/**
	 * Used from pantry
	 */
	static void linkMedia(LinearLayout layout, Media media) {
		View imageView = LayoutInflater.from(layout.getContext()).inflate(R.layout.pezzo_media, layout, false);
		layout.addView(imageView);
		MediaGalleryAdapter.setupMedia(media, imageView.findViewById(R.id.media_testo), imageView.findViewById(R.id.media_num));
		LinearLayout.LayoutParams parami = (LinearLayout.LayoutParams)imageView.getLayoutParams();
		parami.height = dpToPx(80);
		F.showImage(media, imageView.findViewById(R.id.media_img), imageView.findViewById(R.id.media_circolo));
		imageView.setOnClickListener(v -> {
			Memory.setFirst(media);
			layout.getContext().startActivity(new Intent(layout.getContext(), ImageActivity.class));
		});
	}

	/**
	 * Aggiunge un autore al layout
	 */
	static void linkSubmitter(LinearLayout layout, Submitter submitter) {
		Context context = layout.getContext();
		View view = LayoutInflater.from(context).inflate(R.layout.pezzo_nota, layout, false);
		layout.addView(view);
		TextView noteText = view.findViewById(R.id.nota_testo);
		noteText.setText(submitter.getName());
		view.findViewById(R.id.nota_fonti).setVisibility(View.GONE);
		view.setOnClickListener(v -> {
			Memory.setFirst(submitter);
			context.startActivity(new Intent(context, AuthorActivity.class));
		});
	}

	/**
	 * Adds a generic container with one or more links to parent records to the layout // Aggiunge al layout un contenitore generico con uno o più collegamenti a record capostipiti
	 * */
	public static void putContainer(LinearLayout layout, Object what, int title) {
		View view = LayoutInflater.from(layout.getContext()).inflate(R.layout.dispensa, layout, false);
		TextView titleView = view.findViewById(R.id.dispensa_titolo);
		titleView.setText(title);
		titleView.setBackground(AppCompatResources.getDrawable(layout.getContext(), R.drawable.sghembo)); // per android 4
		layout.addView(view);
		LinearLayout pantry = view.findViewById(R.id.dispensa_scatola);
		if( what instanceof Object[] ) {
			for( Object o : (Object[])what )
				putAny(pantry, o);
		} else
			putAny(pantry, what);
	}

	/**
	 * It recognizes the record type and adds the appropriate link to the box
	 */
	static void putAny(LinearLayout layout, Object record) {
		if( record instanceof Person )
			linkPerson(layout, (Person)record, 1);
		else if( record instanceof Source )
			placeSource(layout, (Source)record, false);
		else if( record instanceof Family )
			linkFamily(layout, (Family)record);
		else if( record instanceof Repository )
			RepositoryRefActivity.putRepository(layout, (Repository)record);
		else if( record instanceof Note )
			placeNote(layout, (Note)record, true);
		else if( record instanceof Media )
			linkMedia(layout, (Media)record);
		else if( record instanceof Submitter )
			linkSubmitter(layout, (Submitter)record);
	}

	/**
	 * Adds the piece with the change date and time to the layout
	 */
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
			LinearLayout noteLayout = changeView.findViewById(R.id.cambi_note);
			for( Extension otherTag : findExtensions(change) )
				place(noteLayout, otherTag.name, otherTag.text);
			// Thanks to my contribution the change date can have notes
			placeNotes(noteLayout, change, false);
			changeView.setOnClickListener(v -> {
				Memory.add(change);
				layout.getContext().startActivity(new Intent(layout.getContext(), ChangesActivity.class));
			});
		}
		return changeView;
	}

	/**
	 * Asks for confirmation to delete an item
	 */
	public static boolean preserve(Object what) {
		// todo Confirm delete
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

		// marks the authors on the first save
		if( Global.settings.getCurrentTree().grade == 9 ) {
			for( Submitter author : Global.gc.getSubmitters() )
				author.putExtension("passed", true);
			Global.settings.getCurrentTree().grade = 10;
			Global.settings.save();
		}

		if( Global.settings.autoSave )
			saveJson(Global.gc, Global.settings.openTree);
		else { // shows the Save button
			Global.shouldSave = true;
			if( Global.mainView != null ) {
				NavigationView menu = Global.mainView.findViewById(R.id.menu);
				menu.getHeaderView(0).findViewById(R.id.menu_salva).setVisibility(View.VISIBLE);
			}
		}
	}

	/**
	 * Update the change date of record(s)
	 */
	public static void updateChangeDate(Object... objects) {
		for( Object object : objects ) {
			try { // If updating doesn't have the get/setChange method, it passes silently
				Change change = (Change)object.getClass().getMethod("getChange").invoke(object);
				if( change == null ) // the record does not yet have a CHAN
					change = new Change();
				change.setDateTime(actualDateTime());
				object.getClass().getMethod("setChange", Change.class).invoke(object, change);
				// Extension with zone id, a string type 'America/Sao_Paulo'
				change.putExtension("zone", TimeZone.getDefault().getID());
			} catch( Exception e ) {
			}
		}
	}

	/**
	 * Return actual DateTime
	 */
	public static DateTime actualDateTime() {
		DateTime dateTime = new DateTime();
		Date now = new Date();
		dateTime.setValue(String.format(Locale.ENGLISH, "%te %<Tb %<tY", now));
		dateTime.setTime(String.format(Locale.ENGLISH, "%tT", now));
		return dateTime;
	}

	/**
	 * Save the Json
	 */
	static void saveJson(Gedcom gedcom, int treeId) {
		Header h = gedcom.getHeader();
		// Only if the header is from Family Gem
		if( h != null && h.getGenerator() != null
				&& h.getGenerator().getValue() != null && h.getGenerator().getValue().equals("FAMILY_GEM") ) {
			// Update the date and time
			h.setDateTime(actualDateTime());
			// Eventually update the version of Family Gem
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

	/**
	 * Evaluate whether there are individuals connectable with respect to an individual.
	 * Used to decide whether to show 'Link Existing Person' in the menu
	 */
	static boolean containsConnectableIndividuals(Person person) {
		int total = Global.gc.getPeople().size();
		if( total > 0 && (Global.settings.expert // the experts always can
				|| person == null) ) // in an empty family aRepresentativeOfTheFamily is null
			return true;
		int kin = ListOfPeopleFragment.countRelatives(person);
		return total > kin + 1;
	}

	/**
	 * Asks whether to reference an author in the header
	 */
	static void mainAuthor(Context context, final String authorId) {
		final Header[] head = {Global.gc.getHeader()};
		if( head[0] == null || head[0].getSubmitterRef() == null ) {
			new AlertDialog.Builder(context).setMessage(R.string.make_main_submitter)
					.setPositiveButton(android.R.string.yes, (dialog, id) -> {
						if( head[0] == null ) {
							head[0] = NewTree.createHeader(Global.settings.openTree + ".json");
							Global.gc.setHeader(head[0]);
						}
						head[0].setSubmitterRef(authorId);
						save(true);
					}).setNegativeButton(R.string.no, null).show();
		}
	}

	/**
	 * Returns the first non-passed author
	 */
	static Submitter newSubmitter(Gedcom gc) {
		for( Submitter author : gc.getSubmitters() ) {
			if( author.getExtension("passed") == null )
				return author;
		}
		return null;
	}

	/**
	 * Check if an author has participated in the shares, so as not to have them deleted
	 * */
	static boolean submitterHasShared(Submitter autore) {
		List<Settings.Share> shares = Global.settings.getCurrentTree().shares;
		boolean inviatore = false;
		if( shares != null )
			for( Settings.Share share : shares )
				if( autore.getId().equals(share.submitter) )
					inviatore = true;
		return inviatore;
	}

	/**
	 * String list of representative family members
	 */
	static String[] listFamilies(List<Family> familyList) {
		List<String> familyPivots = new ArrayList<>();
		for( Family fam : familyList ) {
			String label = familyText(Global.context, Global.gc, fam, true);
			familyPivots.add(label);
		}
		return familyPivots.toArray(new String[0]);
	}
	/** For a stud? anchor? reference? ("perno") who is a child in more than one family, ask which family to show
		@param thingToOpen what to open: <ul>
		<li>0 diagram of the previous family, without asking which family (first click on Diagram)</li>
		<li>1 diagram possibly asking which family</li>
		<li>2 family possibly asking which family</li>
		</ul>
	*/
	public static void askWhichParentsToShow(Context context, Person person, int thingToOpen) {
		if( person == null )
			finishParentSelection(context, null, 1, 0);
		else {
			List<Family> families = person.getParentFamilies(Global.gc);
			if( families.size() > 1 && thingToOpen > 0 ) {
				new AlertDialog.Builder(context).setTitle(R.string.which_family)
						.setItems(listFamilies(families), (dialog, quale) -> {
							finishParentSelection(context, person, thingToOpen, quale);
						}).show();
			} else
				finishParentSelection(context, person, thingToOpen, 0);
		}

	}
	private static void finishParentSelection(Context context, Person pivot, int whatToOpen, int whichFamily) {
		if( pivot != null )
			Global.indi = pivot.getId();
		if( whatToOpen > 0 ) // The family to show is set
			Global.familyNum = whichFamily; // it is usually 0
		if( whatToOpen < 2 ) { // Show the diagram
			if( context instanceof Principal ) { // Diagram, ListOfPeopleFragment or Principal itself
				FragmentManager fm = ((AppCompatActivity)context).getSupportFragmentManager();
				// Name of the previous fragment in the backstack
				String previousName = fm.getBackStackEntryAt(fm.getBackStackEntryCount() - 1).getName();
				if( previousName != null && previousName.equals("diagram") )
					fm.popBackStack(); // Clicking on Diagram removes the previous diagram fragment from the history
				fm.beginTransaction().replace(R.id.contenitore_fragment, new Diagram()).addToBackStack("diagram").commit();
			} else { // As an individual or as a family
				context.startActivity(new Intent(context, Principal.class));
			}
		} else { // The family is shown
			Family family = pivot.getParentFamilies(Global.gc).get(whichFamily);
			if( context instanceof FamilyActivity) { // Moving from Family to Family does not accumulate activities in the stack
				Memory.replaceFirst(family);
				((Activity)context).recreate();
			} else {
				Memory.setFirst(family);
				context.startActivity(new Intent(context, FamilyActivity.class));
			}
		}
	}

	/**
	 * For an anchor ("perno") who has multiple marriages it asks which one to show
	 * */
	public static void askWhichSpouseToShow(Context context, Person pivot, Family family) {
		if( pivot.getSpouseFamilies(Global.gc).size() > 1 && family == null ) {
			new AlertDialog.Builder(context).setTitle(R.string.which_family)
					.setItems(listFamilies(pivot.getSpouseFamilies(Global.gc)), (dialog, quale) -> {
						concludeSpouseChoice(context, pivot, null, quale);
					}).show();
		} else {
			concludeSpouseChoice(context, pivot, family, 0);
		}
	}
	private static void concludeSpouseChoice(Context context, Person pivot, Family family, int which) {
		Global.indi = pivot.getId();
		family = family == null ? pivot.getSpouseFamilies(Global.gc).get(which) : family;
		if( context instanceof FamilyActivity) {
			Memory.replaceFirst(family);
			((Activity)context).recreate(); // It does not accumulate activities on the stack
		} else {
			Memory.setFirst(family);
			context.startActivity(new Intent(context, FamilyActivity.class));
		}
	}

	/**
	 * Used to connect one person to another, in inexperienced mode only
	 * Checks if the pivot could or has multiple marriages and asks which one to attach a spouse or child to
	 * Also responsible for setting 'familyId' or 'location'
	 */
	static boolean checkMultipleMarriages(Intent intent, Context context, Fragment fragment) {
		String pivotId = intent.getStringExtra("idIndividuo");
		Person pivot = Global.gc.getPerson(pivotId);
		List<Family> parentsFamilies = pivot.getParentFamilies(Global.gc);
		List<Family> spouseFamilies = pivot.getSpouseFamilies(Global.gc);
		int relationship = intent.getIntExtra("relazione", 0);
		ArrayAdapter<NewRelativeDialog.FamilyItem> adapter = new ArrayAdapter<>(context, android.R.layout.simple_list_item_1);

		// Parents: There is already a family that has at least one empty slot
		if( relationship == 1 && parentsFamilies.size() == 1
				&& (parentsFamilies.get(0).getHusbandRefs().isEmpty() || parentsFamilies.get(0).getWifeRefs().isEmpty()) )
			intent.putExtra("idFamiglia", parentsFamilies.get(0).getId()); // add 'familyId' to the existing intent
			// if this family is already full of parents, 'idFamily' remains null
			// then the recipient's existing family will be searched or a new family will be created

		// Parents: There are multiple families
		if( relationship == 1 && parentsFamilies.size() > 1 ) {
			for( Family fam : parentsFamilies )
				if( fam.getHusbandRefs().isEmpty() || fam.getWifeRefs().isEmpty() )
					adapter.add(new NewRelativeDialog.FamilyItem(context, fam));
			if( adapter.getCount() == 1 )
				intent.putExtra("idFamiglia", adapter.getItem(0).family.getId());
			else if( adapter.getCount() > 1 ) {
				new AlertDialog.Builder(context).setTitle(R.string.which_family_add_parent)
						.setAdapter(adapter, (dialog, quale) -> {
							intent.putExtra("idFamiglia", adapter.getItem(quale).family.getId());
							finishCheckingMultipleMarriages(context, intent, fragment);
						}).show();
				return true;
			}
		}
		// Sibling
		else if( relationship == 2 && parentsFamilies.size() == 1 ) {
			intent.putExtra("idFamiglia", parentsFamilies.get(0).getId());
		} else if( relationship == 2 && parentsFamilies.size() > 1 ) {
			new AlertDialog.Builder(context).setTitle(R.string.which_family_add_sibling)
					.setItems(listFamilies(parentsFamilies), (dialog, quale) -> {
						intent.putExtra("idFamiglia", parentsFamilies.get(quale).getId());
						finishCheckingMultipleMarriages(context, intent, fragment);
					}).show();
			return true;
		}
		// Spouse
		else if( relationship == 3 && spouseFamilies.size() == 1 ) {
			if( spouseFamilies.get(0).getHusbandRefs().isEmpty() || spouseFamilies.get(0).getWifeRefs().isEmpty() ) // Se c'è uno slot libero
				intent.putExtra("idFamiglia", spouseFamilies.get(0).getId());
		} else if( relationship == 3 && spouseFamilies.size() > 1 ) {
			for( Family fam : spouseFamilies ) {
				if( fam.getHusbandRefs().isEmpty() || fam.getWifeRefs().isEmpty() )
					adapter.add(new NewRelativeDialog.FamilyItem(context, fam));
			}
			// In the case of zero eligible families, familyId remains null
			if( adapter.getCount() == 1 ) {
				intent.putExtra("idFamiglia", adapter.getItem(0).family.getId());
			} else if( adapter.getCount() > 1 ) {
				//adapter.add(new NewRelativeDialog.FamilyItem(context, pivot) );
				new AlertDialog.Builder(context).setTitle(R.string.which_family_add_spouse)
						.setAdapter(adapter, (dialog, which) -> {
							intent.putExtra("idFamiglia", adapter.getItem(which).family.getId());
							finishCheckingMultipleMarriages(context, intent, fragment);
						}).show();
				return true;
			}
		}
		// Child: A family already exists with or without children
		else if( relationship == 4 && spouseFamilies.size() == 1 ) {
			intent.putExtra("idFamiglia", spouseFamilies.get(0).getId());
		} // Son: there are many conjugal families
		else if( relationship == 4 && spouseFamilies.size() > 1 ) {
			new AlertDialog.Builder(context).setTitle(R.string.which_family_add_child)
					.setItems(listFamilies(spouseFamilies), (dialog, quale) -> {
						intent.putExtra("idFamiglia", spouseFamilies.get(quale).getId());
						finishCheckingMultipleMarriages(context, intent, fragment);
					}).show();
			return true;
		}
		// Not having found a family of the pivot, he tells the ListOfPeopleActivity to try to place the pivot in the recipient's family
		if( intent.getStringExtra("idFamiglia") == null && intent.getBooleanExtra("anagrafeScegliParente", false) )
			intent.putExtra("collocazione", "FAMIGLIA_ESISTENTE");
		return false;
	}

	/**
	 * Conclusion of the previous function
	 */
	static void finishCheckingMultipleMarriages(Context contesto, Intent intent, Fragment frammento) {
		if( intent.getBooleanExtra("anagrafeScegliParente", false) ) {
			// open ListOfPeopleFragment
			if( frammento != null )
				frammento.startActivityForResult(intent, 1401);
			else
				((Activity)contesto).startActivityForResult(intent, 1401);
		} else // open IndividualEditorActivity
			contesto.startActivity(intent);
	}

	/**
	 * Check that one or more families are empty and propose to eliminate them
	 * @param evenRunWhenDismissing tells to execute 'whatToDo' even when clicking Cancel or out of the dialog
	 */
	static boolean checkFamilyItem(Context context, Runnable whatToDo, boolean evenRunWhenDismissing, Family... families) {
		List<Family> items = new ArrayList<>();
		for( Family fam : families ) {
			int numMembers = fam.getHusbandRefs().size() + fam.getWifeRefs().size() + fam.getChildRefs().size();
			if( numMembers <= 1 && fam.getEventsFacts().isEmpty() && fam.getAllMedia(Global.gc).isEmpty()
					&& fam.getAllNotes(Global.gc).isEmpty() && fam.getSourceCitations().isEmpty() ) {
				items.add(fam);
			}
		}
		if( items.size() > 0 ) {
			new AlertDialog.Builder(context).setMessage(R.string.empty_family_delete)
					.setPositiveButton(android.R.string.yes, (dialog, i) -> {
						for( Family fam : items )
							ChurchFragment.deleteFamily(fam); // So it happens to save several times together ... but oh well
						if( whatToDo != null ) whatToDo.run();
					}).setNeutralButton(android.R.string.cancel, (dialog, i) -> {
						if( evenRunWhenDismissing ) whatToDo.run();
					}).setOnCancelListener(dialog -> {
						if( evenRunWhenDismissing ) whatToDo.run();
					}).show();
			return true;
		}
		return false;
	}

	/**
	 * Display a dialog to edit the ID of any record
	 */
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

	/**
	 * Show a Toast message even from a side thread
	 */
	static void toast(Activity activity, int message) {
		toast(activity, activity.getString(message));
	}

	static void toast(Activity activity, String message) {
		activity.runOnUiThread(() -> Toast.makeText(activity, message, Toast.LENGTH_LONG).show());
	}
}
