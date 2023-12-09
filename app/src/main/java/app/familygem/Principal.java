package app.familygem;

import static app.familygem.Global.gc;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.navigation.NavigationView;

import org.folg.gedcom.model.Media;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

import app.familygem.constant.Choice;
import app.familygem.constant.Image;
import app.familygem.list.FamiliesFragment;
import app.familygem.list.MediaFragment;
import app.familygem.list.NotesFragment;
import app.familygem.list.PersonsFragment;
import app.familygem.list.RepositoriesFragment;
import app.familygem.list.SourcesFragment;
import app.familygem.list.SubmittersFragment;
import app.familygem.util.FileUtil;
import app.familygem.util.TreeUtils;
import app.familygem.visitor.MediaList;
import app.familygem.visitor.NoteList;

public class Principal /*TODO Main?*/ extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {

    DrawerLayout scatolissima;
    Toolbar toolbar;
    NavigationView menuPrincipe;
    List<Integer> menuIds = Arrays.asList(R.id.nav_diagram, R.id.nav_persons, R.id.nav_families,
            R.id.nav_media, R.id.nav_notes, R.id.nav_sources, R.id.nav_repositories, R.id.nav_submitters, R.id.nav_settings);
    List<Class<?>> fragments = Arrays.asList(DiagramFragment.class, PersonsFragment.class, FamiliesFragment.class,
            MediaFragment.class, NotesFragment.class, SourcesFragment.class, RepositoriesFragment.class, SubmittersFragment.class,
            TreeSettingsFragment.class);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.principe);

        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        scatolissima = findViewById(R.id.scatolissima);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, scatolissima, toolbar, R.string.drawer_open, R.string.drawer_close);
        scatolissima.addDrawerListener(toggle);
        toggle.syncState();

        menuPrincipe = findViewById(R.id.menu);
        menuPrincipe.setNavigationItemSelectedListener(this);
        furnishMenu();

        if (savedInstanceState == null) { // Loads only the first time, not rotating the screen
            Fragment fragment;
            if (getIntent().getBooleanExtra(Choice.PERSON, false))
                fragment = new PersonsFragment();
            else if (getIntent().getBooleanExtra(Choice.MEDIA, false))
                fragment = new MediaFragment();
            else if (getIntent().getBooleanExtra(Choice.SOURCE, false))
                fragment = new SourcesFragment();
            else if (getIntent().getBooleanExtra(Choice.NOTE, false))
                fragment = new NotesFragment();
            else if (getIntent().getBooleanExtra(Choice.REPOSITORY, false))
                fragment = new RepositoriesFragment();
            else { // Regular opening
                fragment = new DiagramFragment();
            }
            getSupportFragmentManager().beginTransaction().replace(R.id.contenitore_fragment, fragment).addToBackStack(null).commit();
        }

        menuPrincipe.getHeaderView(0).findViewById(R.id.menu_alberi).setOnClickListener(v -> {
            scatolissima.closeDrawer(GravityCompat.START);
            startActivity(new Intent(Principal.this, TreesActivity.class));
        });

        // Hides some menu items for non-expert users
        if (!Global.settings.expert) {
            Menu menu = menuPrincipe.getMenu();
            menu.findItem(R.id.nav_sources).setVisible(false);
            menu.findItem(R.id.nav_repositories).setVisible(false);
            menu.findItem(R.id.nav_submitters).setVisible(false);
        }
    }

    // Chiamato praticamente sempre tranne che onBackPressed
    @Override
    public void onAttachFragment(@NonNull Fragment fragment) {
        super.onAttachFragment(fragment);
        if (!(fragment instanceof NewRelativeDialog || fragment instanceof DatePickerFragment))
            updateInterface(fragment);
    }

    boolean isActivityRestarting;

    @Override
    public void onRestart() {
        super.onRestart();
        isActivityRestarting = true;
    }

    // Aggiorna i contenuti quando si torna indietro con backPressed()
    @Override
    protected void onResume() {
        super.onResume();
        if (Global.edited && isActivityRestarting) {
            Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.contenitore_fragment);
            if (fragment instanceof DiagramFragment) {
                ((DiagramFragment)fragment).refreshDiagram();
            } else if (fragment instanceof PersonsFragment) {
                ((PersonsFragment)fragment).refresh();
            } else if (fragment instanceof FamiliesFragment) {
                ((FamiliesFragment)fragment).refresh(FamiliesFragment.What.RELOAD);
            } else if (fragment instanceof MediaFragment) {
                ((MediaFragment)fragment).refresh();
            } else if (fragment instanceof NotesFragment) {
                ((NotesFragment)fragment).refresh();
            } else { // This should gradually disappear
                Global.edited = false;
                isActivityRestarting = false;
                recreate();
                return;
            }
            furnishMenu(); // To display the Save button and update items count
            Global.edited = false;
            isActivityRestarting = false;
        }
    }

    // Updates title, random image, 'Save' button in menu header, and menu items count
    public void furnishMenu() {
        NavigationView navigation = scatolissima.findViewById(R.id.menu);
        View menuHeader = navigation.getHeaderView(0);
        ImageView imageView = menuHeader.findViewById(R.id.menu_immagine);
        TextView mainTitle = menuHeader.findViewById(R.id.menu_titolo);
        imageView.setVisibility(ImageView.GONE);
        mainTitle.setText("");
        if (Global.gc != null) {
            MediaList mediaList = new MediaList(Global.gc, 3);
            Global.gc.accept(mediaList);
            if (mediaList.list.size() > 0) {
                int random = new Random().nextInt(mediaList.list.size());
                for (Media media : mediaList.list)
                    if (--random < 0) { // Reaches -1
                        FileUtil.INSTANCE.showImage(media, imageView, Image.DARK);
                        imageView.setVisibility(ImageView.VISIBLE);
                        break;
                    }
            }
            mainTitle.setText(Global.settings.getCurrentTree().title);
            if (Global.settings.expert) {
                TextView treeNumView = menuHeader.findViewById(R.id.menu_number);
                treeNumView.setText(String.valueOf(Global.settings.openTree));
                treeNumView.setVisibility(ImageView.VISIBLE);
            }
            // Put count of existing records in menu items
            Menu menu = navigation.getMenu();
            for (int i = 1; i <= 7; i++) {
                int count = 0;
                switch (i) {
                    case 1:
                        count = gc.getPeople().size();
                        break;
                    case 2:
                        count = gc.getFamilies().size();
                        break;
                    case 3:
                        MediaList mediaList1 = new MediaList(gc, 0);
                        gc.accept(mediaList1);
                        count = mediaList1.list.size();
                        break;
                    case 4:
                        NoteList notesList = new NoteList();
                        gc.accept(notesList);
                        count = notesList.noteList.size() + gc.getNotes().size();
                        break;
                    case 5:
                        count = gc.getSources().size();
                        break;
                    case 6:
                        count = gc.getRepositories().size();
                        break;
                    case 7:
                        count = gc.getSubmitters().size();
                }
                TextView countView = menu.getItem(i).getActionView().findViewById(R.id.menu_item_text);
                if (count > 0) countView.setText(String.valueOf(count));
                else countView.setText("");
            }
        }
        // Save button
        Button saveButton = menuHeader.findViewById(R.id.menu_salva);
        saveButton.setOnClickListener(view -> {
            view.setVisibility(View.GONE);
            TreeUtils.INSTANCE.saveJsonAsync(Global.gc, Global.settings.openTree);
            scatolissima.closeDrawer(GravityCompat.START);
            Global.shouldSave = false;
            Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show();
        });
        saveButton.setOnLongClickListener(view -> {
            PopupMenu popup = new PopupMenu(this, view);
            popup.getMenu().add(0, 0, 0, R.string.revert);
            popup.show();
            popup.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == 0) {
                    // TODO: put in a Kotlin coroutine
                    TreeUtils.INSTANCE.openGedcomAsync(Global.settings.openTree, false);
                    U.whichParentsToShow(this, null, 0); // Basically simply reloads the diagram
                    scatolissima.closeDrawer(GravityCompat.START);
                    Global.edited = false;
                    Global.shouldSave = false;
                    furnishMenu();
                }
                return true;
            });
            return true;
        });
        saveButton.setVisibility(Global.shouldSave ? View.VISIBLE : View.GONE);
    }

    /**
     * Highlights one menu item and shows/hides the toolbar.
     */
    private void updateInterface(Fragment fragment) {
        if (fragment == null)
            fragment = getSupportFragmentManager().findFragmentById(R.id.contenitore_fragment);
        if (fragment != null) {
            int fragmentPosition = fragments.indexOf(fragment.getClass());
            if (menuPrincipe != null)
                menuPrincipe.setCheckedItem(menuIds.get(fragmentPosition));
            if (toolbar == null)
                toolbar = findViewById(R.id.toolbar);
            if (toolbar != null)
                toolbar.setVisibility(fragmentPosition == 0 ? View.GONE : View.VISIBLE);
        }
    }

    @Override
    public void onBackPressed() {
        if (scatolissima.isDrawerOpen(GravityCompat.START)) {
            scatolissima.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
            if (getSupportFragmentManager().getBackStackEntryCount() == 0) {
                // Fa tornare a TreesActivity invece di rivedere il primo diagramma del backstack
                super.onBackPressed();
            } else
                updateInterface(null);
        }
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        Fragment fragment = null;
        try {
            fragment = (Fragment)fragments.get(menuIds.indexOf(item.getItemId())).newInstance();
        } catch (Exception ignored) {
        }
        // Removes TreeSettingsFragment from back stack
        FragmentManager fm = getSupportFragmentManager();
        Fragment lastFragment = fm.getFragments().get(fm.getFragments().size() - 1);
        boolean deleteSettingsFragment = false;
        if (lastFragment instanceof TreeSettingsFragment) {
            deleteSettingsFragment = true;
        }
        // Adds the next fragment to main activity
        if (fragment instanceof DiagramFragment) {
            if (deleteSettingsFragment) fm.popBackStack();
            int whatToOpen;
            if (isCurrentFragment(DiagramFragment.class)) {
                // If we are already in diagram and we click Diagram, shows the root person
                Global.indi = Global.settings.getCurrentTree().root;
                whatToOpen = 1; // Possibly asks about multiple parents
            } else {
                whatToOpen = 0; // Shows the diagram without asking about multiple parents
            }
            Runnable openDiagram = () -> U.whichParentsToShow(this, gc.getPerson(Global.indi), whatToOpen);
            if (TreeUtils.INSTANCE.isGlobalGedcomOk(openDiagram)) openDiagram.run();
        } else {
            // Removes previous fragment from history if it's the same one we are about to see or if it's TreeSettingsFragment
            if (isCurrentFragment(fragment.getClass()) || deleteSettingsFragment) fm.popBackStack();
            fm.beginTransaction().replace(R.id.contenitore_fragment, fragment).addToBackStack(null).commit();
        }
        scatolissima.closeDrawer(GravityCompat.START);
        return true;
    }

    /**
     * Receives a class like 'DiagramFragment.class' and says whether it is the fragment currently visible.
     */
    private boolean isCurrentFragment(Class<?> aClass) {
        Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.contenitore_fragment);
        return aClass.isInstance(currentFragment);
    }

    // Automatically opens the 'Sort by' sub-menu
    @Override
    public boolean onMenuOpened(int featureId, Menu menu) {
        MenuItem item0 = menu.getItem(0);
        if (item0.getTitle().equals(getString(R.string.order_by))) {
            item0.setVisible(false); // a little hack to prevent options menu to appear
            new Handler().post(() -> {
                item0.setVisible(true);
                menu.performIdentifierAction(item0.getItemId(), 0);
            });
        }
        return super.onMenuOpened(featureId, menu);
    }
}
