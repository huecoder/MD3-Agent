package com.mwagent.system.files

import com.mwagent.data.settings.FileScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DeviceFilesTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val files by lazy {
        DeviceFiles(
            workspace = temp.newFolder("workspace"),
            attachments = temp.newFolder("attachments"),
            staging = temp.newFolder("staging"),
            uploads = temp.newFolder("uploads"),
            sharedStorage = temp.newFolder("sdcard"),
        )
    }

    @Test
    fun `relative paths land in the workspace and cannot climb out`() {
        assertEquals(files.workspace.resolve("notes/a.txt"), files.resolve("notes/a.txt"))
        assertEquals(temp.root.canonicalFile.resolve("secret"), files.resolve("../secret"))
    }

    @Test
    fun `app scope writes the workspace and lists it`() = runTest {
        files.write(files.resolve("dir/a.txt"), FileScope.APP, "hi".encodeToByteArray(), append = false)
        files.write(files.resolve("dir/a.txt"), FileScope.APP, "!".encodeToByteArray(), append = true)

        val entries = files.list(files.resolve("dir"), FileScope.APP)

        assertEquals(listOf("a.txt"), entries.map { it.name })
        assertEquals("hi!", files.withLocalCopy(files.resolve("dir/a.txt"), FileScope.APP, 1024) { it.readText() })
    }

    @Test
    fun `attachments are readable but not writable`() = runTest {
        val attached = files.attachments.resolve("x.txt").apply { writeText("data") }

        assertEquals("data", files.withLocalCopy(attached, FileScope.APP, 1024) { it.readText() })
        assertThrows(FileAccessException::class.java) {
            runBlocking { files.write(attached, FileScope.APP, byteArrayOf(1), append = false) }
        }
    }

    @Test
    fun `paths outside the allowed folders are refused`() {
        val outside = files.resolve("../outside.txt")
        assertThrows(FileAccessException::class.java) {
            runBlocking { files.list(outside.parentFile!!, FileScope.APP) }
        }
        assertThrows(FileAccessException::class.java) {
            runBlocking { files.withLocalCopy(temp.root.resolve("sdcard/a.jpg"), FileScope.SHARED_STORAGE, 1024) { it } }
        }
    }

    @Test
    fun `files over the size limit are refused`() = runTest {
        val big = files.workspace.resolve("big.bin").apply { writeBytes(ByteArray(2048)) }
        assertThrows(FileAccessException::class.java) {
            runBlocking { files.withLocalCopy(big, FileScope.APP, 1024) { it } }
        }
    }
}
