package app.familygem;

import android.content.Intent;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentManager;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.google.android.material.appbar.CollapsingToolbarLayout;
import org.folg.gedcom.model.Address;
import org.folg.gedcom.model.EventFact;
import org.folg.gedcom.model.Family;
import org.folg.gedcom.model.GedcomTag;
import org.folg.gedcom.model.Name;
import org.folg.gedcom.model.Note;
import org.folg.gedcom.model.NoteContainer;
import org.folg.gedcom.model.Person;
import org.folg.gedcom.model.SourceCitation;
import org.folg.gedcom.model.SourceCitationContainer;
import org.folg.gedcom.model.SpouseRef;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import app.familygem.constant.Gender;
import app.familygem.detail.EventActivity;
import app.familygem.detail.ExtensionActivity;
import app.familygem.detail.NameActivity;
import static app.familygem.Global.gc;

public class IndividualEventsFragment extends Fragment {

	Person one;
	private View changeView;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View eventsView = inflater.inflate(R.layout.individuo_scheda, container, false);
		if( gc != null ) {
			LinearLayout layout = eventsView.findViewById(R.id.contenuto_scheda);
			one = gc.getPerson(Global.indi);
			if( one != null ) {
				for( Name name : one.getNames()) {
					String title = getString(R.string.name);
					if( name.getType() != null && !name.getType().isEmpty() ) {
						title += " (" + TypeView.getTranslatedType(name.getType(), TypeView.Combo.NAME) + ")";
					}
					placeEvent(layout, title, U.firstAndLastName(name, " "), name);
				}
				for (EventFact fact : one.getEventsFacts() ) {
					String txt = "";
					if( fact.getValue() != null ) {
						if( fact.getValue().equals("Y") && fact.getTag()!=null &&
								( fact.getTag().equals("BIRT") || fact.getTag().equals("CHR") || fact.getTag().equals("DEAT") ) )
							txt = getString(R.string.yes);
						else txt = fact.getValue();
						txt += "\n";
					}
					//if( fact.getType() != null ) txt += fact.getType() + "\n"; // Included in event title
					if( fact.getDate() != null ) txt += new GedcomDateConverter(fact.getDate()).writeDateLong() + "\n";
					if( fact.getPlace() != null ) txt += fact.getPlace() + "\n";
					Address address = fact.getAddress();
					if( address != null ) txt += DetailActivity.writeAddress(address, true) + "\n";
					if( fact.getCause() != null ) txt += fact.getCause();
					if( txt.endsWith("\n") ) txt = txt.substring(0, txt.length() - 1); // Remove the last newline
					placeEvent(layout, writeEventTitle(fact), txt, fact);
				}
				for( Extension est : U.findExtensions(one) ) {
					placeEvent(layout, est.name, est.text, est.gedcomTag);
				}
				U.placeNotes(layout, one, true);
				U.placeSourceCitations(layout, one);
				changeView = U.placeChangeDate(layout, one.getChange());
			}
		}
		return eventsView;
	}

	/**
	 * Find out if it's a name with name pieces or a suffix in the value
	 * */
	boolean complexName(Name n ) {
		// Name pieces
		boolean hasAllFields /*TODO improve translation of ricco*/ = n.getGiven() != null || n.getSurname() != null
				|| n.getPrefix() != null || n.getSurnamePrefix() != null || n.getSuffix() != null
				|| n.getFone() != null || n.getRomn() != null;
		// Something after the surname
		String name = n.getValue();
		boolean hasSuffix = false;
		if( name != null ) {
			name = name.trim();
			if( name.lastIndexOf('/') < name.length()-1 )
				hasSuffix = true;
		}
		return hasAllFields || hasSuffix;
	}

	/**
	 * Compose the title of an event of the person
	 * */
	public static String writeEventTitle(EventFact event) {
		int str = 0;
		switch( event.getTag() ) {
			case "SEX": str = R.string.sex; break;
			case "BIRT": str = R.string.birth; break;
			case "BAPM": str = R.string.baptism; break;
			case "BURI": str = R.string.burial; break;
			case "DEAT": str = R.string.death; break;
			case "EVEN": str = R.string.event; break;
			case "OCCU": str = R.string.occupation; break;
			case "RESI": str = R.string.residence;
		}
		String txt;
		if( str != 0 )
			txt = Global.context.getString(str);
		else
			txt = event.getDisplayType();
		if( event.getType() != null )
			txt += " (" + event.getType() + ")";
		return txt;
	}

	private int chosenSex;
	private void placeEvent(LinearLayout layout, String title, String text, Object object) {
		View eventView = LayoutInflater.from(layout.getContext()).inflate(R.layout.individuo_eventi_pezzo, layout, false);
		layout.addView(eventView);
		((TextView)eventView.findViewById(R.id.evento_titolo)).setText(title);
		TextView textView = eventView.findViewById(R.id.evento_testo);
		if( text.isEmpty() ) textView.setVisibility(View.GONE);
		else textView.setText(text);
		if( Global.settings.expert && object instanceof SourceCitationContainer ) {
			List<SourceCitation> sourceCitations = ((SourceCitationContainer)object).getSourceCitations();
			TextView sourceView = eventView.findViewById(R.id.evento_fonti);
			if( !sourceCitations.isEmpty() ) {
				sourceView.setText(String.valueOf(sourceCitations.size()));
				sourceView.setVisibility(View.VISIBLE);
			}
		}
		LinearLayout otherLayout = eventView.findViewById(R.id.evento_altro);
		if( object instanceof NoteContainer )
			U.placeNotes(otherLayout, object, false);
		eventView.setTag(R.id.tag_object, object);
		registerForContextMenu(eventView);
		if( object instanceof Name ) {
			U.placeMedia(otherLayout, object, false);
			eventView.setOnClickListener(v -> {
				// If it is a complex name, it proposes entering expert mode
				if( !Global.settings.expert && complexName((Name)object) ) {
					new AlertDialog.Builder(getContext()).setMessage(R.string.complex_tree_advanced_tools)
							.setPositiveButton(android.R.string.ok, (dialog, i) -> {
								Global.settings.expert = true;
								Global.settings.save();
								Memory.add(object);
								startActivity(new Intent(getContext(), NameActivity.class));
							}).setNegativeButton(android.R.string.cancel, (dialog, i) -> {
								Memory.add(object);
								startActivity(new Intent(getContext(), NameActivity.class));
							}).show();
				} else {
					Memory.add(object);
					startActivity(new Intent(getContext(), NameActivity.class));
				}
			});
		} else if( object instanceof EventFact ) {
			// Sex fact
			if( ((EventFact)object).getTag() != null && ((EventFact)object).getTag().equals("SEX") ) {
				Map<String, String> sexes = new LinkedHashMap<>();
				sexes.put("M", getString(R.string.male));
				sexes.put("F", getString(R.string.female));
				sexes.put("U", getString(R.string.unknown));
				textView.setText(text);
				chosenSex = 0;
				for( Map.Entry<String, String> sex : sexes.entrySet() ) {
					if( text.equals(sex.getKey()) ) {
						textView.setText(sex.getValue());
						break;
					}
					chosenSex++;
				}
				if( chosenSex > 2 ) chosenSex = -1;
				eventView.setOnClickListener(view -> new AlertDialog.Builder(view.getContext())
						.setSingleChoiceItems(sexes.values().toArray(new String[0]), chosenSex, (dialog, item) -> {
							((EventFact)object).setValue(new ArrayList<>(sexes.keySet()).get(item));
							updateMaritalRoles(one);
							dialog.dismiss();
							refresh(1);
							U.save(true, one);
						}).show());
			} else { // All other events
				U.placeMedia(otherLayout, object, false);
				eventView.setOnClickListener(v -> {
					Memory.add(object);
					startActivity(new Intent(getContext(), EventActivity.class));
				});
			}
		} else if( object instanceof GedcomTag ) {
			eventView.setOnClickListener(v -> {
				Memory.add(object);
				startActivity(new Intent(getContext(), ExtensionActivity.class));
			});
		}
	}

	/**
	 * In all marital families, remove the spouse refs of 'person' and add one corresponding to the gender
	 * It is especially useful in case of Gedcom export to have the HUSB and WIFE aligned with the sex
	 * */
	static void updateMaritalRoles(Person person) {
		SpouseRef spouseRef = new SpouseRef();
		spouseRef.setRef(person.getId());
		boolean removed = false;
		for( Family fam : person.getSpouseFamilies(gc) ) {
			if( Gender.isFemale(person) ) { // Female 'person' will become a wife
				Iterator<SpouseRef> husbandRefs = fam.getHusbandRefs().iterator();
				while( husbandRefs.hasNext() ) {
					String hr = husbandRefs.next().getRef();
					if( hr != null && hr.equals(person.getId()) ) {
						husbandRefs.remove();
						removed = true;
					}
				}
				if( removed ) {
					fam.addWife(spouseRef);
					removed = false;
				}
			} else { // For all other sexs 'person' will become husband
				Iterator<SpouseRef> wifeRefs = fam.getWifeRefs().iterator();
				while( wifeRefs.hasNext() ) {
					String wr = wifeRefs.next().getRef();
					if( wr != null && wr.equals(person.getId()) ) {
						wifeRefs.remove();
						removed = true;
					}
				}
				if( removed ) {
					fam.addHusband(spouseRef);
					removed = false;
				}
			}
		}
	}

	// Contextual menu
	View pieceView;
	Object pieceObject;
	@Override
	public void onCreateContextMenu(ContextMenu menu, View view, ContextMenu.ContextMenuInfo info) {
		// menuInfo as usual is null
		pieceView = view;
		pieceObject = view.getTag(R.id.tag_object);
		if( pieceObject instanceof Name ) {
			menu.add(0, 200, 0, R.string.copy);
			if( one.getNames().indexOf(pieceObject) > 0 )
				menu.add(0, 201, 0, R.string.move_up);
			if( one.getNames().indexOf(pieceObject) < one.getNames().size() - 1 )
				menu.add(0, 202, 0, R.string.move_down);
			menu.add(0, 203, 0, R.string.delete);
		} else if( pieceObject instanceof EventFact ) {
			if( view.findViewById(R.id.evento_testo).getVisibility() == View.VISIBLE )
				menu.add(0, 210, 0, R.string.copy);
			if( one.getEventsFacts().indexOf(pieceObject) > 0 )
				menu.add(0, 211, 0, R.string.move_up);
			if( one.getEventsFacts().indexOf(pieceObject) < one.getEventsFacts().size() - 1 )
				menu.add(0, 212, 0, R.string.move_down);
			menu.add(0, 213, 0, R.string.delete);
		} else if( pieceObject instanceof GedcomTag ) {
			menu.add(0, 220, 0, R.string.copy);
			menu.add(0, 221, 0, R.string.delete);
		} else if( pieceObject instanceof Note ) {
			if( ((TextView)view.findViewById(R.id.nota_testo)).getText().length() > 0 )
				menu.add(0, 225, 0, R.string.copy);
			if( ((Note)pieceObject).getId() != null )
				menu.add(0, 226, 0, R.string.unlink);
			menu.add(0, 227, 0, R.string.delete);
		} else if( pieceObject instanceof SourceCitation ) {
			menu.add(0, 230, 0, R.string.copy);
			menu.add(0, 231, 0, R.string.delete);
		}
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		List<Name> names = one.getNames();
		List<EventFact> facts = one.getEventsFacts();
		int toUpdateId = 0; // what to update after the change
		switch( item.getItemId() ) {
			// Nome
			case 200: // Copy name
			case 210: // Copy event
			case 220: // Copy extension
				U.copyToClipboard(((TextView)pieceView.findViewById(R.id.evento_titolo)).getText(),
						((TextView)pieceView.findViewById(R.id.evento_testo)).getText());
				return true;
			case 201: //Move up
				names.add(names.indexOf(pieceObject) - 1, (Name)pieceObject);
				names.remove(names.lastIndexOf(pieceObject));
				toUpdateId = 2;
				break;
			case 202: // Sposta down
				names.add(names.indexOf(pieceObject) + 2, (Name)pieceObject);
				names.remove(names.indexOf(pieceObject));
				toUpdateId = 2;
				break;
			case 203: // Delete
				if( U.preserve(pieceObject) ) return false;
				one.getNames().remove(pieceObject);
				Memory.setInstanceAndAllSubsequentToNull(pieceObject);
				pieceView.setVisibility(View.GONE);
				toUpdateId = 2;
				break;
			// Generic Event
			case 211: // Move up
				facts.add(facts.indexOf(pieceObject) - 1, (EventFact)pieceObject);
				facts.remove(facts.lastIndexOf(pieceObject));
				toUpdateId = 1;
				break;
			case 212: // Move down
				facts.add(facts.indexOf(pieceObject) + 2, (EventFact)pieceObject);
				facts.remove(facts.indexOf(pieceObject));
				toUpdateId = 1;
				break;
			case 213:
				// todo Confirm delete
				one.getEventsFacts().remove(pieceObject);
				Memory.setInstanceAndAllSubsequentToNull(pieceObject);
				pieceView.setVisibility(View.GONE);
				break;
			// Extension
			case 221: // delete
				U.deleteExtension((GedcomTag)pieceObject, one, pieceView);
				break;
			// Nota
			case 225: // Copy
				U.copyToClipboard(getText(R.string.note), ((TextView)pieceView.findViewById(R.id.nota_testo)).getText());
				return true;
			case 226: // disconnect
				U.disconnectNote((Note)pieceObject, one, pieceView);
				break;
			case 227:
				Object[] heads = U.deleteNote((Note)pieceObject, pieceView);
				U.save(true, heads);
				refresh(0);
				return true;
			// source Citation
			case 230: // Copy
				U.copyToClipboard(getText(R.string.source_citation),
						((TextView)pieceView.findViewById(R.id.fonte_testo)).getText() + "\n"
								+ ((TextView)pieceView.findViewById(R.id.citazione_testo)).getText());
				return true;
			case 231: // delete
				// todo confirm : Do you want to delete this source citation? The source will continue to exist.
				one.getSourceCitations().remove(pieceObject);
				Memory.setInstanceAndAllSubsequentToNull(pieceObject);
				pieceView.setVisibility(View.GONE);
				break;
			default:
				return false;
		}
		U.save(true, one);
		refresh(toUpdateId);
		return true;
	}

	/**
	 * Update person ID in the toolbar and change date
	 * */
	void refreshId() {
		TextView idView = getActivity().findViewById(R.id.persona_id);
		idView.setText("INDI " + one.getId());
		refresh(1);
	}

	/**
	 * Update content of Facts tab
	 * */
	void refresh(int what) {
		if( what == 0 ) { // Only replace change date
			LinearLayout layout = getActivity().findViewById(R.id.contenuto_scheda);
			if( changeView != null )
				layout.removeView(changeView);
			changeView = U.placeChangeDate(layout, one.getChange());
		} else { // Reload the fragment
			FragmentManager fragmentManager = requireActivity().getSupportFragmentManager();
			fragmentManager.beginTransaction().detach(this).commit();
			fragmentManager.beginTransaction().attach(this).commit();
			if( what == 2 ) { // Also update person name in toolbar
				CollapsingToolbarLayout toolbarLayout = requireActivity().findViewById(R.id.toolbar_layout);
				toolbarLayout.setTitle(U.properName(one));
			}
		}
	}
}
