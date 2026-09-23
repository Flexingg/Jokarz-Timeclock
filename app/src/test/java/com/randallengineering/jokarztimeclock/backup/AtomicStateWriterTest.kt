package com.randallengineering.jokarztimeclock.backup

import com.randallengineering.jokarztimeclock.data.backup.AtomicStateWriter
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AtomicStateWriterTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private fun tempOf(target: File) = File(target.parentFile, target.name + ".tmp")

    @Test
    fun successfulWriteReplacesTheContentAndLeavesNoTempFile() {
        val target = tmpFolder.newFile("state.json").apply { writeText("{\"old\": true}") }

        assertTrue(AtomicStateWriter.write(target, "{\"new\": true}"))
        assertEquals("{\"new\": true}", target.readText())
        assertFalse(tempOf(target).exists())
    }

    @Test
    fun staleTempFileFromACrashIsDiscarded() {
        val target = tmpFolder.newFile("state.json").apply { writeText("{\"old\": true}") }
        tempOf(target).writeText("{\"half-writ")

        assertTrue(AtomicStateWriter.write(target, "{\"new\": true}"))
        assertEquals("{\"new\": true}", target.readText())
        assertFalse(tempOf(target).exists())
    }

    @Test
    fun whenTheRenameFailsTheTargetIsUntouchedAndTheTempFileIsRemoved() {
        // A non-empty directory cannot be replaced by rename(2), so the final step fails after the
        // temp file has been fully written.
        val target = tmpFolder.newFolder("child")
        val inside = File(target, "keep.json").apply { writeText("{\"original\": 1}") }
        val before = inside.readBytes()

        assertFalse(AtomicStateWriter.write(target, "{\"new\": true}"))
        assertTrue(target.isDirectory)
        assertArrayEquals(before, inside.readBytes())
        assertFalse(tempOf(target).exists())
    }

    @Test
    fun whenTheTempFileCannotBeWrittenTheExistingFileIsByteForByteUnchanged() {
        // Blocks the temp path with a non-empty directory. A writer that went straight to the target
        // (the non-atomic behaviour this guards against) would succeed here and change the content.
        val target = tmpFolder.newFile("state.json").apply { writeText("{\"original\": 1}") }
        val before = target.readBytes()
        File(tempOf(target), "blocker").apply { parentFile!!.mkdirs(); writeText("x") }

        assertFalse(AtomicStateWriter.write(target, "{\"new\": true}"))
        assertArrayEquals(before, target.readBytes())
    }
}
