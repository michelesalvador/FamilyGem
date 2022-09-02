// Manager of birthday notifications

package app.familygem;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.lifecycle.LifecycleObserver;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import org.folg.gedcom.model.EventFact;
import org.folg.gedcom.model.Person;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import app.familygem.constant.Format;

class Notifier implements LifecycleObserver {
	static final String TREE_ID_KEY = "targetTreeId";
	static final String INDI_ID_KEY = "targetIndiId";
	static final String NOTIFY_ID_KEY = "notifyId";
	static final String WORK_TAG = "notificationsForTree"; // Add tree.id to it, to be able to cancel all works related to a tree
	private static final String CHANNEL_ID = "birthdays";
	private final Date now = new Date();
	private final int notifyHour = 12;

	Notifier(Context context) {

		// Create the notification channel, necessary only on API 26+
		if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ) {
			NotificationChannel channel = new NotificationChannel(CHANNEL_ID, context.getText(R.string.birthdays),
					NotificationManager.IMPORTANCE_DEFAULT);
			channel.setDescription(context.getString(R.string.birthday_notified_midday));
			context.getSystemService(NotificationManager.class).createNotificationChannel(channel);
		}

		//WorkManager.getInstance(context).cancelAllWork(); // Ok per spurgare tutto

		// Select persons who have to celebrate their birthday
		Settings.Tree tree = Global.settings.getCurrentTree();
		WorkManager.getInstance(context).cancelAllWorkByTag(WORK_TAG + tree.id);
		List<Birthday> birthdays = new ArrayList<>();
		for( Person person : Global.gc.getPeople() ) {
			if( !U.isDead(person) ) {
				for( EventFact event : person.getEventsFacts() ) {
					if( event.getTag().equals("BIRT") && event.getDate() != null) {
						Datatore datator = new Datatore(event.getDate());
						if( datator.data1.date != null ) { // 'data1' could be a phrase
							int years = getYears(datator.data1.date);
							if( datator.isSingleKind() && datator.data1.isFormat(Format.D_M_Y) && years <= 120 ) {
								birthdays.add(new Birthday(person, datator.data1.date, years));
							}
						}
					}
				}
			}
		}

		// Create a Worker for each birthday
		int eventId = tree.id * 100000;  // Different for every tree
		for( Birthday birthday : birthdays ) {
			Data.Builder inputData = new Data.Builder()
					.putInt("id", eventId++)
					.putString("title", U.epiteto(birthday.person) + " (" + tree.title + ")")
					.putString("text", context.getString(R.string.turns_years_old, U.givenName(birthday.person), birthday.years))
					.putInt("treeId", tree.id)
					.putString("indiId", birthday.person.getId());

			OneTimeWorkRequest notificationWork = new OneTimeWorkRequest.Builder(NotifyWorker.class)
			//WorkRequest notificationWork = new PeriodicWorkRequest.Builder(NotifyWorker.class, 15, TimeUnit.MINUTES)
					.addTag(WORK_TAG + tree.id)
					.setInputData(inputData.build())
					.setInitialDelay(calcDelay(birthday.date), TimeUnit.MINUTES)
					//.setInitialDelay(5, TimeUnit.SECONDS)
					//.setConstraints(Constraints.NONE)
					.build();
			WorkManager.getInstance(context).enqueue(notificationWork);
			//WorkManager.getInstance(context).enqueue(U.epiteto(birthday.person), ExistingPeriodicWorkPolicy.REPLACE, notificationWork);
		}
	}

	// Count the number of years that will be turned on the next birthday
	private int getYears(Date birthday) {
		int diff = now.getYear() - birthday.getYear();
		if( birthday.getMonth() < now.getMonth()
				|| (birthday.getMonth() == now.getMonth() && birthday.getDate() < now.getDate())
				|| (birthday.getMonth() == now.getMonth() && birthday.getDate() == now.getDate() && now.getHours() >= notifyHour) )
			diff++;
		return diff;
	}

	// Calculate the number of minutes from now to the next birthday
	private long calcDelay(Date birthday) {
		int year = now.getYear();
		if( birthday.getMonth() < now.getMonth()
				|| (birthday.getMonth() == now.getMonth() && birthday.getDate() < now.getDate())
				|| (birthday.getMonth() == now.getMonth() && birthday.getDate() == now.getDate() && now.getHours() >= notifyHour) )
			year++;
		birthday.setYear(year);
		birthday.setHours(notifyHour);
		long diff = birthday.getTime() - now.getTime();
		return TimeUnit.MINUTES.convert(diff, TimeUnit.MILLISECONDS);
	}

	// This class represents the birthday of a person
	private static class Birthday {
		Person person;
		Date date; // Date of birthday
		int years; // Turned years
		public Birthday(Person person, Date date, int years) {
			this.person = person;
			this.date = date;
			this.years = years;
		}
		@Override
		public String toString() {
			DateFormat sdf = new SimpleDateFormat("d MMM y", Locale.US);
			return "[" + U.epiteto(person) + ": " + years + " (" + sdf.format(date) + ")]";
		}
	}

	// The notification
	static public class NotifyWorker extends Worker {
		Context context;
		public NotifyWorker(@NonNull Context context, @NonNull WorkerParameters params) {
			super(context, params);
			this.context = context;
		}
		@Override
		public Result doWork() {
			Data inputData = getInputData();
			Intent intent = new Intent()
					.setClass(context, Alberi.class)
					.putExtra(TREE_ID_KEY, inputData.getInt("treeId", 0))
					.putExtra(INDI_ID_KEY, inputData.getString("indiId"))
					.putExtra(NOTIFY_ID_KEY, inputData.getInt("id", 0));
			PendingIntent pendingIntent = PendingIntent.getActivity(context, inputData.getInt("id", 0),
					intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

			NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
					.setSmallIcon(R.drawable.albero_cherokee)
					.setContentTitle(inputData.getString("title"))
					.setContentText(inputData.getString("text"))
					.setPriority(NotificationCompat.PRIORITY_MAX)
					.setContentIntent(pendingIntent)
					.setAutoCancel(true)
					.setCategory(NotificationCompat.CATEGORY_EVENT);

			NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
			notificationManager.notify(inputData.getInt("id", 0), builder.build());

			// Cancel this "periodic" work
			//WorkManager.getInstance(context).cancelWorkById(getId());
			return Result.success();
		}
	}
}
