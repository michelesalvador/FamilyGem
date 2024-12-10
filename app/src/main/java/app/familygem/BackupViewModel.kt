package app.familygem

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import app.familygem.Settings.Tree
import app.familygem.util.getBasicData
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import org.joda.time.LocalDateTime
import org.joda.time.format.DateTimeFormat
import java.io.BufferedReader
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
    data class Success(val items: List<BackupItem>) : RecoverState()
    data class Error(val message: String?) : RecoverState() // Making backup is not allowed
    data class Notice(val message: String) : RecoverState() // Making backup is allowed
}

data class TreeItem(val tree: Tree, var detail: String, var backupDone: Boolean? = null)

data class BackupItem(val documentFile: DocumentFile, val treeId: Int, var label: String, var valid: Boolean?)

class InvalidUriException(message: String) : Exception(message)

class BackupViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        const val NO_URI = "noUri"
        private const val TAG = "BackupViewModel"
    }

    private val context = getApplication<Application>()
    val mainState = MutableLiveData<MainState>()
    val loading = MutableLiveData<Boolean>()
    val saveState = MutableLiveData<SaveState>()
    lateinit var treeItems: List<TreeItem>
    val canBackup = MutableLiveData<Boolean>()
    val recoverState = MutableStateFlow<RecoverState>(RecoverState.Loading)
    private var backupItems = listOf<BackupItem>()
    private var scheduleJob: Job? = null // Just to wait for next backup
    private var backupJob: Job? = null // The actual backup process TODO maybe each tree/backup should have its own backupJob
    private var backingFileName: String? = null // The DocumentFile we are currently backing up

    /** Displays or hides progress view. */
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
            treeItems = Global.settings.trees.filter { it.grade < 20 }.map { TreeItem(it, it.getBasicData()) }
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
                && backupJob?.isActive != true && recoverState.value !is RecoverState.Error // Could be RecoverState.Notice
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
            // Displays the list of files
            recoverState.value = if (folder.listFiles().filterNot { it.isDirectory }.isEmpty()) {
                RecoverState.Notice("Backup folder is empty.")
            } else {
                val items = mutableListOf<BackupItem>()
                folder.listFiles().filter { it.isFile }.forEach { file ->
                    // Tree ID from the first number of file name
                    val treeId = "^(\\d+)_".toRegex().find(file.name!!)?.groupValues?.get(1)?.toInt() ?: Int.MAX_VALUE
                    items.add(BackupItem(file, treeId, "Loading..", null))
                }
                backupItems = items.sortedWith(compareBy({ it.treeId }, { it.documentFile.name }))
                RecoverState.Success(backupItems)
            }
            // Opens each file to check whether is a valid backup that contains 'settings.json'
            backupItems.forEach { item ->
                yield()
                var valid: Boolean? = null
                val label = if (backupJob?.isActive == true && item.documentFile.name == backingFileName) {
                    "Creating backup.."
                } else try {
                    ZipInputStream(context.contentResolver.openInputStream(item.documentFile.uri)).use { zipInputStream ->
                        generateSequence { zipInputStream.nextEntry }.first { it.name == "settings.json" }.let { _ ->
                            val json = zipInputStream.bufferedReader().use(BufferedReader::readText)
                            val zippedTree = Gson().fromJson(json, Settings.ZippedTree::class.java)
                            return@use if (zippedTree.title != null) {
                                valid = true; zippedTree.title!!
                            } else {
                                valid = false; "Missing title"
                            }
                        }
                    }
                } catch (exception: Exception) {
                    valid = false
                    when (exception) {
                        is NoSuchElementException -> "Invalid backup"
                        is JsonSyntaxException -> "Invalid JSON"
                        else -> exception.localizedMessage ?: "Exception"
                    }
                }
                backupItems = backupItems.map { if (it == item) item.copy(label = label, valid = valid) else it }
                recoverState.value = RecoverState.Success(backupItems)
            }
        }
        working(false)
    }

    fun deleteBackupFile(item: BackupItem) {
        // Cancels any backup job in progress
        if (backupJob?.isActive == true && item.documentFile.name == backingFileName) {
            backupJob?.cancel()
        }
        if (item.documentFile.delete()) {
            backupItems = backupItems.filterNot { it == item }
            recoverState.value = RecoverState.Success(backupItems)
        }
    }

    /** Performs backup of selected trees. */
    fun backupSelectedTrees() {
        backupJob = viewModelScope.launch(Dispatchers.IO) {
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
            // Updates backup item list
            if (treeItems.any { it.tree.backup && it.backupDone == true })
                recoverState.value = RecoverState.Loading
        }
    }

    /** Performs backup of a tree after some delay. To be called on background. */
    fun backupDelayed(treeId: Int) {
        val tree = Global.settings.getTree(treeId)
        if (!tree.backup) {
            Log.v(TAG, "Tree $treeId does not require backup")
            return
        }
        scheduleJob?.cancel()
        scheduleJob = viewModelScope.launch(Dispatchers.Default) {
            Log.i(TAG, "Backup of tree $treeId scheduled")
            delay(1000 * 60) // One minute
            if (backupJob?.isActive == true) {
                Log.w(TAG, "Another backup is already in progress")
            } else {
                backupJob = viewModelScope.launch(Dispatchers.IO) {
                    backupTree(tree).onSuccess { backupFolder ->
                        // Deletes old backup files keeping only the last 3
                        val deletable = mutableListOf<DocumentFile>()
                        backupFolder.listFiles().forEach {
                            // File has name like '12_20251231_123456.zip'
                            if ("^${treeId}_\\d{8}_\\d{6}\\.zip\$".toRegex().matches(it.name!!)) deletable.add(it)
                        }
                        deletable.sortedBy { it.lastModified() }.reversed().take(3).forEach { deletable.remove(it) }
                        deletable.forEach { it.delete() }
                        if (deletable.isNotEmpty()) Log.i(TAG, "Deleted ${deletable.size} old backup files")
                    }
                }
            }
        }
    }

    /** Creates backup file for one tree. */
    private suspend fun backupTree(tree: Tree): Result<DocumentFile> {
        Log.i(TAG, "Backup of tree ${tree.id} started")
        return try {
            DocumentFile.fromTreeUri(context, Uri.parse(Global.settings.backupUri))?.let { folder ->
                val exporter = Exporter(context)
                if (exporter.openTreeStrict(tree.id)) {
                    val format = DateTimeFormat.forPattern("yyyyMMdd_HHmmss")
                    val date = LocalDateTime.now().toString(format)
                    backingFileName = "${tree.id}_$date.zip"
                    val file = folder.createFile("application/zip", backingFileName!!)
                    exporter.exportZipBackup(null, -1, file!!.uri)
                    Log.i(TAG, "Backup of tree ${tree.id} completed")
                    Result.success(folder)
                } else throw Exception(exporter.errorMessage)
            } ?: throw Exception("Backup folder unreachable")
        } catch (exception: Exception) {
            Log.w(TAG, "Backup of tree ${tree.id} failed: ${exception.message}")
            Result.failure(exception)
        }
    }
}
