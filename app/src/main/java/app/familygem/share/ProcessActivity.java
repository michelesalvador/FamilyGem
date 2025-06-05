package app.familygem.share;

import android.content.Intent;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.cardview.widget.CardView;

import org.folg.gedcom.model.Change;
import org.folg.gedcom.model.Family;
import org.folg.gedcom.model.Gedcom;
import org.folg.gedcom.model.Media;
import org.folg.gedcom.model.Note;
import org.folg.gedcom.model.Person;
import org.folg.gedcom.model.Repository;
import org.folg.gedcom.model.Source;
import org.folg.gedcom.model.Submitter;

import app.familygem.BaseActivity;
import app.familygem.Global;
import app.familygem.R;
import app.familygem.U;
import app.familygem.constant.Extra;
import app.familygem.util.AddressUtilKt;
import app.familygem.util.FamilyUtil;
import app.familygem.util.FileUtil;

/**
 * Evaluates a record of the imported tree, with possible comparison with the corresponding record of the old tree.
 */
public class ProcessActivity extends BaseActivity {

    Class<?> clazz; // The class that rules the activity
    int destiny;

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        setContentView(R.layout.process_activity);

        if (!Comparison.getList().isEmpty()) {
            int max;
            int position;
            if (Comparison.get().autoContinue) {
                max = Comparison.get().numChoices;
                position = Comparison.get().choicesMade;
            } else {
                max = Comparison.getList().size();
                position = getIntent().getIntExtra(Extra.POSITION, 0);
            }
            ProgressBar bar = findViewById(R.id.process_progress);
            bar.setMax(max);
            bar.setProgress(position);
            ((TextView)findViewById(R.id.process_state)).setText(position + "/" + max);

            final Object obj = Comparison.getFront(this).object;
            final Object obj2 = Comparison.getFront(this).object2;
            if (obj != null) clazz = obj.getClass();
            else clazz = obj2.getClass();
            populateCard(Global.gc, Global.settings.openTree, R.id.process_old, obj);
            populateCard(Global.gc2, Global.treeId2, R.id.process_new, obj2);

            destiny = 2;

            Button okButton = findViewById(R.id.process_okButton);
            okButton.setBackground(AppCompatResources.getDrawable(getApplicationContext(), R.drawable.frecciona));
            if (obj == null) {
                destiny = 1;
                okButton.setText(R.string.add);
                okButton.setBackgroundColor(0xff00dd00); // getResources().getColor(R.color.accent)
                okButton.setHeight(30); // ineffective
            } else if (obj2 == null) {
                destiny = 3;
                okButton.setText(R.string.delete);
                okButton.setBackgroundColor(0xffff0000);
            } else if (Comparison.getFront(this).canBothAddAndReplace) {
                // Creates another "Add" button
                Button addButton = new Button(this);
                addButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                addButton.setTextColor(0xFFFFFFFF);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                params.rightMargin = 15;
                params.weight = 3;
                addButton.setLayoutParams(params);
                addButton.setText(R.string.add);
                addButton.setBackgroundColor(0xff00dd00);
                addButton.setOnClickListener(view -> {
                    Comparison.getFront(this).destiny = 1;
                    goAhead();
                });
                ((LinearLayout)findViewById(R.id.process_buttons)).addView(addButton, 1);
            }

            // Continues automatically if there is no double action to choose
            if (Comparison.get().autoContinue && !Comparison.getFront(this).canBothAddAndReplace) {
                Comparison.getFront(this).destiny = destiny;
                goAhead();
            }
            // Button to accept the update
            okButton.setOnClickListener(view -> {
                Comparison.getFront(this).destiny = destiny;
                goAhead();
            });
            // Button to ignore the update
            findViewById(R.id.process_ignoreButton).setOnClickListener(view -> {
                Comparison.getFront(this).destiny = 0;
                goAhead();
            });
        } else
            onBackPressed(); // Returns to CompareActivity
    }

    void populateCard(Gedcom gedcom, int treeId, int cardId, Object obj) {
        String title = "";
        String text = "";
        String date = "";
        CardView cardView = findViewById(cardId);
        ImageView imageView = cardView.findViewById(R.id.compare_image);
        if (obj instanceof Note) {
            Note note = (Note)obj;
            writeHeading(R.string.shared_note, note.getId());
            text = note.getValue();
            date = getDateTime(note.getChange());
        } else if (obj instanceof Submitter) {
            Submitter submitter = (Submitter)obj;
            writeHeading(R.string.submitter, submitter.getId());
            title = submitter.getName();
            if (submitter.getEmail() != null) text += submitter.getEmail() + "\n";
            if (submitter.getAddress() != null) text += AddressUtilKt.toString(submitter.getAddress(), true);
            date = getDateTime(submitter.getChange());
        } else if (obj instanceof Repository) {
            Repository repository = (Repository)obj;
            writeHeading(R.string.repository, repository.getId());
            title = repository.getName();
            if (repository.getAddress() != null)
                text += AddressUtilKt.toString(repository.getAddress(), true) + "\n";
            if (repository.getEmail() != null) text += repository.getEmail();
            date = getDateTime(repository.getChange());
        } else if (obj instanceof Media) {
            Media media = (Media)obj;
            writeHeading(R.string.shared_media, media.getId());
            if (media.getTitle() != null) title = media.getTitle();
            text = media.getFile();
            date = getDateTime(media.getChange());
            imageView.setVisibility(View.VISIBLE);
            FileUtil.INSTANCE.showImage(media, imageView, 0, null, null, treeId);
        } else if (obj instanceof Source) {
            Source source = (Source)obj;
            writeHeading(R.string.source, source.getId());
            if (source.getTitle() != null) title = source.getTitle();
            else if (source.getAbbreviation() != null) title = source.getAbbreviation();
            if (source.getAuthor() != null) text = source.getAuthor() + "\n";
            if (source.getPublicationFacts() != null) text += source.getPublicationFacts() + "\n";
            if (source.getText() != null) text += source.getText();
            date = getDateTime(source.getChange());
        } else if (obj instanceof Person) {
            Person person = (Person)obj;
            writeHeading(R.string.person, person.getId());
            title = U.properName(person);
            text = U.details(person, null);
            date = getDateTime(person.getChange());
            FileUtil.INSTANCE.selectMainImage(person, imageView, 0, gedcom, treeId);
        } else if (obj instanceof Family) {
            Family family = (Family)obj;
            writeHeading(R.string.family, family.getId());
            text = FamilyUtil.INSTANCE.writeMembers(this, gedcom, family, true);
            date = getDateTime(family.getChange());
        }
        // Title
        TextView titleView = cardView.findViewById(R.id.compare_title);
        if (title == null || title.isEmpty())
            titleView.setVisibility(View.GONE);
        else
            titleView.setText(title);
        // Text
        TextView textView = cardView.findViewById(R.id.compare_text);
        if (text.isEmpty()) textView.setVisibility(View.GONE);
        else {
            if (text.endsWith("\n")) text = text.substring(0, text.length() - 1);
            textView.setText(text);
        }
        // Change date
        View changeView = cardView.findViewById(R.id.compare_date);
        if (date.isEmpty()) changeView.setVisibility(View.GONE);
        else ((TextView)changeView.findViewById(R.id.changeDate_text)).setText(date);
        // Background color
        if (cardId == R.id.process_new) {
            cardView.setCardBackgroundColor(getResources().getColor(R.color.accent_medium));
        }
        // Invisible if empty
        if ((title == null || title.isEmpty()) && text.isEmpty() && date.isEmpty())
            cardView.setVisibility(View.GONE);
    }

    /**
     * Writes the type and the ID of the processed objects.
     */
    private void writeHeading(int type, String id) {
        ((TextView)findViewById(R.id.process_type)).setText(getString(type));
        TextView idView = findViewById(R.id.process_id);
        if (Global.settings.expert) idView.setText(id);
        else idView.setVisibility(View.GONE);
    }

    String getDateTime(Change change) {
        String dataOra = "";
        if (change != null)
            dataOra = change.getDateTime().getValue() + " - " + change.getDateTime().getTime();
        return dataOra;
    }

    /**
     * Opens next comparison or final confirmation.
     */
    private void goAhead() {
        Intent intent = new Intent();
        if (getIntent().getIntExtra(Extra.POSITION, 0) == Comparison.getList().size()) {
            // The comparisons are over
            intent.setClass(this, ConfirmationActivity.class);
        } else {
            // Next comparison
            intent.setClass(this, ProcessActivity.class);
            intent.putExtra(Extra.POSITION, getIntent().getIntExtra(Extra.POSITION, 0) + 1);
        }
        if (Comparison.get().autoContinue) {
            if (Comparison.getFront(this).canBothAddAndReplace)
                Comparison.get().choicesMade++;
            else finish(); // Removes the current front from the stack
        }
        startActivity(intent);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        onBackPressed();
        return true;
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        if (Comparison.get().autoContinue)
            Comparison.get().choicesMade--;
    }
}
