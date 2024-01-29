package app.familygem

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.test.espresso.Espresso
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.rules.activityScenarioRule
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import app.familygem.util.FileUtil
import app.familygem.util.TreeUtil
import kotlinx.coroutines.test.runTest
import org.apache.commons.io.FileUtils
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * Exports a tree with media files to GEDCOM and ZIP backup.
 */
@RunWith(AndroidJUnit4ClassRunner::class)
class ExportTest {

    @get:Rule
    val writePermissionRule: TestRule = if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
        GrantPermissionRule.grant(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
    } else GrantPermissionRule.grant()

    @get:Rule
    val activityScenarioRule = activityScenarioRule<LauncherActivity>()

    private lateinit var testContext: Context // Test context (to access files in /assets)
    private lateinit var appContext: Context // Context of app.familygem

    @Before
    fun setup() {
        testContext = InstrumentationRegistry.getInstrumentation().context
        appContext = InstrumentationRegistry.getInstrumentation().targetContext
    }

    @Test
    fun exportTreeWithMediaFiles() = runTest {
        Global.settings.trees.clear()
        assertEquals(Global.settings.trees.size, 0)

        onView(withId(R.id.fab)).perform(click())
        onView(withId(R.id.new_import_gedcom)).check(matches(isDisplayed()))

        // Imports /assets/media.ged as a new tree
        val inputStream = testContext.assets.open("media.ged")
        assertNotNull(inputStream)
        val gedcomFile = File(appContext.cacheDir, "media.ged")
        assertNotNull(gedcomFile)
        FileOutputStream(gedcomFile).use { inputStream.copyTo(it) }
        assertTrue(gedcomFile.isFile)
        // This import fails on Android 4, 5 and 6 throwing a strange OutOfMemory error
        TreeUtil.importGedcom(appContext, Uri.fromFile(gedcomFile), {}, {})
        Espresso.pressBack()
        assertEquals(Global.settings.trees.size, 1)
        val mediaTree = Global.settings.trees[Global.settings.trees.size - 1]
        assertEquals(mediaTree.title, "media")

        // Puts some files in various Android folders

        // PDF in external storage
        val path1 = appContext.getExternalFilesDir(mediaTree.id.toString())!!.path
        val input = testContext.assets.open("È Carmelo.pdf")
        val pdfFile = File(path1, "È Carmelo.pdf")
        FileOutputStream(pdfFile).use { input.copyTo(it) }
        assertTrue(pdfFile.isFile)

        // Scoped Storage of Android 10 limits access only to certain folders
        val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        if (!documentsDir.exists()) documentsDir.mkdir() // Necessary
        assertTrue(documentsDir.exists())
        assertTrue(documentsDir.isDirectory)

        // TXT in shared storage
        val subFolder = "SubFolder"
        var path2 = File(Environment.getExternalStorageDirectory(), subFolder)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            path2 = File(documentsDir, subFolder)
        }
        val pathFile = File(path2, "path.txt")
        assertNotNull(pathFile)
        FileUtils.writeStringToFile(pathFile, pathFile.path, "UTF-8")
        assertTrue(pathFile.isFile)
        assertNotEquals(pathFile.length(), 0)

        // Two TXT files with same name in two subfolders
        val subFolderBis = "SubFolder Bis"
        var path3 = File(Environment.getExternalStorageDirectory(), subFolderBis)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            path3 = File(documentsDir, subFolderBis)
        }
        val homonym = "homonym.txt"
        val firstFile = File("$path3/first", homonym)
        assertNotNull(firstFile)
        FileUtils.writeStringToFile(firstFile, firstFile.path, "UTF-8")
        assertTrue(firstFile.isFile)
        assertNotEquals(firstFile.length(), 0)
        // Second TXT file with same name but in another subfolder
        val secondFile = File("$path3/second", homonym)
        assertNotNull(secondFile)
        FileUtils.writeStringToFile(secondFile, secondFile.path, "UTF-8")
        assertTrue(secondFile.isFile)
        assertNotEquals(secondFile.length(), 0)

        // Paths in preferences
        mediaTree.dirs.add(path1)
        assertTrue(mediaTree.dirs.contains(path1))
        mediaTree.dirs.add(path2.absolutePath)
        assertTrue(mediaTree.dirs.contains(path2.absolutePath))
        mediaTree.dirs.add(path3.absolutePath)
        assertTrue(mediaTree.dirs.contains(path3.absolutePath))
        Global.settings.save()

        // TODO: test tree Uri

        // Exports in /Documents the tree as GEDCOM file
        val treeId = mediaTree.id
        assertEquals(treeId, 1)
        val fileGedcom = File(documentsDir, "Küçük ağaç.ged")
        val exporter1 = Exporter(appContext)
        assertTrue(exporter1.openTree(treeId))
        assertNull(exporter1.successMessage)
        assertNull(exporter1.errorMessage)
        assertTrue(exporter1.exportGedcom(Uri.fromFile(fileGedcom)))
        assertTrue(fileGedcom.isFile)
        assertNotEquals(fileGedcom.length(), 0)
        assertEquals(exporter1.successMessage, appContext.getString(R.string.gedcom_exported_ok))

        // Exports in /Documents the tree as zipped GEDCOM with media files
        val fileGedcomZip = File(documentsDir, "ਸੰਕੁਚਿਤ.zip")
        val exporter2 = Exporter(appContext)
        assertTrue(exporter2.openTree(treeId))
        val result1 = exporter2.exportGedcomToZip(Uri.fromFile(fileGedcomZip))
        assertTrue(result1)
        assertEquals(exporter2.successMessage, appContext.getString(R.string.zip_exported_ok))
        assertTrue(fileGedcomZip.isFile)

        // Exports in /Documents the tree as ZIP backup
        val fileBackup = File(documentsDir, "バックアップ.zip")
        val exporter3 = Exporter(appContext)
        assertTrue(exporter3.openTree(treeId))
        val result2 = exporter3.exportZipBackup(null, -1, Uri.fromFile(fileBackup))
        assertTrue(result2)
        assertEquals(exporter3.successMessage, appContext.getString(R.string.zip_exported_ok))
        assertTrue(fileBackup.isFile)

        // Deletes created files
        FileUtil.deleteFilesAndDirs(path2) // SubFolder
        assertFalse(pathFile.exists())
        assertFalse(path2.exists())
        FileUtil.deleteFilesAndDirs(path3) // SubFolder Bis
        assertFalse(firstFile.exists())
        assertFalse(secondFile.exists())
        assertFalse(path3.exists())

        // The exported zipped GEDCOM and ZIP backup should have 3 files each inside the /media folder
    }
}
