package app.familygem

import android.app.Application
import android.net.Uri
import android.text.format.DateUtils
import android.text.format.Formatter
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import app.familygem.Settings.Tree
import app.familygem.util.getBasicData
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import org.joda.time.LocalDateTime
import org.joda.time.format.DateTimeFormat
import java.io.BufferedReader
import java.util.Date
import java.util.zip.ZipInputStream

sealed class MainState {
    data class Success(val uri: Uri) : MainState()
    data class Error(val exception: Throwable) : MainState()

    fun getFolder(): Uri {
        return if (this is Success) uri else throw (this as Error).exception
    }
}

sealed class SaveState {
    data object Success : SaveState()
    data class Error(val message: String) : SaveState()
    data object Saving : SaveState()
}

sealed class RecoverState {
    data object Loading : RecoverState()
    data class Success(val files: List<BackupItem>) : RecoverState()
    data class Error(val message: String?) : RecoverState() // Making backup is not allowed
    data class Notice(val message: String) : RecoverState() // Making backup is allowed
}

data class TreeItem(val tree: Tree, var detail: String, var backupDone: Boolean? = null)

data class BackupItem(
    val documentFile: DocumentFile, val treeId: Int, val treeTitle: String, val size: String, val date: String, val invalid: Boolean
)

class InvalidUriException(message: String) : Exception(message)

class BackupViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        const val NO_URI = "noUri"
    }

    private val context = getApplication<Application>()
    val mainState = MutableLiveData<MainState>()
    val loading = MutableLiveData<Boolean>()
    val saveState = MutableLiveData<SaveState>()
    lateinit var treeItems: List<TreeItem>
    val canBackup = MutableLiveData<Boolean>()
    val recoverState = MutableStateFlow<RecoverState>(RecoverState.Loading)

    /** Displays or hides loading wheel. */
    fun working(hard: Boolean) {
        loading.postValue(hard)
    }

    fun displayFolder() {
        mainState.value = try {
            if (Global.settings.backupUri == NO_URI) throw InvalidUriException("Backup folder not chosen.")
            MainState.Success(Uri.parse(Global.settings.backupUri))
        } catch (exception: Exception) {
            MainState.Error(exception)
        }
    }

    fun setFolder(uri: Uri) {
        Global.settings.backupUri = uri.toString()
        Global.settings.save()
        recoverState.value = RecoverState.Loading
        displayFolder()
    }

    fun listActualTrees() {
        saveState.value = if (Global.settings.trees.isEmpty()) SaveState.Error("No trees.")
        else {
            treeItems = Global.settings.trees.map { TreeItem(it, it.getBasicData()) }
            SaveState.Success
        }
    }

    fun selectTree(item: TreeItem) {
        item.tree.backup = !item.tree.backup
        Global.settings.save()
        updateState()
    }

    /** Updates backup button state. */
    fun updateState() {
        canBackup.value = saveState.value is SaveState.Success && treeItems.any { it.tree.backup }
                && recoverState.value !is RecoverState.Error // Could be RecoverState.Notice
    }

    /** Checks for errors on backup folder and tries to return it. */
    fun getBackupFolder(): DocumentFile? = try {
        val uri = mainState.value!!.getFolder()
        val folder = DocumentFile.fromTreeUri(context, uri) ?: throw Exception("Backup folder is invalid.")
        if (!folder.isDirectory) throw Exception("Can't access backup folder.") // Checks if not existing or not accessible
        if (context.contentResolver.persistedUriPermissions.map { it.uri }.none { it == uri }) throw Exception("Can't write on backup folder.")
        folder
    } catch (exception: Exception) {
        recoverState.value = RecoverState.Error(exception.localizedMessage)
        null
    }

    /** Displays the list of recoverable backup files. */
    fun loadBackupFiles() = viewModelScope.launch(Dispatchers.IO) {
        getBackupFolder()?.let { folder ->
            recoverState.value = if (folder.listFiles().filterNot { it.isDirectory }.isEmpty()) {
                RecoverState.Notice("Backup folder is empty.")
            } else {
                val files = mutableListOf<BackupItem>()
                folder.listFiles().filter { it.isFile }.forEach { file ->
                    yield()
                    val treeId = "^(\\d+)_".toRegex().find(file.name!!)?.groupValues?.get(1)?.toInt() ?: Int.MAX_VALUE
                    var invalid = false
                    val treeTitle = try {
                        ZipInputStream(context.contentResolver.openInputStream(file.uri)).use { zipInputStream ->
                            generateSequence { zipInputStream.nextEntry }.first { it.name == "settings.json" }.let { _ ->
                                val json = zipInputStream.bufferedReader().use(BufferedReader::readText)
                                val zipped = Gson().fromJson(json, Settings.ZippedTree::class.java)
                                zipped.title
                            }
                        }
                    } catch (exception: NoSuchElementException) {
                        invalid = true
                        "Invalid backup"
                    } catch (exception: Exception) {
                        invalid = true
                        exception.localizedMessage
                    }
                    val size = Formatter.formatFileSize(context, file.length())
                    val date = DateUtils.getRelativeTimeSpanString(file.lastModified(), Date().time, DateUtils.MINUTE_IN_MILLIS).toString()
                    files.add(BackupItem(file, treeId, treeTitle, size, date, invalid))
                }
                RecoverState.Success(files.sortedWith(compareBy({ it.treeId }, { it.documentFile.name })))
            }
        }
    }

    fun deleteBackupFile(file: DocumentFile) {
        file.delete()
        recoverState.value = RecoverState.Loading
    }

    fun backupSelectedTrees() = viewModelScope.launch(Dispatchers.IO) {
        treeItems.forEach { item ->
            yield()
            if (item.tree.backup) { // Makes backup
                val result = backupTree(item.tree)
                if (result.isSuccess) {
                    item.detail = "Backup OK"
                    item.backupDone = true
                } else {
                    item.detail = result.exceptionOrNull()?.localizedMessage ?: "Error"
                    item.backupDone = false
                }
            } else { // Restores default values
                item.detail = item.tree.getBasicData()
                item.backupDone = null
            }
        }
        saveState.postValue(SaveState.Success)
        // Updates backup files list
        if (treeItems.any { it.tree.backup && it.backupDone == true })
            recoverState.value = RecoverState.Loading
    }

    private var backupJob: Job? = null

    /** Performs backup of a tree after some delay. */
    fun backupDelayed(treeId: Int) {
        backupJob?.cancel()
        backupJob = viewModelScope.launch(Dispatchers.IO) {
            delay(1000 * 60) // One minute
            val tree = Global.settings.getTree(treeId)
            val result = backupTree(tree)
            // Deletes old backup files keeping only the last 3
            if (result.isSuccess) {
                val backupFolder = result.getOrNull()
                val deletable = mutableListOf<DocumentFile>()
                backupFolder?.listFiles()?.forEach {
                    if ("^${treeId}_\\d{8}_\\d{6}\\.zip\$".toRegex().matches(it.name!!)) { // File with name like '12_20251231_123456.zip'
                        deletable.add(it)
                    }
                }
                deletable.sortedBy { it.lastModified() }.reversed().take(3).forEach { deletable.remove(it) }
                deletable.forEach { it.delete() }
                Log.i("BackupViewModel", "Backup tree $treeId success")
            } else Log.w("BackupViewModel", "Backup tree $treeId fail: $result")
        }
    }

    /** Creates backup file for one tree. */
    private suspend fun backupTree(tree: Tree): Result<DocumentFile> {
        return try {
            if (tree.backup) {
                val folder = DocumentFile.fromTreeUri(context, Uri.parse(Global.settings.backupUri))
                val exporter = Exporter(context)
                if (exporter.openTreeStrict(tree.id)) {
                    val format = DateTimeFormat.forPattern("yyyyMMdd_HHmmss")
                    val date = LocalDateTime.now().toString(format)
                    val file = folder?.createFile("application/zip", "${tree.id}_$date.zip")
                    exporter.exportZipBackup(null, -1, file!!.uri)
                    Result.success(folder)
                } else throw Exception(exporter.errorMessage)
            } else throw Exception("Backup not requested")
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }
}
