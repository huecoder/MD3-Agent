package dev.huecoder.md3agent.system.files

import dev.huecoder.md3agent.system.shell.Shell
import dev.huecoder.md3agent.system.shell.ShellResult
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ShellRemovalTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val appFiles by lazy { temp.newFolder("files") }
    private val commands = mutableListOf<String>()
    private var exitCode: Int? = 0
    private var failure: (suspend () -> Unit)? = null
    private val shell = object : Shell {
        override suspend fun run(command: String, timeoutMs: Long): ShellResult {
            commands += command
            failure?.invoke()
            return ShellResult(exitCode, "", "")
        }
    }

    private fun removal(available: Shell? = shell) = ShellRemoval(appFiles) { available }

    @Test
    fun `an item inside the app folder is removed with rm`() = runTest {
        val target = File(appFiles, "workspace/it's here")

        val removed = removal().remove(target)

        assertTrue(removed)
        assertEquals(listOf("rm -rf -- '${target.path.replace("'", "'\\''")}'"), commands)
    }

    @Test
    fun `nothing runs when no shell is available`() = runTest {
        val removed = removal(available = null).remove(File(appFiles, "workspace/a"))

        assertFalse(removed)
        assertTrue(commands.isEmpty())
    }

    @Test
    fun `paths outside the app folder are never touched`() = runTest {
        val outside = temp.newFolder("elsewhere")
        val removal = removal()

        assertFalse(removal.remove(outside))
        assertFalse(removal.remove(appFiles))
        assertFalse(removal.remove(File(appFiles, "../elsewhere")))
        assertTrue(commands.isEmpty())
    }

    @Test
    fun `an item that is still there after rm counts as not removed`() = runTest {
        val stubborn = File(appFiles, "workspace/stuck.txt").apply { parentFile?.mkdirs(); writeText("x") }

        assertFalse(removal().remove(stubborn))
        assertEquals(1, commands.size)
    }

    @Test
    fun `a failing rm counts as not removed`() = runTest {
        exitCode = 1
        assertFalse(removal().remove(File(appFiles, "workspace/a")))

        exitCode = null
        assertFalse(removal().remove(File(appFiles, "workspace/a")))
    }

    @Test
    fun `a shell that is missing or not running counts as not removed`() = runTest {
        failure = { throw IllegalStateException("Shizuku is not running") }

        assertFalse(removal().remove(File(appFiles, "workspace/a")))
    }

    @Test
    fun `a shell that takes too long to connect counts as not removed`() = runTest {
        failure = { withTimeout(1_000) { awaitCancellation() } }

        assertFalse(removal().remove(File(appFiles, "workspace/a")))
    }
}
