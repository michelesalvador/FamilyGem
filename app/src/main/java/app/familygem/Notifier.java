package app.familygem;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import androidx.work.WorkManager;
import org.folg.gedcom.model.EventFact;
import org.folg.gedcom.model.Gedcom;
import org.folg.gedcom.model.Person;
import java.util.Date;
import java.util.HashSet;
import app.familygem.constant.Format;

/**
 * Manager of birthday notifications
 * */
class Notifier {
	static final String TREE_ID_KEY = "targetTreeId";
	static final String INDI_ID_KEY = "targetIndiId";
	static final String NOTIFY_ID_KEY = "notifyId";
	static final String CHANNEL_ID = "birthdays";
	private final int FACTOR = 100000;
	private final Date now = new Date();
	enum What {REBOOT, CREATE, DELETE, DEFAULT}

	Notifier(Context context, Gedcom gedcom, int treeId, What toDo) {

		// Create the notification channel, necessary only on API 26+
		if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ) {
			NotificationChannel channel = new NotificationChannel(CHANNEL_ID, context.getText(R.string.birthdays),
					NotificationManager.IMPORTANCE_DEFAULT);
			channel.setDescription(context.getString(R.string.birthday_notified_midday));
			context.getSystemService(NotificationManager.class).createNotificationChannel(channel);
		}

		// Delete previous Workish notifications
		WorkManager.getInstance(context).cancelAllWork(); // Todo remove after version 0.9.1

		Settings.Tree tree = Global.settings.getTree(treeId);
		switch( toDo ) {
			case REBOOT: // After reboot restore all alarms from settings
				for( Settings.Tree tree1 : Global.settings.trees ) {
					createAlarms(context, tree1);
				}
				break;
			case DELETE: // Only delete alarms
				deleteAlarms(context, tree);
				break;
			case CREATE: // Create birthday notifications saving them to settings.json
				findBirthdays(gedcom, tree);
				createAlarms(context, tree);
				break;
			default:// Delete old alarms too
				deleteAlarms(context, tree);
				findBirthdays(gedcom, tree);
				createAlarms(context, tree);
		}
	}

	/**
	 * Select people who have to celebrate their birthday and add them to the settings
	 * Eventually save settings
	 */
	void findBirthdays(Gedcom gedcom, Settings.Tree tree) {
		if( tree.birthdays == null )
			tree.birthdays = new HashSet<>();
		else
			tree.birthdays.clear();
		for( Person person : gedcom.getPeople() ) {
			Date birth = findBirth(person);
			if( birth != null ) {
				int years = findAge(birth);
				if( years >= 0 ) {
					tree.birthdays.add(new Settings.Birthday(person.getId(), U.givenName(person),
							U.properName(person), nextBirthday(birth), years));
				}
			}
		}
		Global.settings.save();
	}

	/**
	 * Possibly find the birth Date of a person
	 */
	private Date findBirth(Person person) {
		if( !U.isDead(person) ) {
			for( EventFact event : person.getEventsFacts() ) {
				if( event.getTag().equals("BIRT") && event.getDate() != null ) {
					GedcomDateConverter dateConverter = new GedcomDateConverter(event.getDate());
					if( dateConverter.isSingleKind() && dateConverter.data1.isFormat(Format.D_M_Y) ) {
						return dateConverter.data1.date;
					}
				}
			}
		}
		return null;
	}

	/**
	 * Count the number of years that will be turned on the next birthday
	 */
	private int findAge(Date birth) {
		int years = now.getYear() - birth.getYear();
		if( birth.getMonth() < now.getMonth()
				|| (birth.getMonth() == now.getMonth() && birth.getDate() < now.getDate())
				|| (birth.getMonth() == now.getMonth() && birth.getDate() == now.getDate() && now.getHours() >= 12) )
			years++;
		return years <= 120 ? years : -1;
	}

	/**
	 * From birth Date find next birthday as long timestamp
	 */
	private long nextBirthday(Date birth) {
		birth.setYear(now.getYear());
		birth.setHours(12);
		//birth.setMinutes(0);
		if( now.after(birth) )
			birth.setYear(now.getYear() + 1);
		return birth.getTime();
	}

	/**
	 * Generate an alarm from each birthday of the provided tree
	 */
	void createAlarms(Context context, Settings.Tree tree) {
		if( tree.birthdays == null ) return;
		int eventId = tree.id * FACTOR;  // Different for every tree
		AlarmManager alarmManager = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
		for( Settings.Birthday birthday : tree.birthdays ) {
			if( birthday.date > now.getTime() ) { // Avoid setting alarm for a past birthday
				Intent intent = new Intent(context, NotifyReceiver.class)
						.putExtra("id", eventId)
						.putExtra("title", birthday.name + " (" + tree.title + ")")
						.putExtra("text", context.getString(R.string.turns_years_old, birthday.given, birthday.age))
						.putExtra("treeId", tree.id)
						.putExtra("indiId", birthday.id);
				PendingIntent pendingIntent = PendingIntent.getBroadcast(context, eventId++, intent,
						PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_CANCEL_CURRENT);
				try {
					alarmManager.setExact(AlarmManager.RTC, birthday.date, pendingIntent);
				} catch( Exception e ) {
					break; // There is a limit of 500 alarms on some devices
				}
			}
		}
	}

	/**
	 * Delete all alarms already set for a tree
	 */
	void deleteAlarms(Context context, Settings.Tree tree) {
		if( tree.birthdays == null ) return;
		int eventId = tree.id * FACTOR;
		AlarmManager alarmManager = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
		for( Settings.Birthday b : tree.birthdays ) {
			Intent intent = new Intent(context, NotifyReceiver.class);
			PendingIntent pendingIntent = PendingIntent.getBroadcast(context, eventId++, intent,
					// Flags also need to be identical to alarm creator
					PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_CANCEL_CURRENT);
			alarmManager.cancel(pendingIntent);
		}
	}
}
