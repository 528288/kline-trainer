package com.stock.trainer.data.csv

import android.content.Context
import com.stock.trainer.data.local.KLineDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataInitializer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val kLineDao: KLineDao,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val importMutex = Mutex()
    private var backgroundJob: Job? = null
    private val _progress = MutableStateFlow(DataInitState())
    val progress: StateFlow<DataInitState> = _progress.asStateFlow()

    suspend fun initializeIfNeeded() = withContext(Dispatchers.IO) {
        val zipFiles = loadZipFiles() ?: return@withContext
        if (zipFiles.isEmpty()) {
            _progress.value = DataInitState(isReady = true, isComplete = true)
            return@withContext
        }

        resetStaleKLineDataIfNeeded()

        val readyCount = trainableCount(MIN_DAY_BARS_FOR_BOOTSTRAP)
        val importedCount = trainableCount(MIN_DAY_BARS_FOR_FULL_IMPORT)
        val allImported = importedCount >= zipFiles.size && isCurrentVersionFullyImported()

        if (readyCount >= BOOTSTRAP_SYMBOLS) {
            _progress.value = DataInitState(
                isReady = true,
                isBackgroundLoading = !allImported,
                importedSymbols = importedCount,
                totalSymbols = zipFiles.size,
                progress = importedCount.toFloat() / zipFiles.size,
                isComplete = allImported,
            )
            if (!allImported) startBackgroundImport()
            return@withContext
        }

        importNextBatch(
            targetReadyCount = BOOTSTRAP_SYMBOLS,
            maxSymbols = BOOTSTRAP_SYMBOLS,
            foreground = true,
        )
        startBackgroundImport()
    }

    fun startBackgroundImport() {
        if (backgroundJob?.isActive == true) return
        backgroundJob = scope.launch {
            runBackgroundImport()
        }
    }

    suspend fun importSomeForTraining(minDayCount: Int, desiredReadyCount: Int = TRAINING_READY_TARGET) {
        withContext(Dispatchers.IO) {
            val readyCount = trainableCount(minDayCount)
            if (readyCount >= desiredReadyCount) {
                startBackgroundImport()
                return@withContext
            }
            importNextBatch(
                targetReadyCount = desiredReadyCount,
                maxSymbols = TRAINING_BATCH_SYMBOLS,
                foreground = false,
                minDayCount = minDayCount,
            )
            startBackgroundImport()
        }
    }

    private suspend fun runBackgroundImport() {
        val zipFiles = loadZipFiles() ?: return
        if (zipFiles.isEmpty()) return

        while (true) {
            val importedBefore = trainableCount(MIN_DAY_BARS_FOR_FULL_IMPORT)
            if (importedBefore >= zipFiles.size) {
                markComplete(zipFiles.size)
                return
            }

            val importedNow = importNextBatch(
                targetReadyCount = Int.MAX_VALUE,
                maxSymbols = BACKGROUND_BATCH_SYMBOLS,
                foreground = false,
            )
            if (importedNow == 0) {
                val importedAfter = trainableCount(MIN_DAY_BARS_FOR_FULL_IMPORT)
                if (importedAfter >= zipFiles.size) {
                    markComplete(zipFiles.size)
                }
                return
            }
            delay(BACKGROUND_BATCH_DELAY_MS)
        }
    }

    private suspend fun importNextBatch(
        targetReadyCount: Int,
        maxSymbols: Int,
        foreground: Boolean,
        minDayCount: Int = MIN_DAY_BARS_FOR_BOOTSTRAP,
    ): Int = importMutex.withLock {
        val zipFiles = loadZipFiles() ?: return@withLock 0
        if (zipFiles.isEmpty()) {
            _progress.value = DataInitState(isReady = true, isComplete = true)
            return@withLock 0
        }

        var importedThisRun = 0
        var readyCount = trainableCount(minDayCount)
        var importedCount = trainableCount(MIN_DAY_BARS_FOR_FULL_IMPORT)

        if (foreground) {
            _progress.value = DataInitState(
                isLoading = true,
                isBackgroundLoading = false,
                importedSymbols = importedCount,
                totalSymbols = zipFiles.size,
                progress = bootstrapProgress(readyCount, targetReadyCount),
            )
        } else {
            updateBackgroundState(importedCount, zipFiles.size)
        }

        for (zipName in orderedZipFiles(zipFiles)) {
            if (readyCount >= targetReadyCount || importedThisRun >= maxSymbols) break

            val symbol = zipName.removeSuffix(".zip")
            if (isSymbolImportValid(symbol, MIN_DAY_BARS_FOR_FULL_IMPORT)) continue

            if (foreground) {
                _progress.value = DataInitState(
                    isLoading = true,
                    currentFile = symbol,
                    importedSymbols = importedCount,
                    totalSymbols = zipFiles.size,
                    progress = bootstrapProgress(readyCount, targetReadyCount),
                )
            } else {
                updateBackgroundState(importedCount, zipFiles.size, symbol)
            }

            try {
                importSymbolZip(symbol, zipName)
                importedThisRun++
                importedCount = trainableCount(MIN_DAY_BARS_FOR_FULL_IMPORT)
                if (isSymbolImportValid(symbol, minDayCount)) {
                    readyCount++
                }
                saveResumeSymbol(symbol)
            } catch (e: Exception) {
                _progress.value = DataInitState(
                    isReady = readyCount > 0,
                    isBackgroundLoading = !foreground,
                    error = "导入 $symbol 失败: ${e.message}",
                    importedSymbols = importedCount,
                    totalSymbols = zipFiles.size,
                    progress = importedCount.toFloat() / zipFiles.size,
                )
                return@withLock importedThisRun
            }
        }

        val allImported = importedCount >= zipFiles.size
        if (allImported) {
            markComplete(zipFiles.size)
        } else if (readyCount >= BOOTSTRAP_SYMBOLS || readyCount > 0) {
            _progress.value = DataInitState(
                isReady = true,
                isBackgroundLoading = !foreground,
                importedSymbols = importedCount,
                totalSymbols = zipFiles.size,
                progress = importedCount.toFloat() / zipFiles.size,
                isComplete = false,
            )
        } else {
            _progress.value = DataInitState(
                isLoading = foreground,
                isBackgroundLoading = !foreground,
                importedSymbols = importedCount,
                totalSymbols = zipFiles.size,
                progress = importedCount.toFloat() / zipFiles.size,
            )
        }

        importedThisRun
    }

    private suspend fun importSymbolZip(symbol: String, zipName: String) {
        val cacheDir = File(context.cacheDir, "kline_extract")
        cacheDir.mkdirs()

        try {
            context.assets.open("kline/$zipName").use { input ->
                ZipInputStream(input).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            val period = entry.name.removeSuffix(".csv")
                            if (period in REQUIRED_PERIODS) {
                                val csvFile = File(cacheDir, "${symbol}_${entry.name}")
                                try {
                                    csvFile.outputStream().use { fos -> zis.copyTo(fos) }
                                    val entities = CsvParser.parse(symbol, period, csvFile)
                                    entities.chunked(500).forEach { batch ->
                                        kLineDao.insertAll(batch)
                                    }
                                } finally {
                                    csvFile.delete()
                                }
                            }
                        }
                        entry = zis.nextEntry
                    }
                }
            }
        } finally {
            cacheDir.deleteRecursively()
        }
    }

    private suspend fun isSymbolImportValid(symbol: String, minDayCount: Int): Boolean {
        return kLineDao.hasDayWeekMinute60(symbol, minDayCount) == 1
    }

    private suspend fun trainableCount(minDayCount: Int): Int {
        return kLineDao.getSymbolsWithDayWeekMinute60(minDayCount).size
    }

    private fun bootstrapProgress(readyCount: Int, targetReadyCount: Int): Float {
        val target = targetReadyCount.takeIf { it != Int.MAX_VALUE } ?: BOOTSTRAP_SYMBOLS
        return readyCount.toFloat() / target.coerceAtLeast(1)
    }

    private fun loadZipFiles(): List<String>? {
        return try {
            (context.assets.list("kline") ?: emptyArray())
                .filter { it.endsWith(".zip") }
                .sorted()
        } catch (e: Exception) {
            _progress.value = DataInitState(error = "读取资源失败: ${e.message}")
            null
        }
    }

    private fun orderedZipFiles(zipFiles: List<String>): List<String> {
        val resumeSymbol = prefs().getString(KEY_LAST_IMPORTED_SYMBOL, null) ?: return zipFiles
        val resumeIndex = zipFiles.indexOf("$resumeSymbol.zip")
        if (resumeIndex < 0 || resumeIndex + 1 >= zipFiles.size) return zipFiles
        return zipFiles.drop(resumeIndex + 1) + zipFiles.take(resumeIndex + 1)
    }

    private fun updateBackgroundState(importedCount: Int, total: Int, currentFile: String = "") {
        _progress.value = DataInitState(
            isReady = true,
            isBackgroundLoading = true,
            currentFile = currentFile,
            importedSymbols = importedCount,
            totalSymbols = total,
            progress = importedCount.toFloat() / total,
        )
    }

    private fun markComplete(total: Int) {
        prefs().edit().putInt(KEY_FULL_IMPORT_VERSION, currentVersion()).apply()
        _progress.value = DataInitState(
            isReady = true,
            isComplete = true,
            importedSymbols = total,
            totalSymbols = total,
            progress = 1f,
        )
    }

    private fun saveResumeSymbol(symbol: String) {
        prefs().edit().putString(KEY_LAST_IMPORTED_SYMBOL, symbol).apply()
    }

    private suspend fun resetStaleKLineDataIfNeeded() {
        val prefs = prefs()
        val loadedVersion = prefs.getInt(KEY_DATA_VERSION, 0)
        val currentVersion = currentVersion()
        val hasAnyKLine = kLineDao.getAllSymbols().isNotEmpty()
        if (hasAnyKLine && loadedVersion < currentVersion) {
            kLineDao.deleteAll()
            prefs.edit()
                .remove(KEY_LAST_IMPORTED_SYMBOL)
                .remove(KEY_FULL_IMPORT_VERSION)
                .putInt(KEY_DATA_VERSION, currentVersion)
                .apply()
        } else if (!hasAnyKLine && loadedVersion < currentVersion) {
            prefs.edit().putInt(KEY_DATA_VERSION, currentVersion).apply()
        }
    }

    private fun isCurrentVersionFullyImported(): Boolean {
        return prefs().getInt(KEY_FULL_IMPORT_VERSION, 0) >= currentVersion()
    }

    private fun currentVersion(): Int {
        return try {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionCode
        } catch (_: Exception) {
            1
        }
    }

    private fun prefs() = context.getSharedPreferences("data_init", Context.MODE_PRIVATE)
}

private val REQUIRED_PERIODS = setOf("day", "week", "60min")
private const val BOOTSTRAP_SYMBOLS = 20
private const val BACKGROUND_BATCH_SYMBOLS = 3
private const val TRAINING_BATCH_SYMBOLS = 8
private const val TRAINING_READY_TARGET = 40
private const val MIN_DAY_BARS_FOR_BOOTSTRAP = 300
private const val MIN_DAY_BARS_FOR_FULL_IMPORT = 1
private const val BACKGROUND_BATCH_DELAY_MS = 1_500L
private const val KEY_LAST_IMPORTED_SYMBOL = "last_imported_symbol"
private const val KEY_FULL_IMPORT_VERSION = "full_import_version"
private const val KEY_DATA_VERSION = "data_version"

data class DataInitState(
    val isLoading: Boolean = false,
    val isBackgroundLoading: Boolean = false,
    val currentFile: String = "",
    val progress: Float = 0f,
    val isReady: Boolean = false,
    val isComplete: Boolean = false,
    val importedSymbols: Int = 0,
    val totalSymbols: Int = 0,
    val error: String = "",
)
