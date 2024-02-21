package app.familygem;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

public class BaseActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Updates activity title when one in-app language is selected
        try {
            int title = getPackageManager().getActivityInfo(getComponentName(), 0).labelRes;
            if (title != 0) setTitle(title);
        } catch (Exception ignored) {
        }
    }
}
