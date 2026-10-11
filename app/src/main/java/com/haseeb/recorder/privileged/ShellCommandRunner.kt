package com.haseeb.recorder.privileged

import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

internal data class ShellResult(val exitCode: Int?, val output: String = "") {
    val success: Boolean get() = exitCode == 0
}

/** Bounds process lifetime and drains both pipes so shell output cannot deadlock a command. */
internal class ShellCommandRunner(
    private val start: (List<String>) -> Process = { ProcessBuilder(it).start() }
) {
    fun run(arguments: List<String>, timeoutSeconds: Long = 5): ShellResult = try {
        runProcess(start(arguments), timeoutSeconds)
    } catch (_: Exception) {
        ShellResult(null)
    }

    fun runProcess(process: Process, timeoutSeconds: Long = 5): ShellResult {
        val output = StringBuilder()
        val stdout = thread(isDaemon = true, name = "PrivilegedStdout") {
            try {
                process.inputStream.bufferedReader().use { reader ->
                    val buffer = CharArray(1024)
                    while (true) {
                        val count = reader.read(buffer)
                        if (count < 0) break
                        synchronized(output) {
                            val retained = minOf(count, 4096 - output.length)
                            if (retained > 0) output.append(buffer, 0, retained)
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        val stderr = thread(isDaemon = true, name = "PrivilegedStderr") {
            try {
                process.errorStream.use { stream ->
                    val buffer = ByteArray(1024)
                    while (stream.read(buffer) >= 0) { /* Drain without retaining stderr. */ }
                }
            }
            catch (_: Exception) {}
        }
        return try {
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroy()
                ShellResult(null)
            } else {
                stdout.join(200)
                ShellResult(process.exitValue(), synchronized(output) { output.toString() })
            }
        } catch (_: Exception) {
            process.destroy()
            ShellResult(null)
        } finally {
            try { process.outputStream.close() } catch (_: Exception) {}
            try { process.inputStream.close() } catch (_: Exception) {}
            try { process.errorStream.close() } catch (_: Exception) {}
            stdout.join(200)
            stderr.join(200)
        }
    }
}
