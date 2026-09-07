package com.example.myjarvice.data

import android.content.ContextWrapper
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class LocalAssistantDeviceTest {
    @Test fun memoryAndPdfRoundTrip() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = File(target.cacheDir, "knowledge-test-${System.nanoTime()}").apply { mkdirs() }
        val isolated = object : ContextWrapper(target) { override fun getFilesDir(): File = folder }
        try {
            val store = LocalKnowledgeStore(isolated)
            val fact = store.remember("I prefer short answers.")
            assertEquals(fact.id, store.remember(fact.text).id)
            assertEquals(1, LocalKnowledgeStore(isolated).entries().size)
            store.delete(fact.id)
            assertTrue(store.entries().isEmpty())
            PDFBoxResourceLoader.init(target)
            val pdf = File(folder, "timetable.pdf")
            PDDocument().use { doc ->
                val page = PDPage()
                doc.addPage(page)
                PDPageContentStream(doc, page).use { stream ->
                    stream.beginText()
                    stream.setFont(PDType1Font.HELVETICA, 12f)
                    stream.newLineAtOffset(40f, 700f)
                    stream.showText("The physics exam starts at 10 AM.")
                    stream.endText()
                }
                doc.save(pdf)
            }
            store.importDocument(Uri.fromFile(pdf))
            assertTrue(store.search("physics exam").first().text.contains("10 AM"))
            assertEquals("100", LocalCalculator.evaluate("(18+7)*4"))
        } finally { folder.deleteRecursively() }
    }

    @Test fun phoneModelCompletesTwoReplies() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val path = File(context.filesDir, "models/jarvis-on-device.litertlm").absolutePath
        assertTrue("The on-device model must be installed for this smoke test", File(path).isFile)
        ActivityScenario.launch(ComponentActivity::class.java).use {
        OnDeviceInferenceEngine(context).use { engine ->
            listOf("Hey Jarvis!", "I'm overwhelmed by studying. Help me take one small step.").forEachIndexed { index, prompt ->
                val started = System.currentTimeMillis()
                val reply = engine.generate(path, prompt, emptyList(), "Natural & warm", 0.6f).getOrThrow()
                assertTrue("Expected generated text", reply.isNotBlank())
                assertFalse(reply.contains("couldn't generate"))
                android.util.Log.i("JarvisDeviceTest", "Tone sample ${index + 1} in ${System.currentTimeMillis() - started} ms: $reply")
            }
        }
        }
    }
}
