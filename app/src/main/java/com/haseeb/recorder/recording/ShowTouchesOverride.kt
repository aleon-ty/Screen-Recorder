package com.haseeb.recorder.recording

/** Owns only an off-to-on change successfully made for this recording session. */
internal class ShowTouchesOverride(
    private val readEnabled: () -> Boolean?,
    private val writeEnabled: (Boolean) -> Boolean
) {
    private var changedByRecording = false

    fun enableForRecording() {
        if (changedByRecording) return
        // Unknown state is left alone, because we cannot safely restore it later.
        if (readEnabled() == false && writeEnabled(true)) changedByRecording = true
    }

    fun restore() {
        if (!changedByRecording) return
        when (readEnabled()) {
            false -> changedByRecording = false // Already restored outside the recorder.
            true -> if (writeEnabled(false)) changedByRecording = false
            null -> Unit // Retain ownership so a later cleanup can retry.
        }
    }
}
