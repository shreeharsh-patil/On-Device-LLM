package com.example.on_device_qwen.domain.modelmanager

import android.content.Context
import android.net.Uri
import android.os.Environment
import com.example.on_device_qwen.domain.engine.GgufMetadataParser
import com.example.on_device_qwen.model.EngineType
import com.example.on_device_qwen.model.ModelFormat
import com.example.on_device_qwen.model.ModelMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class ModelRepository(private val context: Context) {

    private val searchDirectories: List<File>
        get() = listOfNotNull(
            context.filesDir,
            context.getExternalFilesDir(null),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            File("/sdcard/Download"),
            File("/sdcard/Android/data/${context.packageName}/files"),
            File("/data/local/tmp")
        )

    suspend fun scanModels(): List<ModelMetadata> = withContext(Dispatchers.IO) {
        val found = mutableListOf<ModelMetadata>()
        val seenPaths = mutableSetOf<String>()

        for (dir in searchDirectories) {
            if (!dir.exists() || !dir.isDirectory) continue
            val files = dir.listFiles { _, name ->
                val lower = name.lowercase()
                (lower.endsWith(".litertlm") || lower.endsWith(".litertl") || lower.endsWith(".gguf")) &&
                        !lower.contains("gemma")
            } ?: continue

            for (file in files) {
                if (file.length() > 512 * 1024 && seenPaths.add(file.absolutePath)) {
                    val metadata = if (file.extension.equals("gguf", ignoreCase = true)) {
                        GgufMetadataParser.parse(file) ?: ModelMetadata(
                            file = file,
                            name = file.nameWithoutExtension,
                            format = ModelFormat.GGUF,
                            engineType = EngineType.LLAMA_CPP,
                            fileSizeBytes = file.length(),
                            estimatedRamBytes = (file.length() * 1.3).toLong(),
                            isAbliterated = file.name.contains("abliterat", ignoreCase = true)
                        )
                    } else {
                        val isVlm = file.name.contains("vl", ignoreCase = true)
                        ModelMetadata(
                            file = file,
                            name = file.nameWithoutExtension,
                            format = ModelFormat.LITERT,
                            engineType = EngineType.LITERT,
                            architecture = if (isVlm) "qwen2-vl" else "qwen2.5",
                            quantization = "LiteRT INT4/INT8",
                            fileSizeBytes = file.length(),
                            estimatedRamBytes = (file.length() * 1.2).toLong(),
                            hasVision = isVlm
                        )
                    }
                    found.add(metadata)
                }
            }
        }
        found.sortedByDescending { it.fileSizeBytes }
    }

    suspend fun importModelFromUri(
        uri: Uri,
        onProgress: (Float) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver
            var fileName = "imported_model_${System.currentTimeMillis()}.gguf"

            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    fileName = cursor.getString(nameIndex) ?: fileName
                }
            }

            val targetFile = File(context.filesDir, fileName)
            val totalBytes = contentResolver.openFileDescriptor(uri, "r")?.statSize ?: -1L

            contentResolver.openInputStream(uri)?.use { input ->
                targetFile.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var bytesCopied = 0L
                    var read: Int
                    while (input.read(buffer).also { read = it } >= 0) {
                        output.write(buffer, 0, read)
                        bytesCopied += read
                        if (totalBytes > 0) {
                            onProgress(bytesCopied.toFloat() / totalBytes)
                        }
                    }
                }
            }

            Result.success(targetFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteModel(file: File): Boolean = withContext(Dispatchers.IO) {
        try {
            if (file.exists() && file.parentFile?.absolutePath == context.filesDir.absolutePath) {
                file.delete()
            } else false
        } catch (_: Exception) {
            false
        }
    }
}
