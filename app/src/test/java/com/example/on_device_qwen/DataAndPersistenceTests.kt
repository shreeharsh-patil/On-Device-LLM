package com.example.on_device_qwen

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.test.core.app.ApplicationProvider
import com.example.on_device_qwen.data.db.ChatDatabaseHelper
import com.example.on_device_qwen.data.preferences.AppPreferences
import com.example.on_device_qwen.data.repository.ConversationRepository
import com.example.on_device_qwen.domain.modelmanager.ImportProgress
import com.example.on_device_qwen.domain.modelmanager.ModelImportErrorReason
import com.example.on_device_qwen.domain.modelmanager.ModelImportException
import com.example.on_device_qwen.domain.modelmanager.ModelImportState
import com.example.on_device_qwen.domain.modelmanager.ModelRepository
import com.example.on_device_qwen.model.BenchmarkMetrics
import com.example.on_device_qwen.model.ChatMessage
import com.example.on_device_qwen.model.ConversationSession
import com.example.on_device_qwen.model.ConversationSummary
import com.example.on_device_qwen.model.MemoryCategory
import com.example.on_device_qwen.model.MemoryItem
import com.example.on_device_qwen.model.ModelFormat
import com.example.on_device_qwen.model.PromptProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowStatFs
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DataAndPersistenceTests {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(ChatDatabaseHelper.DATABASE_NAME)
        context.getSharedPreferences("on_device_qwen_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        SafTestContentProvider.clear()
        cleanModelDirs()
    }

    @After
    fun tearDown() {
        ShadowStatFs.reset()
        SafTestContentProvider.clear()
        context.deleteDatabase(ChatDatabaseHelper.DATABASE_NAME)
        cleanModelDirs()
    }

    private fun cleanModelDirs() {
        File(context.filesDir, "models").deleteRecursively()
        context.filesDir.listFiles()?.forEach { if (it.isFile) it.delete() }
        context.getExternalFilesDir("models")?.deleteRecursively()
        context.getExternalFilesDir(null)?.listFiles()?.forEach { if (it.isFile) it.delete() }
    }

    // ── Database v2 -> v3 Upgrade & Persistence Tests ─────────────────────────

    @Test
    fun testDatabaseV2ToV3MigrationPreservesAllDataAndAddsV3Columns() = runBlocking {
        // 1. Create a real v2 SQLite database on disk without v3 columns.
        val legacyHelper = object : SQLiteOpenHelper(
            context,
            ChatDatabaseHelper.DATABASE_NAME,
            null,
            2,
        ) {
            override fun onCreate(db: SQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE ${ChatDatabaseHelper.TABLE_CONVERSATIONS} (
                        ${ChatDatabaseHelper.COL_CONV_ID} TEXT PRIMARY KEY,
                        ${ChatDatabaseHelper.COL_CONV_TITLE} TEXT NOT NULL,
                        ${ChatDatabaseHelper.COL_CONV_CREATED_AT} INTEGER NOT NULL,
                        ${ChatDatabaseHelper.COL_CONV_UPDATED_AT} INTEGER NOT NULL,
                        ${ChatDatabaseHelper.COL_CONV_MODEL_NAME} TEXT,
                        ${ChatDatabaseHelper.COL_CONV_PINNED} INTEGER DEFAULT 0,
                        ${ChatDatabaseHelper.COL_CONV_PREVIEW} TEXT DEFAULT ''
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE ${ChatDatabaseHelper.TABLE_MESSAGES} (
                        ${ChatDatabaseHelper.COL_MSG_ID} INTEGER PRIMARY KEY,
                        ${ChatDatabaseHelper.COL_MSG_CONV_ID} TEXT NOT NULL,
                        ${ChatDatabaseHelper.COL_MSG_TEXT} TEXT NOT NULL,
                        ${ChatDatabaseHelper.COL_MSG_IS_USER} INTEGER NOT NULL,
                        ${ChatDatabaseHelper.COL_MSG_TIMESTAMP} INTEGER NOT NULL,
                        ${ChatDatabaseHelper.COL_MSG_IMAGE_PATH} TEXT,
                        ${ChatDatabaseHelper.COL_MSG_THINK_TEXT} TEXT,
                        ${ChatDatabaseHelper.COL_MSG_TTFT_MS} INTEGER DEFAULT 0,
                        ${ChatDatabaseHelper.COL_MSG_DECODE_TPS} REAL DEFAULT 0.0,
                        ${ChatDatabaseHelper.COL_MSG_DECODE_TOKENS} INTEGER DEFAULT 0,
                        ${ChatDatabaseHelper.COL_MSG_DURATION_MS} INTEGER DEFAULT 0,
                        ${ChatDatabaseHelper.COL_MSG_BACKEND} TEXT DEFAULT 'CPU',
                        ${ChatDatabaseHelper.COL_MSG_IS_ERROR} INTEGER DEFAULT 0,
                        FOREIGN KEY (${ChatDatabaseHelper.COL_MSG_CONV_ID})
                            REFERENCES ${ChatDatabaseHelper.TABLE_CONVERSATIONS}(${ChatDatabaseHelper.COL_CONV_ID})
                            ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE ${ChatDatabaseHelper.TABLE_MEMORIES} (
                        ${ChatDatabaseHelper.COL_MEM_ID} TEXT PRIMARY KEY,
                        ${ChatDatabaseHelper.COL_MEM_CATEGORY} TEXT NOT NULL,
                        ${ChatDatabaseHelper.COL_MEM_CONTENT} TEXT NOT NULL,
                        ${ChatDatabaseHelper.COL_MEM_SOURCE_CONV_ID} TEXT,
                        ${ChatDatabaseHelper.COL_MEM_CREATED_AT} INTEGER NOT NULL,
                        ${ChatDatabaseHelper.COL_MEM_UPDATED_AT} INTEGER NOT NULL,
                        ${ChatDatabaseHelper.COL_MEM_CONFIDENCE} REAL DEFAULT 1.0,
                        ${ChatDatabaseHelper.COL_MEM_KEYWORDS} TEXT DEFAULT '',
                        ${ChatDatabaseHelper.COL_MEM_PINNED} INTEGER DEFAULT 0
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE ${ChatDatabaseHelper.TABLE_SUMMARIES} (
                        ${ChatDatabaseHelper.COL_SUM_CONV_ID} TEXT PRIMARY KEY,
                        ${ChatDatabaseHelper.COL_SUM_TEXT} TEXT NOT NULL,
                        ${ChatDatabaseHelper.COL_SUM_LAST_MSG_TIME} INTEGER NOT NULL,
                        ${ChatDatabaseHelper.COL_SUM_UPDATED_AT} INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }

            override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }

        legacyHelper.writableDatabase.use { db ->
            db.execSQL(
                "INSERT INTO conversations VALUES ('conv_v2', 'Legacy Chat', 1000, 2000, 'Qwen2.5-1.5B', 1, 'Answer')"
            )
            db.execSQL(
                "INSERT INTO messages VALUES (101, 'conv_v2', 'Hello', 1, 1000, NULL, NULL, 0, 0.0, 0, 0, 'CPU', 0)"
            )
            db.execSQL(
                "INSERT INTO messages VALUES (102, 'conv_v2', 'Answer', 0, 1500, NULL, 'thinking', 120, 18.5, 42, 2300, 'CPU_6T', 0)"
            )
            db.execSQL(
                "INSERT INTO memories VALUES ('mem_v2', 'USER', 'User prefers dark theme', 'conv_v2', 1000, 1000, 0.9, 'dark theme', 1)"
            )
            db.execSQL(
                "INSERT INTO conversation_summaries VALUES ('conv_v2', 'Greeted and discussed theme.', 1500, 1600)"
            )
        }
        legacyHelper.close()

        // 2. Open with v3 ChatDatabaseHelper + ConversationRepository.
        val helper = ChatDatabaseHelper(context)
        val columns = mutableSetOf<String>()
        helper.readableDatabase.rawQuery("PRAGMA table_info(${ChatDatabaseHelper.TABLE_MESSAGES})", null).use { c ->
            val nameIdx = c.getColumnIndexOrThrow("name")
            while (c.moveToNext()) columns.add(c.getString(nameIdx))
        }
        helper.close()

        assertTrue(ChatDatabaseHelper.COL_MSG_PREFILL_TPS in columns)
        assertTrue(ChatDatabaseHelper.COL_MSG_PREFILL_TOKENS in columns)
        assertTrue(ChatDatabaseHelper.COL_MSG_MODEL_NAME in columns)
        assertTrue(ChatDatabaseHelper.COL_MSG_BACKEND_LABEL in columns)
        assertTrue(ChatDatabaseHelper.COL_MSG_IS_CANCELLED in columns)

        val repo = ConversationRepository(context)
        val conv = repo.getConversationById("conv_v2")
        assertNotNull("v2 conversation must survive v3 upgrade", conv)
        assertEquals("Legacy Chat", conv!!.title)
        assertTrue(conv.isPinned)

        val msgs = repo.getMessages("conv_v2")
        assertEquals("Both v2 messages must survive upgrade", 2, msgs.size)
        assertEquals("Hello", msgs[0].text)
        assertNull(msgs[0].benchmark)
        assertEquals("Answer", msgs[1].text)
        assertEquals(18.5, msgs[1].benchmark!!.decodeTps, 0.01)
        assertFalse(msgs[1].isCancelled)

        val mems = repo.getAllMemories()
        assertEquals(1, mems.size)
        assertEquals("User prefers dark theme", mems[0].content)

        val summary = repo.getConversationSummary("conv_v2")
        assertNotNull(summary)
        assertEquals("Greeted and discussed theme.", summary!!.summaryText)

        // 3. Verify v3 fields round-trip cleanly (including cancelled turn & prefill metrics).
        val v3Msg = ChatMessage(
            id = 200L,
            conversationId = "conv_v2",
            text = "Partial stopped output",
            isUser = false,
            timestamp = 3000L,
            benchmark = BenchmarkMetrics(
                ttftMs = 0L,
                decodeTps = 0.0,
                prefillTps = 45.2,
                prefillTokens = 128,
                decodeTokens = 17,
                totalDurationMs = 950L,
                backend = "NPU",
            ),
            modelName = "Qwen2.5-1.5B-Q4",
            backendLabel = "NPU (Fast)",
            isCancelled = true,
        )
        repo.saveMessage(v3Msg)

        val reloadedV3 = repo.getMessages("conv_v2").last()
        assertEquals("Qwen2.5-1.5B-Q4", reloadedV3.modelName)
        assertEquals("NPU (Fast)", reloadedV3.backendLabel)
        assertTrue(reloadedV3.isCancelled)
        val reloadedBenchmark = reloadedV3.benchmark
        assertNotNull("BenchmarkMetrics must be restored on prefill/decodeTokens/isCancelled", reloadedBenchmark)
        assertEquals(45.2, reloadedBenchmark!!.prefillTps!!, 0.01)
        assertEquals(128, reloadedBenchmark.prefillTokens)
        assertEquals(17, reloadedBenchmark.decodeTokens)
    }

    // ── Transactional ConversationRepository & Helpers Tests ──────────────────

    @Test
    fun testConversationRepositoryTransactionsHelpersAndImageCleanup() = runBlocking {
        val repo = ConversationRepository(context)
        val convId = "conv_tx"
        repo.saveConversation(ConversationSession(id = convId, title = "TX Test"))

        val vlmImg1 = File(context.cacheDir, "vlm_input_1.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val vlmImg2 = File(context.cacheDir, "vlm_input_2.jpg").apply { writeBytes(byteArrayOf(4, 5, 6)) }
        val userPhotoOutsideCache = tempFolder.newFile("user_photo.jpg").apply { writeBytes(byteArrayOf(7, 8, 9)) }

        // Save messages with id <= 0 -> monotonic IDs prevent PK collisions even at same timestamp
        repo.saveMessage(
            ChatMessage(
                id = 0L,
                conversationId = convId,
                text = "Turn 1 user",
                isUser = true,
                timestamp = 1000L,
                imagePath = vlmImg1.absolutePath,
            )
        )
        repo.saveMessage(
            ChatMessage(
                id = -5L,
                conversationId = convId,
                text = "Turn 1 assistant",
                isUser = false,
                timestamp = 1000L,
            )
        )
        repo.saveMessage(
            ChatMessage(
                id = 0L,
                conversationId = convId,
                text = "Turn 2 user",
                isUser = true,
                timestamp = 2000L,
                imagePath = userPhotoOutsideCache.absolutePath,
            )
        )
        repo.saveMessage(
            ChatMessage(
                id = 0L,
                conversationId = convId,
                text = "Turn 2 assistant first",
                isUser = false,
                timestamp = 3000L,
                imagePath = vlmImg2.absolutePath,
            )
        )
        repo.saveMessage(
            ChatMessage(
                id = 0L,
                conversationId = convId,
                text = "Turn 2 assistant tiebreak",
                isUser = false,
                timestamp = 3000L,
            )
        )

        repo.saveConversationSummary(
            ConversationSummary(convId, "Stale summary", 3000L)
        )

        val allMsgs = repo.getMessages(convId)
        assertEquals(5, allMsgs.size)
        assertTrue("Monotonic IDs should all be strictly increasing", allMsgs.zipWithNext().all { (a, b) -> a.id < b.id })

        // Verify getUserMessageForAssistantTurn on equal-timestamp turn 1 and turn 2
        val turn1User = repo.getUserMessageForAssistantTurn(convId, allMsgs[1].id)
        assertEquals("Turn 1 user", turn1User?.text)
        val turn2User = repo.getUserMessageForAssistantTurn(convId, allMsgs[3].id)
        assertEquals("Turn 2 user", turn2User?.text)

        // deleteLastAssistantMessage must use (timestamp DESC, id DESC) and invalidate summary
        val deletedLastId = repo.deleteLastAssistantMessage(convId)
        assertEquals(allMsgs[4].id, deletedLastId)
        assertNull("Summary must be invalidated on assistant deletion", repo.getConversationSummary(convId))
        assertEquals("Turn 2 assistant first", repo.getConversationById(convId)!!.lastMessagePreview)

        // deleteMessagesFromId from Turn 2 user -> deletes Turn 2 user & remaining Turn 2 assistant
        repo.saveConversationSummary(ConversationSummary(convId, "Summary 2", 3000L))
        repo.deleteMessagesFromId(convId, allMsgs[2].id)

        val remainingAfterTruncate = repo.getMessages(convId)
        assertEquals(2, remainingAfterTruncate.size)
        assertEquals("Turn 1 assistant", remainingAfterTruncate.last().text)
        assertEquals("Turn 1 assistant", repo.getConversationById(convId)!!.lastMessagePreview)
        assertNull(repo.getConversationSummary(convId))
        assertTrue("vlm_input_1 referenced by surviving turn must remain", vlmImg1.exists())
        assertFalse("Orphaned vlm_input_2 inside cacheDir must be cleaned up", vlmImg2.exists())
        assertTrue("User photo outside cacheDir must NEVER be deleted", userPhotoOutsideCache.exists())

        // clearAllConversations must remove messages, conversations, summaries, and remaining VLM cache files
        repo.saveConversationSummary(ConversationSummary(convId, "Summary 3", 1000L))
        repo.clearAllConversations()
        assertTrue(repo.getAllConversations().isEmpty())
        assertTrue(repo.getMessages(convId).isEmpty())
        assertNull(repo.getConversationSummary(convId))
        assertFalse("Remaining vlm_input_1 must be deleted on clearAllConversations", vlmImg1.exists())
    }

    @Test
    fun testConversationRepositoryMemorySanitizationAndKeywordSync() = runBlocking {
        val repo = ConversationRepository(context)
        val blocked = repo.saveMemory(
            MemoryItem(
                id = "m_email",
                category = MemoryCategory.USER,
                content = "Email me at secret@example.com",
            )
        )
        assertEquals(-1L, blocked)
        assertTrue(repo.getAllMemories().isEmpty())

        repo.saveMemory(
            MemoryItem(
                id = "m_clean",
                category = MemoryCategory.PROJECT,
                content = "<|im_start|>Project uses Vulkan<|im_end|>",
                keywords = "vulkan",
            )
        )
        assertEquals("Project uses Vulkan", repo.getAllMemories().single().content)

        val piiUpdate = repo.updateMemory("m_clean", "SSN 123-45-6789", MemoryCategory.PROJECT)
        assertEquals(0, piiUpdate)
        assertEquals("Project uses Vulkan", repo.getAllMemories().single().content)

        repo.updateMemoryWithKeywords(
            id = "m_clean",
            newContent = "<|eot_id|>Project uses OpenCL<|endoftext|>",
            keywords = "opencl gpu",
            category = MemoryCategory.PROJECT,
        )
        val updated = repo.getAllMemories().single()
        assertEquals("Project uses OpenCL", updated.content)
        assertEquals("opencl gpu", updated.keywords)
    }

    // ── Thread-Safe AppPreferences & Flags Tests ──────────────────────────────

    @Test
    fun testAppPreferencesThreadSafetyAndNewBooleans() {
        val prefs = AppPreferences(context)

        assertFalse(prefs.onboardingCompleted)
        assertFalse(prefs.dynamicColorEnabled)
        assertFalse(prefs.showAdvancedSettings)
        assertTrue(prefs.shouldShowOnboarding(hasInstalledModels = false))
        assertFalse(prefs.shouldShowOnboarding(hasInstalledModels = true))

        prefs.onboardingCompleted = true
        prefs.dynamicColorEnabled = true
        prefs.showAdvancedSettings = true
        assertTrue(prefs.onboardingCompleted)
        assertTrue(prefs.dynamicColorEnabled)
        assertTrue(prefs.showAdvancedSettings)
        assertFalse(prefs.shouldShowOnboarding(hasInstalledModels = false))

        // Stress-test concurrent registerModelPath & addOrUpdateProfile across threads.
        val threadCount = 8
        val opsPerThread = 25
        val latch = CountDownLatch(threadCount)
        val pool = Executors.newFixedThreadPool(threadCount)

        for (t in 0 until threadCount) {
            pool.execute {
                try {
                    for (i in 0 until opsPerThread) {
                        val idx = t * opsPerThread + i
                        prefs.registerModelPath("/app/models/model_$idx.gguf")
                        if (i % 5 == 0) {
                            prefs.addOrUpdateProfile(
                                PromptProfile(
                                    id = "custom_${t}_$i",
                                    name = "Profile $t-$i",
                                    instructionText = "Be concise",
                                )
                            )
                        }
                    }
                } finally {
                    latch.countDown()
                }
            }
        }
        assertTrue(latch.await(15, TimeUnit.SECONDS))
        pool.shutdown()

        assertEquals(
            "Concurrent registerModelPath calls must not drop paths",
            threadCount * opsPerThread,
            prefs.registeredModelPaths.size,
        )
        assertEquals(
            "Concurrent addOrUpdateProfile calls must preserve all built-ins + custom profiles",
            PromptProfile.builtInProfiles().size + threadCount * (opsPerThread / 5),
            prefs.promptProfiles.size,
        )
    }

    // ── Real ModelRepository SAF Import, Validation, Deletion & Pruning Tests ─

    @Test
    fun testSafImportStagesAtomicallyAndValidatesFormats() = runBlocking {
        Robolectric.buildContentProvider(SafTestContentProvider::class.java)
            .create("com.example.on_device_qwen.testprovider")

        val modelRepo = ModelRepository(context)
        val prefs = AppPreferences(context)

        // 1. Valid GGUF with path-traversal displayName
        val ggufSource = createValidGgufFile(tempFolder.newFile("src1.bin"), sizeBytes = 4096)
        val ggufUri = SafTestContentProvider.registerFile(
            path = "/gguf1",
            file = ggufSource,
            displayName = "../../evil/Qwen 2.5 (1.5B)?.gguf",
            reportedSize = ggufSource.length(),
        )
        val states = mutableListOf<ModelImportState>()
        val res = modelRepo.importModelWithState(ggufUri) { states.add(it) }

        assertTrue("Valid GGUF import should succeed: ${res.exceptionOrNull()}", res.isSuccess)
        val importedMeta = res.getOrThrow()
        assertEquals(ModelFormat.GGUF, importedMeta.format)
        assertFalse(importedMeta.isCorrupted)
        assertTrue(importedMeta.file.parentFile!!.name == "models")
        assertFalse("Sanitized filename must not contain path traversal or '?'", importedMeta.file.name.contains(".."))
        assertFalse(importedMeta.file.name.contains("?"))
        assertTrue(states.any { it.progress == ImportProgress.COPYING })
        assertTrue(states.any { it.progress == ImportProgress.VERIFYING })
        assertTrue(states.any { it.progress == ImportProgress.FINALIZING })
        assertEquals(ImportProgress.READY, states.last().progress)
        assertTrue(prefs.registeredModelPaths.contains(importedMeta.file.absolutePath))
        assertTrue("No .tmp files should remain after import", importedMeta.file.parentFile!!.listFiles()!!.none { it.name.endsWith(".tmp") })

        // 2. Valid .litertlm with real LITERTLM magic
        val lrtSource = tempFolder.newFile("src2.bin").apply {
            writeBytes("LITERTLM".toByteArray(Charsets.US_ASCII) + ByteArray(2040))
        }
        val lrtUri = SafTestContentProvider.registerFile("/lrt1", lrtSource, "gemma.litertlm", lrtSource.length())
        val lrtRes = modelRepo.importModelWithState(lrtUri) {}
        assertTrue(lrtRes.isSuccess)
        assertEquals(ModelFormat.LITERT, lrtRes.getOrThrow().format)

        // 3. Valid .tflite with TFL3 magic at offset 4
        val tflSource = tempFolder.newFile("src3.bin").apply {
            writeBytes(byteArrayOf(0x18, 0, 0, 0, 'T'.code.toByte(), 'F'.code.toByte(), 'L'.code.toByte(), '3'.code.toByte()) + ByteArray(1024))
        }
        val tflUri = SafTestContentProvider.registerFile("/tfl1", tflSource, "embed.tflite", tflSource.length())
        assertTrue(modelRepo.importModelWithState(tflUri) {}.isSuccess)
    }

    @Test
    fun testSafImportRejectsBadFormatsHeadersEmptyTruncatedAndInsufficientDisk() = runBlocking {
        Robolectric.buildContentProvider(SafTestContentProvider::class.java)
            .create("com.example.on_device_qwen.testprovider")

        val modelRepo = ModelRepository(context)
        val modelsDir = File(context.filesDir, "models").apply { mkdirs() }

        // 1. Unsupported extension
        val txtFile = tempFolder.newFile("notes.txt").apply { writeText("hello") }
        val txtUri = SafTestContentProvider.registerFile("/bad_ext", txtFile, "notes.txt", txtFile.length())
        val txtRes = modelRepo.importModelWithState(txtUri) {}
        assertTrue(txtRes.isFailure)
        assertEquals(ModelImportErrorReason.UNSUPPORTED_FORMAT, (txtRes.exceptionOrNull() as ModelImportException).reason)

        // 2. Corrupt GGUF (tensorCount == 0 & bad magic)
        val zeroTensorGguf = createValidGgufFile(tempFolder.newFile("zero_tensor.bin"), tensorCount = 0L)
        val zeroTensorUri = SafTestContentProvider.registerFile("/zero_t", zeroTensorGguf, "empty_tensors.gguf", zeroTensorGguf.length())
        val zeroRes = modelRepo.importModelWithState(zeroTensorUri) {}
        assertTrue(zeroRes.isFailure)
        assertEquals(ModelImportErrorReason.INVALID_HEADER, (zeroRes.exceptionOrNull() as ModelImportException).reason)

        val badMagicFile = tempFolder.newFile("bad_magic.bin").apply { writeBytes("HTML404PAGE_NOT_MODEL_BYTES_HERE".toByteArray()) }
        val badMagicUri = SafTestContentProvider.registerFile("/bad_magic", badMagicFile, "fake.gguf", badMagicFile.length())
        val magicRes = modelRepo.importModelWithState(badMagicUri) {}
        assertTrue(magicRes.isFailure)
        assertEquals(ModelImportErrorReason.INVALID_HEADER, (magicRes.exceptionOrNull() as ModelImportException).reason)

        // 3. Empty file (0 bytes)
        val emptyFile = tempFolder.newFile("empty.bin")
        val emptyUri = SafTestContentProvider.registerFile("/empty", emptyFile, "empty.litertlm", 0L)
        val emptyRes = modelRepo.importModelWithState(emptyUri) {}
        assertTrue(emptyRes.isFailure)
        assertEquals(ModelImportErrorReason.INCOMPLETE_TRANSFER, (emptyRes.exceptionOrNull() as ModelImportException).reason)

        // 4. Truncated transfer (provider reports 8192 bytes, actual stream yields 512 bytes)
        val truncFile = createValidGgufFile(tempFolder.newFile("trunc.bin"), sizeBytes = 512)
        val truncUri = SafTestContentProvider.registerFile("/trunc", truncFile, "truncated.gguf", 8192L)
        val truncRes = modelRepo.importModelWithState(truncUri) {}
        assertTrue(truncRes.isFailure)
        assertEquals(ModelImportErrorReason.INCOMPLETE_TRANSFER, (truncRes.exceptionOrNull() as ModelImportException).reason)

        // 5. Insufficient disk space checked BEFORE copying via StatFs
        val extModelsDir = context.getExternalFilesDir("models")?.apply { mkdirs() }
        ShadowStatFs.registerStats(modelsDir.path, 1000, 1, 1) // 4 KB free
        extModelsDir?.let { ShadowStatFs.registerStats(it.path, 1000, 1, 1) }

        val validLarge = createValidGgufFile(tempFolder.newFile("big.bin"), sizeBytes = 32 * 1024)
        val lowSpaceUri = SafTestContentProvider.registerFile("/lowspace", validLarge, "qwen_big.gguf", validLarge.length())
        val lowSpaceStates = mutableListOf<ModelImportState>()
        val lowSpaceRes = modelRepo.importModelWithState(lowSpaceUri) { lowSpaceStates.add(it) }
        assertTrue(lowSpaceRes.isFailure)
        assertEquals(ModelImportErrorReason.INSUFFICIENT_STORAGE, (lowSpaceRes.exceptionOrNull() as ModelImportException).reason)
        assertEquals(ImportProgress.FAILED, lowSpaceStates.last().progress)

        assertTrue("No .tmp files should remain after failed imports", modelsDir.listFiles()!!.none { it.name.endsWith(".tmp") })
        assertTrue("No invalid destination files should remain", modelsDir.listFiles()!!.isEmpty())
    }

    @Test
    fun testSafImportConflictPreservesActiveFileAndCancellationCleansTemp() = runBlocking {
        Robolectric.buildContentProvider(SafTestContentProvider::class.java)
            .create("com.example.on_device_qwen.testprovider")

        val modelRepo = ModelRepository(context)
        val prefs = AppPreferences(context)
        val modelsDir = File(context.filesDir, "models").apply { mkdirs() }

        // Existing active model in models/qwen.gguf
        val activeModel = createValidGgufFile(File(modelsDir, "qwen.gguf"), sizeBytes = 2048)
        val originalActiveBytes = activeModel.readBytes()
        prefs.activeModelPath = activeModel.absolutePath
        prefs.registerModelPath(activeModel.absolutePath)

        // Import another qwen.gguf with a DIFFERENT size (4096 bytes)
        val incoming = createValidGgufFile(tempFolder.newFile("incoming.bin"), sizeBytes = 4096)
        val incomingUri = SafTestContentProvider.registerFile("/conflict", incoming, "qwen.gguf", incoming.length())
        val conflictRes = modelRepo.importModelWithState(incomingUri) {}

        assertTrue(conflictRes.isSuccess)
        val importedFile = conflictRes.getOrThrow().file
        assertEquals("qwen_1.gguf", importedFile.name)
        assertArrayEquals("Active model bytes must remain untouched on filename conflict", originalActiveBytes, activeModel.readBytes())

        // Cancellation test mid-copy (1 MB file triggers progress emission inside copy loop)
        val largeFile = createValidGgufFile(tempFolder.newFile("cancel_src.bin"), sizeBytes = 1024 * 1024)
        val cancelUri = SafTestContentProvider.registerFile("/cancel", largeFile, "cancelled.gguf", largeFile.length())
        val startedCopying = CompletableDeferred<Unit>()
        var caughtCancel = false

        val job = launch {
            try {
                modelRepo.importModelWithState(cancelUri) { state ->
                    if (state.progress == ImportProgress.COPYING && state.bytesCopied > 0L) {
                        startedCopying.complete(Unit)
                    }
                }
            } catch (ce: CancellationException) {
                caughtCancel = true
                throw ce
            }
        }
        startedCopying.await()
        job.cancel()
        job.join()

        assertTrue("CancellationException must propagate cleanly", caughtCancel)
        assertFalse("Cancelled import must not leave destination", File(modelsDir, "cancelled.gguf").exists())
        assertTrue("Cancelled import must clean up .tmp staging file", modelsDir.listFiles()!!.none { it.name.contains("cancelled") })
    }

    @Test
    fun testScanModelsCleansTempPrunesDeletedAndDeleteModelCoversAllAppDirs() = runBlocking {
        val modelRepo = ModelRepository(context)
        val prefs = AppPreferences(context)

        val internalModelsDir = File(context.filesDir, "models").apply { mkdirs() }
        val extModelsDir = context.getExternalFilesDir("models")!!.apply { mkdirs() }
        val legacyInternalRoot = context.filesDir
        val legacyExtRoot = context.getExternalFilesDir(null)!!

        val m1 = createValidGgufFile(File(internalModelsDir, "m1.gguf"), sizeBytes = 4096)
        val m2 = createValidGgufFile(File(extModelsDir, "m2.gguf"), sizeBytes = 3072)
        val m3 = createValidGgufFile(File(legacyInternalRoot, "m3.gguf"), sizeBytes = 2048)
        val m4 = createValidGgufFile(File(legacyExtRoot, "m4.gguf"), sizeBytes = 1024)
        val orphanTmp = File(internalModelsDir, ".interrupted.gguf.tmp").apply { writeBytes(ByteArray(512)) }

        // Register a nonexistent path & stale activeModelPath to verify pruning
        val deletedPath = File(internalModelsDir, "ghost_deleted.gguf").absolutePath
        prefs.registerModelPath(deletedPath)
        prefs.activeModelPath = deletedPath

        val scanned = modelRepo.scanModels()
        assertFalse("Orphaned .tmp file must be deleted during scanModels()", orphanTmp.exists())
        assertFalse("Deleted registered path must be pruned", deletedPath in prefs.registeredModelPaths)
        assertNull("Stale activeModelPath must be cleared", prefs.activeModelPath)
        assertEquals(setOf("m1.gguf", "m2.gguf", "m3.gguf", "m4.gguf"), scanned.map { it.file.name }.toSet())

        // Set m2 as activeModelPath via non-canonical relative segment and verify deleteModel removes it cleanly
        val aliasedM2 = File(extModelsDir, "../models/m2.gguf")
        prefs.activeModelPath = aliasedM2.absolutePath

        assertTrue(modelRepo.deleteModel(m1))
        assertFalse(m1.exists())

        assertTrue(modelRepo.deleteModel(m2))
        assertFalse(m2.exists())
        assertNull("Deleting active model must clear prefs.activeModelPath via canonical match", prefs.activeModelPath)
        assertFalse(m2.absolutePath in prefs.registeredModelPaths)

        assertTrue(modelRepo.deleteModel(m3))
        assertFalse(m3.exists())

        assertTrue(modelRepo.deleteModel(m4))
        assertFalse(m4.exists())
    }

    private fun createValidGgufFile(
        target: File,
        sizeBytes: Long = 2048,
        version: Int = 3,
        tensorCount: Long = 1L,
    ): File {
        FileOutputStream(target).use { stream ->
            val header = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN).apply {
                put('G'.code.toByte())
                put('G'.code.toByte())
                put('U'.code.toByte())
                put('F'.code.toByte())
                putInt(version)
                putLong(tensorCount)
                putLong(0L)
            }.array()
            stream.write(header)
            val remaining = (sizeBytes - header.size).toInt()
            if (remaining > 0) {
                stream.write(ByteArray(remaining))
            }
        }
        return target
    }

    /**
     * In-process SAF test ContentProvider backed by local test files for Robolectric tests.
     */
    class SafTestContentProvider : ContentProvider() {
        data class Entry(val file: File, val displayName: String, val reportedSize: Long)

        override fun onCreate(): Boolean = true

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor? {
            val entry = entries[uri.path] ?: return null
            val cursor = MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE))
            cursor.addRow(arrayOf<Any>(entry.displayName, entry.reportedSize))
            return cursor
        }

        override fun getType(uri: Uri): String = "application/octet-stream"

        override fun insert(uri: Uri, values: ContentValues?): Uri? = null

        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

        override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<out String>?,
        ): Int = 0

        override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor? {
            val entry = entries[uri.path] ?: return null
            val pfd = ParcelFileDescriptor.open(entry.file, ParcelFileDescriptor.MODE_READ_ONLY)
            return AssetFileDescriptor(pfd, 0L, entry.file.length())
        }

        companion object {
            private const val AUTHORITY = "com.example.on_device_qwen.testprovider"
            private val entries = mutableMapOf<String, Entry>()

            fun registerFile(path: String, file: File, displayName: String, reportedSize: Long): Uri {
                entries[path] = Entry(file, displayName, reportedSize)
                return Uri.parse("content://$AUTHORITY$path")
            }

            fun clear() {
                entries.clear()
            }
        }
    }
}
