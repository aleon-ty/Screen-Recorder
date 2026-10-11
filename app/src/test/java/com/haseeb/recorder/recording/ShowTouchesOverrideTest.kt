package com.haseeb.recorder.recording

import org.junit.Assert.*
import org.junit.Test

class ShowTouchesOverrideTest {
    private class Setting(var enabled: Boolean? = false) {
        val writes = mutableListOf<Boolean>()
        var allowWrites = true
        val override = ShowTouchesOverride({ enabled }) {
            writes.add(it)
            if (allowWrites) enabled = it
            allowWrites
        }
    }

    @Test fun developerOptionsAlreadyEnabledRemainEnabledAfterStop() {
        val setting = Setting(true)
        setting.override.enableForRecording()
        setting.override.restore()
        assertEquals(true, setting.enabled)
        assertTrue(setting.writes.isEmpty())
    }

    @Test fun initiallyDisabledSettingReturnsToDisabled() {
        val setting = Setting(false)
        setting.override.enableForRecording()
        assertEquals(true, setting.enabled)
        setting.override.restore()
        assertEquals(false, setting.enabled)
        assertEquals(listOf(true, false), setting.writes)
    }

    @Test fun unknownInitialStateIsNeverOverwritten() {
        val setting = Setting(null)
        setting.override.enableForRecording()
        setting.override.restore()
        assertTrue(setting.writes.isEmpty())
    }

    @Test fun failedEnableDoesNotAcquireCleanupOwnership() {
        val setting = Setting(false)
        setting.allowWrites = false
        setting.override.enableForRecording()
        setting.enabled = true // User later enables it independently.
        setting.allowWrites = true
        setting.override.restore()
        assertEquals(true, setting.enabled)
        assertEquals(listOf(true), setting.writes)
    }

    @Test fun cleanupWithoutApplyingFeatureDoesNothing() {
        val setting = Setting(true)
        setting.override.restore()
        assertEquals(true, setting.enabled)
        assertTrue(setting.writes.isEmpty())
    }

    @Test fun repeatedCleanupRestoresOnlyOnce() {
        val setting = Setting(false)
        setting.override.enableForRecording()
        repeat(3) { setting.override.restore() }
        assertEquals(listOf(true, false), setting.writes)
    }

    @Test fun repeatedEnableKeepsOriginalBaseline() {
        val setting = Setting(false)
        repeat(3) { setting.override.enableForRecording() }
        setting.override.restore()
        assertEquals(listOf(true, false), setting.writes)
    }

    @Test fun failedRestoreCanBeRetried() {
        val setting = Setting(false)
        setting.override.enableForRecording()
        setting.allowWrites = false
        setting.override.restore()
        assertEquals(true, setting.enabled)
        setting.allowWrites = true
        setting.override.restore()
        assertEquals(false, setting.enabled)
        assertEquals(listOf(true, false, false), setting.writes)
    }

    @Test fun settingDisabledByUserDuringRecordingNeedsNoWriteOnStop() {
        val setting = Setting(false)
        setting.override.enableForRecording()
        setting.enabled = false
        setting.override.restore()
        assertEquals(listOf(true), setting.writes)
    }

    @Test fun eachSessionChecksItsOwnBaseline() {
        val setting = Setting(false)
        setting.override.enableForRecording()
        setting.override.restore()
        setting.enabled = true
        setting.override.enableForRecording()
        setting.override.restore()
        assertEquals(true, setting.enabled)
        assertEquals(listOf(true, false), setting.writes)
    }
}
