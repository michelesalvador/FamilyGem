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
import org.joda.time.LocalDateTime;
import org.joda.time.LocalTime;
import org.joda.time.Years;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;

import app.familygem.constant.Extra;
import app.familygem.constant.Format;

/**
 * Manager of birthday notifications.
 */
public class Notifier {
    static final String TREE_ID_KEY = "targetTreeId";
    static final String PERSON_ID_KEY = "targetPersonId";
    static final String CHANNEL_ID = "birthdays";
    private final int FACTOR = 100000;
    private final LocalDateTime now = LocalDateTime.now();
    private final LocalTime notifyTime = LocalTime.parse(Global.settings.notifyTime);

    public enum What {REBOOT, UPDATE, CREATE, DELETE, DEFAULT}

    public Notifier(Context context, Gedcom gedcom, int treeId, What toDo) {
        Settings.Tree tree = Global.settings.getTree(treeId);

        // With custom fixed date is nonsense to create birthday notifications
        if (tree != null && tree.settings.customDate && toDo != What.DELETE) return;

        // Creates the notification channel, necessary only on API 26+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, context.getText(R.string.birthdays),
                    NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription(context.getString(R.string.person_birthday));
            context.getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }

        // Deletes previous Workish notifications
        WorkManager.getInstance(context).cancelAllWork(); // Todo remove after version 0.9.1

        switch (toDo) {
            case REBOOT: // After reboot restores all alarms from settings
                for (Settings.Tree tree1 : Global.settings.trees) {
                    if (tree1.settings.customDate) continue;
                    createAlarms(context, tree1);
                }
                break;
            case UPDATE: // Updates time of each birthday and recreates alarms for all trees
                LocalTime nowTime = now.toLocalTime();
                for (Settings.Tree tree1 : Global.settings.trees) {
                    if (tree1.settings.customDate) continue;
                    deleteAlarms(context, tree1);
                    for (Settings.Birthday birthday : tree1.birthdays) {
                        // Updates date time to reflect current setting
                        LocalDateTime dateTime = new LocalDateTime(birthday.date)
                                .withHourOfDay(notifyTime.getHourOfDay()).withMinuteOfHour(notifyTime.getMinuteOfHour());
                        // Same day than today but different year needs to be updated
                        if (dateTime.dayOfMonth().equals(now.dayOfMonth()) && dateTime.monthOfYear().equals(now.monthOfYear())
                                && dateTime.getYear() > now.getYear() && notifyTime.isAfter(nowTime)) {
                            dateTime = dateTime.withYear(now.getYear());
                            birthday.age -= 1;
                        }
                        birthday.date = dateTime.toDate().getTime();
                    }
                    createAlarms(context, tree1);
                }
                Global.settings.save();
                break;
            case DELETE: // Only deletes alarms
                deleteAlarms(context, tree);
                break;
            case CREATE: // Creates birthday notifications saving them to settings.json
                findBirthdays(gedcom, tree);
                createAlarms(context, tree);
                break;
            default:// Deletes old alarms too
                deleteAlarms(context, tree);
                findBirthdays(gedcom, tree);
                createAlarms(context, tree);
        }
    }

    /**
     * Selects people who have to celebrate their birthday and adds them to the settings.
     * Eventually saves settings.
     */
    void findBirthdays(Gedcom gedcom, Settings.Tree tree) {
        if (tree.birthdays == null)
            tree.birthdays = new ArrayList<>();
        else
            tree.birthdays.clear();
        for (Person person : gedcom.getPeople()) {
            Date birth = findBirth(person);
            if (birth != null) {
                // Calculates the number of years that will be turned on the next birthday
                LocalDateTime birthDay = new LocalDateTime(birth).withTime(notifyTime.getHourOfDay(), notifyTime.getMinuteOfHour(), 0, 0);
                LocalDateTime nextBirthday = birthDay.withYear(now.getYear());
                if (nextBirthday.isBefore(now)) nextBirthday = nextBirthday.plusYears(1);
                int years = Years.yearsBetween(birthDay, nextBirthday).getYears();
                if (years >= 0 && years <= tree.settings.lifeSpan) {
                    tree.birthdays.add(new Settings.Birthday(person.getId(), U.givenName(person),
                            U.properName(person), nextBirthday.toDate().getTime(), years));
                }
            }
        }
        Global.settings.save();
    }

    /**
     * Possibly finds the birth Date of a person or null.
     */
    private Date findBirth(Person person) {
        if (!U.isDead(person)) {
            for (EventFact event : person.getEventsFacts()) {
                if (event.getTag().equals("BIRT") && event.getDate() != null) {
                    GedcomDateConverter dater = new GedcomDateConverter(event.getDate());
                    if (dater.isSingleKind() && dater.firstDate.isFormat(Format.D_M_Y)) {
                        return dater.firstDate.date;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Generates an alarm from each birthday of the provided tree.
     */
    void createAlarms(Context context, Settings.Tree tree) {
        if (tree.birthdays == null) return;
        // Sorts birthdays by date
        Collections.sort(tree.birthdays, (birthday1, birthday2) -> {
            if (birthday1.date == birthday2.date) return 0;
            else return birthday1.date > birthday2.date ? 1 : -1;
        });
        int eventId = tree.id * FACTOR;  // Different for every tree
        AlarmManager alarmManager = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        long nowTime = now.toDate().getTime();
        for (Settings.Birthday birthday : tree.birthdays) {
            if (birthday.date > nowTime) { // Avoids setting alarm for a past birthday
                Intent intent = new Intent(context, NotifyReceiver.class)
                        .putExtra(Extra.ID, eventId)
                        .putExtra(Extra.TITLE, birthday.name + " (" + tree.title + ")")
                        .putExtra(Extra.TEXT, context.getString(R.string.turns_years_old, birthday.given, birthday.age))
                        .putExtra(Extra.TREE_ID, tree.id)
                        .putExtra(Extra.PERSON_ID, birthday.id);
                PendingIntent pendingIntent = PendingIntent.getBroadcast(context, eventId++, intent,
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_CANCEL_CURRENT);
                try { // If exact alarms permission is not granted throws SecurityException
                    alarmManager.setExact(AlarmManager.RTC, birthday.date, pendingIntent);
                } catch (Exception e) {
                    break; // There is a limit of 500 alarms on some devices
                }
            }
        }
    }

    /**
     * Deletes all alarms already set for a tree.
     */
    void deleteAlarms(Context context, Settings.Tree tree) {
        if (tree.birthdays == null) return;
        int eventId = tree.id * FACTOR;
        AlarmManager alarmManager = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        for (Settings.Birthday b : tree.birthdays) {
            Intent intent = new Intent(context, NotifyReceiver.class);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(context, eventId++, intent,
                    // Flags also need to be identical to alarm creator
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_CANCEL_CURRENT);
            alarmManager.cancel(pendingIntent);
        }
    }
}
