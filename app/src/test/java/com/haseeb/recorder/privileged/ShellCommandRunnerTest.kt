package com.haseeb.recorder.privileged

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class ShellCommandRunnerTest {
    private class FakeProcess(
        output: String = "0\n",
        errors: String = "",
        private val code: Int = 0,
        private val completes: Boolean = true
    ) : Process() {
        val stdout = ByteArrayInputStream(output.toByteArray())
        val stderr = ByteArrayInputStream(errors.toByteArray())
        val stdin = ByteArrayOutputStream()
        var destroyed = false
        var timeout = 0L
        override fun getOutputStream() = stdin
        override fun getInputStream() = stdout
        override fun getErrorStream() = stderr
        override fun waitFor(): Int = code
        override fun waitFor(timeout: Long, unit: TimeUnit): Boolean {
            this.timeout = unit.toSeconds(timeout)
            return completes
        }
        override fun exitValue(): Int = code
        override fun destroy() { destroyed = true }
    }

    @Test fun missingExecutableReturnsFailureInsteadOfCrashing() {
        val runner = ShellCommandRunner { throw java.io.IOException("su not found") }
        assertFalse(runner.run(listOf("su", "-c", "id -u")).success)
    }

    @Test fun processReceivesTheRequestedArguments() {
        val runner = ShellCommandRunner { args ->
            assertEquals(listOf("su", "-c", "id -u"), args)
            FakeProcess()
        }
        val result = runner.run(listOf("su", "-c", "id -u"))
        assertTrue(result.success)
        assertEquals("0\n", result.output)
    }

    @Test fun timedOutProcessIsDestroyed() {
        val process = FakeProcess(completes = false)
        val result = ShellCommandRunner { process }.run(listOf("su"), 30)
        assertFalse(result.success)
        assertTrue(process.destroyed)
        assertEquals(30L, process.timeout)
    }

    @Test fun nonzeroExitIsReturnedAsFailure() {
        val result = ShellCommandRunner { FakeProcess(code = 1) }.run(listOf("su"))
        assertFalse(result.success)
        assertEquals(1, result.exitCode)
    }

    @Test fun largeStdoutAndStderrAreDrainedButRetainedOutputIsBounded() {
        val process = FakeProcess(output = "a".repeat(100_000), errors = "b".repeat(100_000))
        val result = ShellCommandRunner { process }.run(listOf("su"))
        assertTrue(result.success)
        assertEquals(4096, result.output.length)
        assertEquals(0, process.stdout.available())
        assertEquals(0, process.stderr.available())
    }

    @Test fun existingShizukuProcessUsesTheSameBoundedRunner() {
        val process = FakeProcess()
        val result = ShellCommandRunner().runProcess(process)
        assertTrue(result.success)
        assertEquals(5L, process.timeout)
    }
}
