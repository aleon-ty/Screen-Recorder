package com.haseeb.recorder.privileged

internal class RootShell(private val run: (List<String>, Long) -> ShellResult) {
    @Volatile var isGranted = false
        private set

    fun requestPermission(): Boolean {
        val result = run(listOf("su", "-c", "id -u"), 30)
        isGranted = result.success && result.output.trim() == "0"
        return isGranted
    }

    fun execute(command: String): Boolean {
        if (!isGranted) return false
        // The command is a single argv entry; su handles its shell interpretation.
        val result = run(listOf("su", "-c", command), 5)
        if (result.exitCode == null) isGranted = false
        return result.success
    }
}
