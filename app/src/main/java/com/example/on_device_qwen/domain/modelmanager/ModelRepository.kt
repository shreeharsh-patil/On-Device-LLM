package com.example.on_device_qwen.domain.modelmanager

import android.content.Context
import android.net.Uri
import android.os.StatFs
import android.provider.OpenableColumns
import com.example.on_device_qwen.data.preferences.AppPreferences
import com.example.on_device_qwen.domain.engine.GgufMetadataParser
import com.example.on_device_qwen.model.EngineType
import com.example.on_device_qwen.model.ModelCompatibilityStatus
import com.example.on_device_qwen.model.ModelDiagnostic
import com.example.on_device_qwen.model.ModelErrorCode
import com.example.on_device_qwen.model.ModelFormat
import com.example.on_device_qwen.model.ModelMetadata
import com.example.on_device_qwen.model.Modality
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.SyncFailedException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.Collections
import java.util.Locale
import kotlin.coroutines.coroutineContext

enum class ModelImportErrorReason(val defaultHint: String) {
    INSUFFICIENT_STORAGE("Free up storage space on your device and try importing again."),
    INVALID_HEADER("The selected file is corrupted or does not match a valid model format header."),
    INCOMPLETE_TRANSFER("The file transfer stopped before all bytes were copied. Please retry importing."),
    UNSUPPORTED_FORMAT("Select a supported .gguf, .litertlm, .task, or .tflite model file."),
    PERMISSION_DENIED("Permission to read the selected file was denied or expired. Re-select it in the file picker."),
    IO_ERROR("An unexpected I/O error occurred while copying the model file."),
}

class ModelImportException(
    val reason: ModelImportErrorReason,
    message: String,
    val recoveryHint: String = reason.defaultHint,
    cause: Throwable? = null,
) : IOException(message, cause)

class ModelRepository(private val context: Context) {

