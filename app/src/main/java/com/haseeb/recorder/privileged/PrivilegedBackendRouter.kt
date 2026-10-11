package com.haseeb.recorder.privileged

/** Never falls back to a different privilege provider without a settings change. */
internal class PrivilegedBackendRouter(
    private val rootGranted: () -> Boolean,
    private val shizukuGranted: () -> Boolean,
    private val rootCommand: (String) -> Boolean,
    private val shizukuCommand: (String) -> Boolean
) {
    fun isGranted(useRoot: Boolean): Boolean = if (useRoot) rootGranted() else shizukuGranted()

    fun execute(useRoot: Boolean, command: String): Boolean {
        if (!isGranted(useRoot)) return false
        return if (useRoot) rootCommand(command) else shizukuCommand(command)
    }
}
