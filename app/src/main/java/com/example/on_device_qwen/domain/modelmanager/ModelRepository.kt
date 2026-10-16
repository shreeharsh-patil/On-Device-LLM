package com.example.on_device_qwen.domain.modelmanager

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.ContextCompat
import com.example.on_device_qwen.data.preferences.AppPreferences
import com.example.on_device_qwen.domain.engine.GgufMetadataParser
import com.example.on_device_qwen.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.IOException

class ModelRepository(private val context: Context) {

    private val prefs = AppPreferences(context)

    /**
     * Comprehensive list of directories where AI models may be stored.
     * Includes app-specific internal & external directories, public Downloads,
     * public Documents, secondary SD storage, and temporary directories.
     */
    private val searchDirectories: List<File>
        get() {
            val dirs = mutableListOf<File>()

            // 1. App-specific internal storage (always accessible, no permissions needed)
            dirs.add(context.filesDir)
            dirs.add(File(context.filesDir, "models"))

            // 2. App-specific external storage (always accessible, no permissions needed)
            context.getExternalFilesDir(null)?.let { dirs.add(it) }
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.let { dirs.add(it) }
            context.getExternalFilesDir("models")?.let { dirs.add(it) }

            // 3. All secondary external storage volumes (removable SD cards, USB OTG)
            try {
                context.getExternalFilesDirs(null)?.filterNotNull()?.forEach { dirs.add(it) }
            } catch (_: Exception) {}

            // 4. Cache directories (sometimes used for downloads)
            dirs.add(context.cacheDir)
            context.externalCacheDir?.let { dirs.add(it) }

            // 5. Standard public Download directories
            try {
                dirs.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS))
            } catch (_: Exception) {}
            try {
                dirs.add(File(Environment.getExternalStorageDirectory(), "Download"))
            } catch (_: Exception) {}
            dirs.add(File("/sdcard/Download"))
            dirs.add(File("/storage/emulated/0/Download"))

            // 6. Public Documents directories
            try {
                dirs.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS))
            } catch (_: Exception) {}
            try {
                dirs.add(File(Environment.getExternalStorageDirectory(), "Documents"))
            } catch (_: Exception) {}
            dirs.add(File("/sdcard/Documents"))
            dirs.add(File("/storage/emulated/0/Documents"))

            // 7. Developer / ADB push locations
            dirs.add(File("/data/local/tmp"))

            // 8. Registered model parent directories from preferences
            prefs.registeredModelPaths.forEach { path ->
                try {
                    File(path).parentFile?.let { dirs.add(it) }
                } catch (_: Exception) {}
            }
            prefs.activeModelPath?.let { path ->
                try {
                    File(path).parentFile?.let { dirs.add(it) }
                } catch (_: Exception) {}
            }

            return dirs.distinctBy { it.absolutePath }
        }

    /**
     * Recursively scans for valid AI models across all app-accessible directories,
     * registered paths, and subdirectories up to 3 levels deep.
     */
    suspend fun scanModels(): List<ModelMetadata> = withContext(Dispatchers.IO) {
        val found = mutableListOf<ModelMetadata>()
        val seenCanonicalPaths = mutableSetOf<String>()

        // Step 1: Check registered model paths from persistent preferences
        for (path in (prefs.registeredModelPaths + listOfNotNull(prefs.activeModelPath))) {
            val file = File(path)
            if (file.exists() && file.isFile && file.canRead() && file.length() > 512 * 1024) {
                val canon = try { file.canonicalPath } catch (_: Exception) { file.absolutePath }
                if (seenCanonicalPaths.add(canon)) {
                    parseMetadata(file)?.let { found.add(it) }
                }
            }
        }

        // Step 2: Search known filesystem directories and subdirectories
        for (dir in searchDirectories) {
            if (!dir.exists() || !dir.isDirectory || !dir.canRead()) continue
            scanDirectoryRecursively(
                dir = dir,
                currentDepth = 0,
                maxDepth = 3,
                seenPaths = seenCanonicalPaths
            ) { file ->
                parseMetadata(file)?.let { metadata ->
                    found.add(metadata)
                    prefs.registerModelPath(file.absolutePath)
                }
            }
        }

        // Step 3: Query MediaStore for downloads on Android 10+ (scoped storage bypass)
        queryMediaStoreForModels(seenCanonicalPaths) { file ->
            parseMetadata(file)?.let { metadata ->
                found.add(metadata)
                prefs.registerModelPath(file.absolutePath)
            }
        }

        found.sortedByDescending { it.fileSizeBytes }
    }

    /**
     * Recursively scans directories up to maxDepth.
     * Skips hidden folders and system directories.
     */
    private fun scanDirectoryRecursively(
        dir: File,
        currentDepth: Int,
        maxDepth: Int,
        seenPaths: MutableSet<String>,
        onModelFound: (File) -> Unit
    ) {
        if (currentDepth > maxDepth) return
        val children = dir.listFiles() ?: return

        for (child in children) {
            // Skip hidden files/directories (starting with .)
            if (child.name.startsWith(".")) continue

            if (child.isDirectory) {
                // Skip system and huge non-model directories
                val lowerName = child.name.lowercase()
                if (lowerName in listOf("android", "obb", "lost+found", ".thumbnails", "dcim", "pictures", "movies", "music")) {
                    continue
                }
                scanDirectoryRecursively(child, currentDepth + 1, maxDepth, seenPaths, onModelFound)
            } else if (child.isFile) {
                if (isCandidateModelFile(child)) {
                    val canon = try { child.canonicalPath } catch (_: Exception) { child.absolutePath }
                    if (seenPaths.add(canon)) {
                        onModelFound(child)
                    }
                }
            }
        }
    }

    /**
     * Query MediaStore to find models saved by browsers or download managers.
     */
    private fun queryMediaStoreForModels(
        seenPaths: MutableSet<String>,
        onModelFound: (File) -> Unit
    ) {
        try {
            val contentResolver = context.contentResolver
            val projection = arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.DATA,
                MediaStore.MediaColumns.SIZE
            )
            val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE '%.gguf' OR " +
                    "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE '%.litertlm' OR " +
                    "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE '%.task' OR " +
                    "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE '%.tflite' OR " +
                    "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE '%.bin'"

            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Downloads.EXTERNAL_CONTENT_URI
            } else {
                MediaStore.Files.getContentUri("external")
            }

            contentResolver.query(collection, projection, selection, null, null)?.use { cursor ->
                val dataIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                val sizeIndex = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                while (cursor.moveToNext()) {
                    val path = if (dataIndex != -1) cursor.getString(dataIndex) else null
                    val size = if (sizeIndex != -1) cursor.getLong(sizeIndex) else 0L
                    if (!path.isNullOrBlank() && size > 512 * 1024) {
                        val file = File(path)
                        if (file.exists() && file.isFile && file.canRead()) {
                            val canon = try { file.canonicalPath } catch (_: Exception) { file.absolutePath }
                            if (seenPaths.add(canon)) {
                                onModelFound(file)
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}
    }

    /**
     * Checks if a file is a candidate AI model file via extension or binary magic header.
     */
    fun isCandidateModelFile(file: File): Boolean {
        if (!file.isFile || file.length() < 512 * 1024) return false

        val name = file.name.lowercase()
        // Skip incomplete download files
        if (name.endsWith(".crdownload") || name.endsWith(".part") || name.endsWith(".download") || name.endsWith(".tmp")) {
            return false
        }

        // Known extensions
        if (name.endsWith(".litertlm") || name.endsWith(".litertl") || name.endsWith(".gguf") ||
            name.endsWith(".task") || name.endsWith(".tflite")) {
            return true
        }

        // Binary magic check for .bin or un-extensioned files
        return isGgufBinary(file) || isLiteRtBinary(file)
    }

    /**
     * Checks if file starts with GGUF magic bytes (0x47, 0x47, 0x55, 0x46).
     */
    fun isGgufBinary(file: File): Boolean {
        if (!file.exists() || !file.canRead() || file.length() < 4) return false
        return try {
            FileInputStream(file).use { stream ->
                val magic = ByteArray(4)
                if (stream.read(magic) != 4) false
                else magic[0] == 'G'.code.toByte() &&
                        magic[1] == 'G'.code.toByte() &&
                        magic[2] == 'U'.code.toByte() &&
                        magic[3] == 'F'.code.toByte()
            }
        } catch (_: Exception) { false }
    }

    /**
     * Checks if file has TFLite / LiteRT magic bytes (TFL3 at offset 4).
     */
    fun isLiteRtBinary(file: File): Boolean {
        if (!file.exists() || !file.canRead() || file.length() < 8) return false
        return try {
            FileInputStream(file).use { stream ->
                val buf = ByteArray(8)
                if (stream.read(buf) != 8) false
                else buf[4] == 'T'.code.toByte() &&
                        buf[5] == 'F'.code.toByte() &&
                        buf[6] == 'L'.code.toByte() &&
                        buf[7] == '3'.code.toByte()
            }
        } catch (_: Exception) { false }
    }

    /**
     * Parse and construct ModelMetadata from file.
     */
    fun parseMetadata(file: File): ModelMetadata? {
        if (!file.exists() || !file.canRead() || file.length() < 512 * 1024) return null

        val isGguf = file.extension.equals("gguf", ignoreCase = true) || isGgufBinary(file)
        return if (isGguf) {
            GgufMetadataParser.parse(file) ?: ModelMetadata(
                file = file,
                name = file.nameWithoutExtension,
                format = ModelFormat.GGUF,
                engineType = EngineType.LLAMA_CPP,
                architecture = "qwen2",
                quantization = "GGUF Q4_K_M",
                contextLength = 4096,
                fileSizeBytes = file.length(),
                estimatedRamBytes = (file.length() * 1.3).toLong(),
                isAbliterated = file.name.contains("abliterat", ignoreCase = true) ||
                        file.name.contains("dolphin", ignoreCase = true)
            )
        } else {
            val isVlm = file.name.contains("vl", ignoreCase = true) || file.name.contains("vision", ignoreCase = true)
            ModelMetadata(
                file = file,
                name = file.nameWithoutExtension,
                format = ModelFormat.LITERT,
                engineType = EngineType.LITERT,
                architecture = if (isVlm) "qwen2-vl" else "qwen2.5",
                quantization = "LiteRT INT4/INT8",
                contextLength = 4096,
                fileSizeBytes = file.length(),
                estimatedRamBytes = (file.length() * 1.2).toLong(),
                hasVision = isVlm
            )
        }
    }

    /**
     * Diagnoses why model detection or loading failed, providing specific states
     * (Permission denied, Incomplete download, Unregistered model, Unsupported format, Not found).
     */
    suspend fun diagnoseModelStatus(): ModelDiagnostic = withContext(Dispatchers.IO) {
        val hasAllFilesAccess = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_EXTERNAL_STORAGE) ==
                    PackageManager.PERMISSION_GRANTED
        }

        // Check if there are candidate files in Download folder that are unreadable
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!hasAllFilesAccess && downloadDir.exists()) {
            val files = try { downloadDir.listFiles() } catch (_: Exception) { null }
            if (files == null) {
                return@withContext ModelDiagnostic(
                    errorCode = ModelErrorCode.FILE_UNREADABLE_OR_PERMISSION_DENIED,
                    title = "Storage Permission Required",
                    message = "Your downloaded model is located in the device Download directory, but Android requires 'All files access' permission to read it.\n\nGrant storage access or tap 'Select with File Picker' to load it directly.",
                    canRequestPermission = true,
                    canOpenFilePicker = true
                )
            }
        }

        // Check for incomplete downloads (.crdownload, .part, .download)
        for (dir in listOfNotNull(downloadDir, context.filesDir, context.getExternalFilesDir(null))) {
            val partials = try {
                dir.listFiles { _, name ->
                    val lower = name.lowercase()
                    (lower.endsWith(".crdownload") || lower.endsWith(".part") || lower.endsWith(".download")) &&
                            (lower.contains("qwen") || lower.contains("dolphin") || lower.contains("gguf") || lower.contains("litert"))
                }
            } catch (_: Exception) { null }

            if (!partials.isNullOrEmpty()) {
                val partialFile = partials.first()
                val partialMb = partialFile.length() / (1024 * 1024)
                return@withContext ModelDiagnostic(
                    errorCode = ModelErrorCode.MODEL_FILE_NOT_FOUND,
                    title = "Incomplete Download Detected",
                    message = "Found in-progress download '${partialFile.name}' ($partialMb MB).\n\nPlease wait for the download to complete in your browser or download manager before loading.",
                    canOpenFilePicker = true
                )
            }
        }

        // Check for downloaded-but-unregistered models
        val scanned = scanModels()
        if (scanned.isNotEmpty()) {
            return@withContext ModelDiagnostic(
                errorCode = ModelErrorCode.DOWNLOADED_MODEL_NOT_REGISTERED,
                title = "Downloaded Model Not Registered",
                message = "Found ${scanned.size} model(s) on your device that are ready to load.",
                candidateFiles = scanned.map { it.file }
            )
        }

        // Truly not found
        ModelDiagnostic(
            errorCode = ModelErrorCode.MODEL_FILE_NOT_FOUND,
            title = "No Compatible Model File Found",
            message = "No valid .litertlm or .gguf model found in app storage, Downloads, or Documents.\n\nDownload a model from the Model Catalog or import an existing model from your device.",
            canRequestPermission = !hasAllFilesAccess,
            canOpenFilePicker = true
        )
    }

    /**
     * Imports a model from a SAF content URI or file URI.
     * Prevents duplicate file copies if the target already exists with identical size.
     */
    suspend fun importModelFromUri(
        uri: Uri,
        onProgress: (Float) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            // Handle file:// URIs directly
            if (uri.scheme == "file") {
                val directPath = uri.path
                if (!directPath.isNullOrBlank()) {
                    val directFile = File(directPath)
                    if (directFile.exists() && directFile.canRead() && directFile.length() > 512 * 1024) {
                        prefs.registerModelPath(directFile.absolutePath)
                        return@withContext Result.success(directFile)
                    }
                }
            }

            val contentResolver = context.contentResolver
            var fileName = "imported_model_${System.currentTimeMillis()}.litertlm"
            var fileSize = -1L

            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) {
                        cursor.getString(nameIndex)?.let { fileName = it }
                    }
                    if (sizeIndex != -1) {
                        fileSize = cursor.getLong(sizeIndex)
                    }
                }
            }

            if (fileSize <= 0) {
                fileSize = contentResolver.openFileDescriptor(uri, "r")?.statSize ?: -1L
            }

            // Choose target storage directory with sufficient free space
            val targetDir = if (context.filesDir.usableSpace > fileSize * 1.3) {
                context.filesDir
            } else {
                context.getExternalFilesDir(null) ?: context.filesDir
            }

            val targetFile = File(targetDir, fileName)

            // Check if identical file already exists to avoid duplicate multi-gigabyte copying
            if (targetFile.exists() && fileSize > 0 && targetFile.length() == fileSize) {
                prefs.registerModelPath(targetFile.absolutePath)
                onProgress(1.0f)
                return@withContext Result.success(targetFile)
            }

            contentResolver.openInputStream(uri)?.use { input ->
                targetFile.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var bytesCopied = 0L
                    var read: Int
                    while (input.read(buffer).also { read = it } >= 0) {
                        output.write(buffer, 0, read)
                        bytesCopied += read
                        if (fileSize > 0) {
                            onProgress((bytesCopied.toFloat() / fileSize).coerceIn(0f, 1f))
                        }
                    }
                }
            }

            if (fileSize > 0 && targetFile.length() != fileSize) {
                targetFile.delete()
                throw IOException("File copy failed: expected $fileSize bytes, got ${targetFile.length()} bytes")
            }

            prefs.registerModelPath(targetFile.absolutePath)
            Result.success(targetFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteModel(file: File): Boolean = withContext(Dispatchers.IO) {
        try {
            prefs.unregisterModelPath(file.absolutePath)
            if (file.exists() && (file.parentFile?.absolutePath == context.filesDir.absolutePath ||
                        file.parentFile?.absolutePath == context.getExternalFilesDir(null)?.absolutePath)) {
                file.delete()
            } else {
                true
            }
        } catch (_: Exception) {
            false
        }
    }
}