    private val prefs = AppPreferences(context)
    private val activeStagingPaths: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())

    /**
     * App-owned model directories (scoped-storage compliant; no external storage permissions required).
     * Order prioritizes dedicated model subdirectories, then legacy root app files dirs.
     */
    private val appManagedSubdirs: List<File>
        get() = listOfNotNull(
            File(context.filesDir, "models"),
            context.getExternalFilesDir("models"),
        ).distinctBy { canonicalOrAbsolute(it) }

    private val appLegacyRootDirs: List<File>
        get() = listOfNotNull(
            context.filesDir,
            context.getExternalFilesDir(null),
        ).distinctBy { canonicalOrAbsolute(it) }

    private val appOwnedRoots: List<File>
        get() = listOfNotNull(
            context.filesDir,
            context.getExternalFilesDir(null),
        ).distinctBy { canonicalOrAbsolute(it) }

    /**
     * Cleans up orphaned staging `.tmp` files and scans strictly app-owned model directories
     * plus registered SAF/internal paths, pruning stale/deleted registered paths along the way.
     */
    suspend fun scanModels(): List<ModelMetadata> = withContext(Dispatchers.IO) {
        cleanupOrphanedTempFiles()

        val found = mutableListOf<ModelMetadata>()
        val seenCanonicalPaths = mutableSetOf<String>()
        val staleRegisteredPaths = mutableSetOf<String>()

        // 1. Validate registered model paths and prune missing/unreadable entries.
        for (registeredPath in prefs.registeredModelPaths) {
            val file = File(registeredPath)
            if (isValidRegisteredCandidate(file)) {
                val canon = canonicalOrAbsolute(file)
                if (seenCanonicalPaths.add(canon)) {
                    parseMetadata(file)?.let { found.add(it) }
                }
            } else {
                staleRegisteredPaths.add(registeredPath)
            }
        }
        prefs.pruneRegisteredModelPaths(staleRegisteredPaths)

        // Clear stale activeModelPath if the backing file no longer exists.
        prefs.activeModelPath?.let { activePath ->
            val activeFile = File(activePath)
            if (!isValidRegisteredCandidate(activeFile)) {
                prefs.activeModelPath = null
            } else {
                val canon = canonicalOrAbsolute(activeFile)
                if (seenCanonicalPaths.add(canon)) {
                    parseMetadata(activeFile)?.let {
                        found.add(it)
                        prefs.registerModelPath(activeFile.absolutePath)
                    }
                }
            }
        }

        // 2. Scan dedicated app-private `models/` directories (up to depth 2).
        for (dir in appManagedSubdirs) {
            if (!dir.exists() || !dir.isDirectory || !dir.canRead()) continue
            scanDirectory(
                dir = dir,
                currentDepth = 0,
                maxDepth = 2,
                seenPaths = seenCanonicalPaths,
            ) { file ->
                parseMetadata(file)?.let { metadata ->
                    found.add(metadata)
                    prefs.registerModelPath(file.absolutePath)
                }
            }
        }

        // 3. Scan direct model files placed in legacy app roots (`filesDir`, `getExternalFilesDir(null)`).
        for (rootDir in appLegacyRootDirs) {
            if (!rootDir.exists() || !rootDir.isDirectory || !rootDir.canRead()) continue
            scanDirectory(
                dir = rootDir,
                currentDepth = 0,
                maxDepth = 0,
                seenPaths = seenCanonicalPaths,
            ) { file ->
                parseMetadata(file)?.let { metadata ->
                    found.add(metadata)
                    prefs.registerModelPath(file.absolutePath)
                }
            }
        }

        found.sortedByDescending { it.fileSizeBytes }
    }

    private fun scanDirectory(
        dir: File,
        currentDepth: Int,
        maxDepth: Int,
        seenPaths: MutableSet<String>,
        onModelFound: (File) -> Unit,
    ) {
        val children = dir.listFiles() ?: return
        for (child in children) {
            if (child.name.startsWith(".")) continue
            if (child.isDirectory) {
                if (currentDepth < maxDepth) {
                    val lower = child.name.lowercase(Locale.ROOT)
                    if (lower != "models" || currentDepth > 0) {
                        scanDirectory(child, currentDepth + 1, maxDepth, seenPaths, onModelFound)
                    }
                }
            } else if (child.isFile && isCandidateModelFile(child)) {
                val canon = canonicalOrAbsolute(child)
                if (seenPaths.add(canon)) {
                    onModelFound(child)
                }
            }
        }
    }

    /**
     * Deletes orphaned `.tmp` staging files from interrupted imports in app-private storage,
     * leaving any actively running staging transfers untouched.
     */
    fun cleanupOrphanedTempFiles() {
        val dirs = (appManagedSubdirs + appLegacyRootDirs).distinctBy { canonicalOrAbsolute(it) }
        for (dir in dirs) {
            if (!dir.exists() || !dir.isDirectory) continue
            val files = dir.listFiles() ?: continue
            for (child in files) {
                if (!child.isFile) continue
                val lower = child.name.lowercase(Locale.ROOT)
                if (lower.endsWith(".tmp")) {
                    val canon = canonicalOrAbsolute(child)
                    if (canon !in activeStagingPaths) {
                        child.delete()
                    }
                }
            }
        }
    }

    private fun isValidRegisteredCandidate(file: File): Boolean {
        return file.exists() && file.isFile && file.canRead() && isCandidateModelFile(file)
    }

    /**
     * Checks if a file is a candidate AI model file via extension or binary magic header,
     * ignoring incomplete partial/temp downloads.
     */
    fun isCandidateModelFile(file: File): Boolean {
        if (!file.isFile || file.length() < MIN_MODEL_BYTES) return false

        val name = file.name.lowercase(Locale.ROOT)
        if (name.endsWith(".crdownload") ||
            name.endsWith(".part") ||
            name.endsWith(".download") ||
            name.endsWith(".tmp")
        ) {
            return false
        }

        if (name.endsWith(".litertlm") ||
            name.endsWith(".gguf") ||
            name.endsWith(".task") ||
            name.endsWith(".tflite")
        ) {
            return true
        }

        return isGgufBinary(file) || isLiteRtBinary(file)
    }

    /**
     * Checks if file starts with GGUF magic bytes (`GGUF` / 0x47, 0x47, 0x55, 0x46).
     */
    fun isGgufBinary(file: File): Boolean {
        if (!file.exists() || !file.canRead() || file.length() < 4) return false
        return try {
            FileInputStream(file).use { stream ->
                val magic = ByteArray(4)
                if (readExact(stream, magic) < 4) {
                    false
                } else {
                    magic[0] == 'G'.code.toByte() &&
                        magic[1] == 'G'.code.toByte() &&
                        magic[2] == 'U'.code.toByte() &&
                        magic[3] == 'F'.code.toByte()
                }
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Checks if file starts with LiteRT-LM (`LITERTLM`), TFLite FlatBuffer (`TFL3` at offset 4),
     * or ZIP archive (`PK\x03\x04`) header.
     */
    fun isLiteRtBinary(file: File): Boolean {
        if (!file.exists() || !file.canRead() || file.length() < 8) return false
        return try {
            FileInputStream(file).use { stream ->
                val buf = ByteArray(8)
                if (readExact(stream, buf) < 8) {
                    false
                } else {
                    hasLiteRtLmMagic(buf) || hasTfl3Magic(buf) || hasZipMagic(buf)
                }
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Parse and construct [ModelMetadata] from an on-disk model file.
     * Flags corrupted/truncated files with `isCorrupted = true` instead of fabricating valid metadata.
     */
    fun parseMetadata(file: File): ModelMetadata? {
        if (!file.exists() || !file.canRead()) return null

        val ext = file.extension.lowercase(Locale.ROOT)
        val isGguf = ext == "gguf" || isGgufBinary(file)
        if (isGguf) {
            val parsed = GgufMetadataParser.parse(file)
            if (parsed != null) {
                return parsed
            }
            return ModelMetadata(
                file = file,
                name = file.nameWithoutExtension,
                format = ModelFormat.GGUF,
                engineType = EngineType.LLAMA_CPP,
                architecture = "Unknown",
                quantization = "Invalid",
                contextLength = 4096,
                fileSizeBytes = file.length(),
                estimatedRamBytes = 0L,
                isReadable = file.canRead(),
                isCorrupted = true,
                compatibilityStatus = ModelCompatibilityStatus.CORRUPTED,
                compatibilityMessage = "Corrupted or invalid GGUF header.",
            )
        }

        val format = ModelFormat.LITERT
        val validLiteRt = isValidLiteRtHeaderForExtension(file, ext)
        if (!validLiteRt) {
            return ModelMetadata(
                file = file,
                name = file.nameWithoutExtension,
                format = format,
                engineType = EngineType.LITERT,
                architecture = "Unknown",
                quantization = "Invalid",
                contextLength = 4096,
                fileSizeBytes = file.length(),
                estimatedRamBytes = 0L,
                isReadable = file.canRead(),
                isCorrupted = true,
                compatibilityStatus = ModelCompatibilityStatus.CORRUPTED,
                compatibilityMessage = "Corrupted or invalid ${format.name} header.",
            )
        }

        return ModelMetadata(
            file = file,
            name = file.nameWithoutExtension,
            format = format,
            engineType = EngineType.LITERT,
            architecture = "litert",
            quantization = "LiteRT INT4/INT8",
            contextLength = 4096,
            fileSizeBytes = file.length(),
            estimatedRamBytes = (file.length() * 1.2).toLong(),
            hasVision = false,
            supportedModalities = setOf(Modality.TEXT),
            isReadable = file.canRead(),
            isCorrupted = false,
        )
    }

    /**
     * Computes SHA-256 hex digest of [file] using a bounded streaming buffer.
     */
    suspend fun computeSha256(file: File): String? = withContext(Dispatchers.IO) {
        if (!file.exists() || !file.isFile || !file.canRead()) return@withContext null
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(COPY_BUFFER_BYTES)
            FileInputStream(file).use { input ->
                while (true) {
                    coroutineContext.ensureActive()
                    val read = input.read(buffer)
                    if (read <= 0) break
                    digest.update(buffer, 0, read)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Evaluates RAM compatibility using clamped mobile context bounds instead of un-clamped
     * 128K GGUF metadata context lengths.
     */
    fun evaluateDeviceCompatibility(
        metadata: ModelMetadata,
        availRamMb: Long,
        totalRamMb: Long,
    ): ModelMetadata {
        if (metadata.isCorrupted || metadata.compatibilityStatus == ModelCompatibilityStatus.CORRUPTED) {
            return metadata.copy(
                isCorrupted = true,
                compatibilityStatus = ModelCompatibilityStatus.CORRUPTED,
                compatibilityMessage = metadata.compatibilityMessage ?: "Corrupted or invalid model file.",
            )
        }

        val fileMb = (metadata.fileSizeBytes / BYTES_PER_MB).coerceAtLeast(1L)
        val effectiveContext = metadata.contextLength.coerceIn(1024, 8192)
        val sizeFactor = (fileMb / 1024.0).coerceIn(0.5, 4.0)
        val clampedKvMb = ((effectiveContext / 1024.0) * 48.0 * sizeFactor)
            .toLong()
            .coerceIn(48L, 768L)
        val runtimeOverheadMb = if (metadata.engineType == EngineType.LLAMA_CPP) 256L else 192L
        val minRamMb = fileMb + (runtimeOverheadMb / 2L) + 64L
        val recommendedRamMb = fileMb + clampedKvMb + runtimeOverheadMb

        val safeAvailMb = availRamMb.coerceAtLeast(0L)
        val safeTotalMb = totalRamMb.coerceAtLeast(0L)

        val (status, message) = when {
            safeTotalMb > 0L && fileMb > (safeTotalMb * 0.85).toLong() -> {
                ModelCompatibilityStatus.INSUFFICIENT_RAM to
                    "Model size (${fileMb} MB) exceeds safe share of device RAM (${safeTotalMb} MB)."
            }
            safeAvailMb in 1L until minRamMb -> {
                ModelCompatibilityStatus.INSUFFICIENT_RAM to
                    "Insufficient free RAM (${safeAvailMb} MB free, at least ${minRamMb} MB required)."
            }
            safeAvailMb in minRamMb until recommendedRamMb -> {
                ModelCompatibilityStatus.TIGHT_RAM to
                    "Tight memory (${safeAvailMb} MB free, ${recommendedRamMb} MB recommended). Close other apps if loading fails."
            }
            safeTotalMb in 1L until recommendedRamMb -> {
                ModelCompatibilityStatus.TIGHT_RAM to
                    "Tight device RAM (${safeTotalMb} MB total, ${recommendedRamMb} MB recommended)."
            }
            else -> {
                ModelCompatibilityStatus.COMPATIBLE to
                    "Compatible (~${recommendedRamMb} MB RAM recommended for ${effectiveContext} context)."
            }
        }

        return metadata.copy(
            estimatedRamBytes = recommendedRamMb * BYTES_PER_MB,
            recommendedRamMb = recommendedRamMb,
            minRamMb = minRamMb,
            estimatedKvRamMb = clampedKvMb,
            compatibilityStatus = status,
            compatibilityMessage = message,
        )
    }

    /**
     * Scoped-storage model status diagnostic (no legacy storage or MANAGE_EXTERNAL_STORAGE checks).
     */
    @Deprecated("Use scanModels() and importModelWithState() instead")
    suspend fun diagnoseModelStatus(): ModelDiagnostic = withContext(Dispatchers.IO) {
        val scanned = scanModels().filterNot { it.isCorrupted }
        if (scanned.isNotEmpty()) {
            return@withContext ModelDiagnostic(
                errorCode = ModelErrorCode.DOWNLOADED_MODEL_NOT_REGISTERED,
                title = "Model Ready in App Storage",
                message = "Found ${scanned.size} compatible model(s) in app storage.",
                canRequestPermission = false,
                canOpenFilePicker = true,
                candidateFiles = scanned.map { it.file },
            )
        }

        ModelDiagnostic(
            errorCode = ModelErrorCode.MODEL_FILE_NOT_FOUND,
            title = "No Model Imported Yet",
            message = "Import a compatible .gguf, .litertlm, .task, or .tflite model from your device using the system file picker.",
            canRequestPermission = false,
            canOpenFilePicker = true,
        )
    }

    /**
     * Imports a model from a SAF content URI or file URI with staged temp copy,
     * pre-copy disk space validation, strict magic-byte verification, atomic rename,
     * and guaranteed staging cleanup on failure or cancellation.
     */
    suspend fun importModelWithState(
        uri: Uri,
        onState: (ModelImportState) -> Unit,
    ): Result<ModelMetadata> = withContext(Dispatchers.IO) {
        var resolvedName = ""
        var stagingFile: File? = null
        var finalized = false

        try {
            // Fast path when file:// URI already points inside app-owned storage.
            if (uri.scheme.equals("file", ignoreCase = true)) {
                val directPath = uri.path
                if (!directPath.isNullOrBlank()) {
                    val directFile = File(directPath)
                    if (isInsideAppOwnedStorage(directFile) && directFile.exists() && directFile.isFile) {
                        val directName = sanitizeDisplayName(directFile.name)
                        val directExt = directName.substringAfterLast('.', "").lowercase(Locale.ROOT)
                        validateExtension(directExt, directName)
                        onState(
                            ModelImportState(
                                progress = ImportProgress.VERIFYING,
                                fileName = directName,
                                bytesCopied = directFile.length(),
                                totalBytes = directFile.length(),
                            )
                        )
                        validateImportedModelFile(directFile, directExt)
                        prefs.registerModelPath(directFile.absolutePath)
                        val meta = parseMetadata(directFile)
                            ?: throw ModelImportException(
                                ModelImportErrorReason.INVALID_HEADER,
                                "Unable to parse metadata from ${directFile.name}.",
                            )
                        onState(
                            ModelImportState(
                                progress = ImportProgress.READY,
                                fileName = directFile.name,
                                bytesCopied = directFile.length(),
                                totalBytes = directFile.length(),
                            )
                        )
                        return@withContext Result.success(meta)
                    }
                }
            }

            val metadataTuple = resolveUriMetadata(uri)
            val sanitizedName = sanitizeDisplayName(metadataTuple.displayName)
            resolvedName = sanitizedName
            val ext = sanitizedName.substringAfterLast('.', "").lowercase(Locale.ROOT)
            validateExtension(ext, sanitizedName)

            val expectedSize = metadataTuple.sizeBytes
            val targetDir = selectTargetDirectory(expectedSize)

            // Skip copy only if an identical verified file already exists in targetDir.
            val candidateExisting = File(targetDir, sanitizedName)
            if (expectedSize > 0L &&
                candidateExisting.exists() &&
                candidateExisting.isFile &&
                candidateExisting.length() == expectedSize &&
                isValidImportedHeaderQuiet(candidateExisting, ext)
            ) {
                prefs.registerModelPath(candidateExisting.absolutePath)
                val existingMeta = parseMetadata(candidateExisting)
                if (existingMeta != null && !existingMeta.isCorrupted) {
                    onState(
                        ModelImportState(
                            progress = ImportProgress.READY,
                            fileName = candidateExisting.name,
                            bytesCopied = expectedSize,
                            totalBytes = expectedSize,
                        )
                    )
                    return@withContext Result.success(existingMeta)
                }
            }

            val stage = File(targetDir, ".$sanitizedName.tmp")
            stagingFile = stage
            activeStagingPaths.add(canonicalOrAbsolute(stage))
            if (stage.exists()) {
                stage.delete()
            }

            onState(
                ModelImportState(
                    progress = ImportProgress.COPYING,
                    fileName = sanitizedName,
                    bytesCopied = 0L,
                    totalBytes = expectedSize,
                )
            )

            val bytesCopied = copyUriToStaging(uri, stage, sanitizedName, expectedSize, onState)
            coroutineContext.ensureActive()

            onState(
                ModelImportState(
                    progress = ImportProgress.VERIFYING,
                    fileName = sanitizedName,
                    bytesCopied = bytesCopied,
                    totalBytes = expectedSize,
                )
            )

            if (bytesCopied <= 0L) {
                throw ModelImportException(
                    ModelImportErrorReason.INCOMPLETE_TRANSFER,
                    "Imported file '$sanitizedName' is empty (0 bytes).",
                )
            }
            if (expectedSize > 0L && bytesCopied != expectedSize) {
                throw ModelImportException(
                    ModelImportErrorReason.INCOMPLETE_TRANSFER,
                    "Incomplete transfer for '$sanitizedName': expected $expectedSize bytes, copied $bytesCopied bytes.",
                )
            }

            validateImportedModelFile(stage, ext)
            coroutineContext.ensureActive()

            onState(
                ModelImportState(
                    progress = ImportProgress.FINALIZING,
                    fileName = sanitizedName,
                    bytesCopied = bytesCopied,
                    totalBytes = bytesCopied,
                )
            )

            val destination = resolveConflictFreeDestination(targetDir, sanitizedName)
            if (!stage.renameTo(destination)) {
                throw ModelImportException(
                    ModelImportErrorReason.IO_ERROR,
                    "Failed to finalize imported file '$sanitizedName' into app storage.",
                )
            }
            finalized = true

            val parsedMeta = parseMetadata(destination)
                ?: throw ModelImportException(
                    ModelImportErrorReason.INVALID_HEADER,
                    "Imported model '${destination.name}' could not be parsed.",
                )
            if (parsedMeta.isCorrupted) {
                destination.delete()
                throw ModelImportException(
                    ModelImportErrorReason.INVALID_HEADER,
                    parsedMeta.compatibilityMessage ?: "Imported model '${destination.name}' failed validation.",
                )
            }

            prefs.registerModelPath(destination.absolutePath)
            onState(
                ModelImportState(
                    progress = ImportProgress.READY,
                    fileName = destination.name,
                    bytesCopied = bytesCopied,
                    totalBytes = bytesCopied,
                )
            )
            Result.success(parsedMeta)
        } catch (ce: CancellationException) {
            if (!finalized) {
                stagingFile?.delete()
            }
            throw ce
        } catch (mie: ModelImportException) {
            val detail = "${mie.message} ${mie.recoveryHint}".trim()
            onState(
                ModelImportState(
                    progress = ImportProgress.FAILED,
                    fileName = resolvedName,
                    errorMessage = detail,
                )
            )
            Result.failure(mie)
        } catch (se: SecurityException) {
            val mie = ModelImportException(
                reason = ModelImportErrorReason.PERMISSION_DENIED,
                message = "Read permission denied for selected URI.",
                cause = se,
            )
            onState(
                ModelImportState(
                    progress = ImportProgress.FAILED,
                    fileName = resolvedName,
                    errorMessage = "${mie.message} ${mie.recoveryHint}".trim(),
                )
            )
            Result.failure(mie)
        } catch (ioe: IOException) {
            val isEnospc = ioe.message?.contains("ENOSPC", ignoreCase = true) == true ||
                ioe.message?.contains("No space", ignoreCase = true) == true
            val reason = if (isEnospc) ModelImportErrorReason.INSUFFICIENT_STORAGE else ModelImportErrorReason.IO_ERROR
            val mie = ModelImportException(
                reason = reason,
                message = ioe.message ?: "Failed to copy model file.",
                cause = ioe,
            )
            onState(
                ModelImportState(
                    progress = ImportProgress.FAILED,
                    fileName = resolvedName,
                    errorMessage = "${mie.message} ${mie.recoveryHint}".trim(),
                )
            )
            Result.failure(mie)
        } catch (e: Exception) {
            val mie = ModelImportException(
                reason = ModelImportErrorReason.IO_ERROR,
                message = e.message ?: "Unexpected error while importing model.",
                cause = e,
            )
            onState(
                ModelImportState(
                    progress = ImportProgress.FAILED,
                    fileName = resolvedName,
                    errorMessage = "${mie.message} ${mie.recoveryHint}".trim(),
                )
            )
            Result.failure(mie)
        } finally {
            stagingFile?.let { file ->
                activeStagingPaths.remove(canonicalOrAbsolute(file))
                if (!finalized && file.exists()) {
                    file.delete()
                }
            }
        }
    }

    /**
     * Backwards-compatible wrapper around [importModelWithState] for existing UI callers.
     */
    suspend fun importModelFromUri(
        uri: Uri,
        onProgress: (Float) -> Unit,
    ): Result<File> {
        return importModelWithState(uri) { state ->
            when (state.progress) {
                ImportProgress.COPYING -> state.fraction?.let(onProgress)
                ImportProgress.VERIFYING, ImportProgress.FINALIZING -> onProgress(0.99f)
                ImportProgress.READY -> onProgress(1.0f)
                ImportProgress.IDLE, ImportProgress.FAILED -> Unit
            }
        }.map { it.file }
    }

    /**
     * Deletes a model file from ANY app-owned storage directory (`filesDir`, `filesDir/models`,
     * `getExternalFilesDir(null)`, `getExternalFilesDir("models")`), unregisters it,
     * and clears `prefs.activeModelPath` when matching by canonical path.
     */
    suspend fun deleteModel(file: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val targetCanon = canonicalOrAbsolute(file)
            val isOwned = isInsideAppOwnedStorage(file)

            val deleted = if (!file.exists()) {
                true
            } else if (isOwned) {
                file.delete()
            } else {
                false
            }

            if (deleted || !file.exists()) {
                val matchingPaths = prefs.registeredModelPaths.filterTo(mutableSetOf()) { regPath ->
                    canonicalOrAbsolute(File(regPath)) == targetCanon
                }
                prefs.pruneRegisteredModelPaths(matchingPaths)
                prefs.activeModelPath?.let { activePath ->
                    if (canonicalOrAbsolute(File(activePath)) == targetCanon) {
                        prefs.activeModelPath = null
                    }
                }
            }
            deleted
        } catch (_: Exception) {
            false
        }
    }

    private data class UriFileMetadata(val displayName: String, val sizeBytes: Long)

    private fun resolveUriMetadata(uri: Uri): UriFileMetadata {
        if (uri.scheme.equals("file", ignoreCase = true)) {
            val path = uri.path.orEmpty()
            val file = File(path)
            return UriFileMetadata(
                displayName = file.name.ifBlank { "imported_model" },
                sizeBytes = if (file.exists() && file.isFile) file.length() else -1L,
            )
        }

        val resolver = context.contentResolver
        var rawName = uri.lastPathSegment
            ?.substringAfterLast('/')
            ?.substringAfterLast('\\')
            ?.substringAfterLast(':')
            ?: ""
        var fileSize = -1L

        try {
            resolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0 && !cursor.isNull(nameIdx)) {
                        rawName = cursor.getString(nameIdx) ?: rawName
                    }
                    if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                        fileSize = cursor.getLong(sizeIdx)
                    }
                }
            }
        } catch (se: SecurityException) {
            throw ModelImportException(
                reason = ModelImportErrorReason.PERMISSION_DENIED,
                message = "Permission denied querying metadata for selected URI.",
                cause = se,
            )
        }

        if (fileSize <= 0L) {
            try {
                resolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    if (pfd.statSize > 0L) {
                        fileSize = pfd.statSize
                    }
                }
            } catch (se: SecurityException) {
                throw ModelImportException(
                    reason = ModelImportErrorReason.PERMISSION_DENIED,
                    message = "Permission denied opening file descriptor for selected URI.",
                    cause = se,
                )
            } catch (_: Exception) {
                // Provider may not expose a seekable FD (e.g., virtual stream); copy loop checks actual bytes.
            }
        }

        return UriFileMetadata(displayName = rawName, sizeBytes = fileSize)
    }

    private fun sanitizeDisplayName(rawName: String): String {
        val leaf = rawName.trim()
            .substringAfterLast('/')
            .substringAfterLast('\\')
        val cleaned = leaf
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .trimStart('.')
            .trim()
        return cleaned.ifBlank { "imported_model" }
    }

    private fun validateExtension(ext: String, displayName: String) {
        if (ext !in SUPPORTED_IMPORT_EXTENSIONS) {
            throw ModelImportException(
                reason = ModelImportErrorReason.UNSUPPORTED_FORMAT,
                message = "Unsupported model extension '$displayName'. Expected .gguf, .litertlm, .task, or .tflite.",
            )
        }
        val baseName = displayName.substringBeforeLast('.', "")
        if (baseName.isBlank()) {
            throw ModelImportException(
                reason = ModelImportErrorReason.UNSUPPORTED_FORMAT,
                message = "Invalid empty model filename '$displayName'.",
            )
        }
    }

    private fun selectTargetDirectory(expectedSizeBytes: Long): File {
        val requiredBytes = if (expectedSizeBytes > 0L) {
            val headroom = (expectedSizeBytes / 10L)
                .coerceAtLeast(MIN_KNOWN_SIZE_BUFFER_BYTES)
                .coerceAtMost(MAX_IMPORT_BUFFER_BYTES)
            expectedSizeBytes + headroom
        } else {
            MIN_UNKNOWN_SIZE_FREE_BYTES
        }

        val candidates = listOfNotNull(
            File(context.filesDir, "models"),
            context.getExternalFilesDir("models"),
        ).distinctBy { canonicalOrAbsolute(it) }

        var maxAvail = -1L
        for (dir in candidates) {
            if (!dir.exists() && !dir.mkdirs()) continue
            if (!dir.isDirectory) continue
            val avail = queryAvailableBytes(dir)
            if (avail > maxAvail) maxAvail = avail
            if (avail >= requiredBytes) {
                return dir
            }
        }

        val neededMb = requiredBytes.toDouble() / BYTES_PER_MB
        val availMb = maxAvail.coerceAtLeast(0L).toDouble() / BYTES_PER_MB
        throw ModelImportException(
            reason = ModelImportErrorReason.INSUFFICIENT_STORAGE,
            message = String.format(
                Locale.US,
                "Insufficient storage space: need %.1f MB (with safety buffer), only %.1f MB available.",
                neededMb,
                availMb,
            ),
        )
    }

    private fun queryAvailableBytes(dir: File): Long {
        return try {
            val statFs = StatFs(dir.path)
            if (statFs.blockCountLong > 0L) {
                statFs.availableBytes
            } else {
                // Fallback when Robolectric ShadowStatFs has not been explicitly configured for the path.
                dir.usableSpace
            }
        } catch (_: Exception) {
            dir.usableSpace
        }
    }

    private suspend fun copyUriToStaging(
        uri: Uri,
        stagingFile: File,
        displayName: String,
        expectedSize: Long,
        onState: (ModelImportState) -> Unit,
    ): Long {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw ModelImportException(
                reason = ModelImportErrorReason.PERMISSION_DENIED,
                message = "Unable to open input stream for '$displayName'.",
            )

        var bytesCopied = 0L
        var lastReportedBytes = -1L
        inputStream.use { input ->
            FileOutputStream(stagingFile).use { output ->
                val buffer = ByteArray(COPY_BUFFER_BYTES)
                while (true) {
                    coroutineContext.ensureActive()
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (read == 0) continue
                    output.write(buffer, 0, read)
                    bytesCopied += read
                    if (bytesCopied - lastReportedBytes >= PROGRESS_EMIT_INTERVAL_BYTES ||
                        (expectedSize > 0L && bytesCopied >= expectedSize)
                    ) {
                        lastReportedBytes = bytesCopied
                        onState(
                            ModelImportState(
                                progress = ImportProgress.COPYING,
                                fileName = displayName,
                                bytesCopied = bytesCopied,
                                totalBytes = expectedSize,
                            )
                        )
                    }
                }
                output.flush()
                try {
                    output.fd.sync()
                } catch (_: SyncFailedException) {
                    // Best-effort fsync; some virtual/emulated filesystems do not support sync().
                }
            }
        }
        return bytesCopied
    }

    private fun isValidImportedHeaderQuiet(file: File, ext: String): Boolean {
        return try {
            validateImportedModelFile(file, ext)
            true
        } catch (_: ModelImportException) {
            false
        }
    }

    private fun validateImportedModelFile(file: File, ext: String) {
        when (ext) {
            "gguf" -> {
                if (file.length() < MIN_GGUF_BYTES) {
                    throw ModelImportException(
                        ModelImportErrorReason.INVALID_HEADER,
                        "File '${file.name}' is too small to be a valid GGUF model.",
                    )
                }
                val header = ByteArray(24)
                FileInputStream(file).use { stream ->
                    if (readExact(stream, header) < 24) {
                        throw ModelImportException(
                            ModelImportErrorReason.INVALID_HEADER,
                            "Unable to read GGUF header from '${file.name}'.",
                        )
                    }
                }
                val buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
                val magic = buf.int
                val version = buf.int
                val tensorCount = buf.long
                val metadataKvCount = buf.long
                if (magic != GGUF_MAGIC_LE) {
                    throw ModelImportException(
                        ModelImportErrorReason.INVALID_HEADER,
                        "Invalid GGUF magic header in '${file.name}'.",
                    )
                }
                if (version !in 2..3) {
                    throw ModelImportException(
                        ModelImportErrorReason.INVALID_HEADER,
                        "Unsupported GGUF version $version in '${file.name}' (expected 2 or 3).",
                    )
                }
                if (tensorCount <= 0L || metadataKvCount < 0L) {
                    throw ModelImportException(
                        ModelImportErrorReason.INVALID_HEADER,
                        "Invalid GGUF tensor count ($tensorCount) in '${file.name}'.",
                    )
                }
            }
            "tflite" -> {
                val buf = readHeaderBytes(file, 8)
                if (buf == null || !hasTfl3Magic(buf)) {
                    throw ModelImportException(
                        ModelImportErrorReason.INVALID_HEADER,
                        "Invalid TFLite header (expected TFL3 magic) in '${file.name}'.",
                    )
                }
            }
            "litertlm", "task" -> {
                val buf = readHeaderBytes(file, 8)
                val valid = buf != null && (hasLiteRtLmMagic(buf) || hasZipMagic(buf) || hasTfl3Magic(buf))
                if (!valid) {
                    throw ModelImportException(
                        ModelImportErrorReason.INVALID_HEADER,
                        "Invalid .$ext container header in '${file.name}' (expected LITERTLM, ZIP, or TFL3 magic).",
                    )
                }
            }
            else -> throw ModelImportException(
                ModelImportErrorReason.UNSUPPORTED_FORMAT,
                "Unsupported model extension '$ext'.",
            )
        }
    }

    private fun isValidLiteRtHeaderForExtension(file: File, ext: String): Boolean {
        val buf = readHeaderBytes(file, 8) ?: return false
        return when (ext) {
            "tflite" -> hasTfl3Magic(buf)
            "litertlm", "task" -> hasLiteRtLmMagic(buf) || hasZipMagic(buf) || hasTfl3Magic(buf)
            else -> hasLiteRtLmMagic(buf) || hasZipMagic(buf) || hasTfl3Magic(buf)
        }
    }

    private fun resolveConflictFreeDestination(targetDir: File, sanitizedName: String): File {
        val primary = File(targetDir, sanitizedName)
        if (!primary.exists()) return primary

        val dotIdx = sanitizedName.lastIndexOf('.')
        val base = if (dotIdx > 0) sanitizedName.substring(0, dotIdx) else sanitizedName
        val ext = if (dotIdx > 0) sanitizedName.substring(dotIdx) else ""
        var index = 1
        while (true) {
            val candidate = File(targetDir, "${base}_$index$ext")
            if (!candidate.exists()) return candidate
            index++
        }
    }

    private fun isInsideAppOwnedStorage(file: File): Boolean {
        val targetCanon = canonicalOrAbsolute(file)
        return appOwnedRoots.any { root ->
            val rootCanon = canonicalOrAbsolute(root)
            targetCanon.startsWith(rootCanon + File.separator) || targetCanon == rootCanon
        }
    }

    private fun canonicalOrAbsolute(file: File): String {
        return try {
            file.canonicalPath
        } catch (_: Exception) {
            file.absolutePath
        }
    }

    private fun readHeaderBytes(file: File, count: Int): ByteArray? {
        if (!file.exists() || !file.canRead() || file.length() < count) return null
        return try {
            FileInputStream(file).use { stream ->
                val buf = ByteArray(count)
                if (readExact(stream, buf) < count) null else buf
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun readExact(stream: FileInputStream, buf: ByteArray): Int {
        var total = 0
        while (total < buf.size) {
            val read = stream.read(buf, total, buf.size - total)
            if (read <= 0) break
            total += read
        }
        return total
    }

    private fun hasLiteRtLmMagic(buf: ByteArray): Boolean {
        if (buf.size < 8) return false
        return buf[0] == 'L'.code.toByte() &&
            buf[1] == 'I'.code.toByte() &&
            buf[2] == 'T'.code.toByte() &&
            buf[3] == 'E'.code.toByte() &&
            buf[4] == 'R'.code.toByte() &&
            buf[5] == 'T'.code.toByte() &&
            buf[6] == 'L'.code.toByte() &&
            buf[7] == 'M'.code.toByte()
    }

    private fun hasTfl3Magic(buf: ByteArray): Boolean {
        if (buf.size < 8) return false
        return buf[4] == 'T'.code.toByte() &&
            buf[5] == 'F'.code.toByte() &&
            buf[6] == 'L'.code.toByte() &&
            buf[7] == '3'.code.toByte()
    }

    private fun hasZipMagic(buf: ByteArray): Boolean {
        if (buf.size < 4) return false
        return buf[0] == 'P'.code.toByte() &&
            buf[1] == 'K'.code.toByte() &&
            buf[2] == 0x03.toByte() &&
            buf[3] == 0x04.toByte()
    }

    companion object {
        private const val GGUF_MAGIC_LE = 0x46554747 // "GGUF" little-endian
        private const val MIN_MODEL_BYTES = 8L
        private const val MIN_GGUF_BYTES = 32L
        private const val BYTES_PER_MB = 1024L * 1024L
        private const val MIN_KNOWN_SIZE_BUFFER_BYTES = 64L * 1024L
        private const val MIN_UNKNOWN_SIZE_FREE_BYTES = 16L * 1024L * 1024L
        private const val MAX_IMPORT_BUFFER_BYTES = 128L * 1024L * 1024L
        private const val COPY_BUFFER_BYTES = 64 * 1024
        private const val PROGRESS_EMIT_INTERVAL_BYTES = 256L * 1024L
        private val SUPPORTED_IMPORT_EXTENSIONS = setOf("gguf", "litertlm", "task", "tflite")
    }
}
