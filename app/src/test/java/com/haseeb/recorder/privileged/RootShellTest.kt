package com.haseeb.recorder.privileged

import org.junit.Assert.*
import org.junit.Test

class RootShellTest {
    @Test fun rootIsGrantedOnlyForUidZero() {
        val shell = RootShell { args, timeout ->
            assertEquals(listOf("su", "-c", "id -u"), args)
            assertEquals(30L, timeout)
            ShellResult(0, "0\n")
        }
        assertTrue(shell.requestPermission())
        assertTrue(shell.isGranted)
    }

    @Test fun aSuccessfulNonRootShellDoesNotGrantRoot() {
        val shell = RootShell { _, _ -> ShellResult(0, "2000\n") }
        assertFalse(shell.requestPermission())
        assertFalse(shell.isGranted)
    }

    @Test fun deniedPermissionDoesNotEnableCommands() {
        var invocations = 0
        val shell = RootShell { _, _ -> invocations++; ShellResult(1) }
        assertFalse(shell.requestPermission())
        assertFalse(shell.execute("settings put system show_touches 1"))
        assertEquals(1, invocations)
    }

    @Test fun missingSuAndTimeoutAreHandledAsUnavailable() {
        val shell = RootShell { _, _ -> ShellResult(null) }
        assertFalse(shell.requestPermission())
        assertFalse(shell.isGranted)
    }

    @Test fun malformedUidOutputIsNotAccepted() {
        for (output in listOf("", "root", "0\n2000", "uid=0(root)")) {
            val shell = RootShell { _, _ -> ShellResult(0, output) }
            assertFalse(shell.requestPermission())
        }
    }

    @Test fun commandsNeverRequestRootImplicitly() {
        val shell = RootShell { _, _ -> fail("Unexpected su invocation"); ShellResult(0, "0") }
        assertFalse(shell.execute("settings put system show_touches 1"))
    }

    @Test fun approvedCommandIsPassedAsOneArgumentWithShortTimeout() {
        val calls = mutableListOf<Pair<List<String>, Long>>()
        val shell = RootShell { args, timeout ->
            calls.add(args to timeout)
            ShellResult(0, if (args.last() == "id -u") "0" else "")
        }
        shell.requestPermission()
        val command = "settings put global policy_control 'immersive.full=*'"
        assertTrue(shell.execute(command))
        assertEquals(listOf("su", "-c", command) to 5L, calls.last())
    }

    @Test fun unsupportedCommandDoesNotRevokeExistingAuthorization() {
        var result = ShellResult(0, "0")
        val shell = RootShell { _, _ -> result }
        shell.requestPermission()
        result = ShellResult(1)
        assertFalse(shell.execute("unsupported-command"))
        assertTrue(shell.isGranted)
    }

    @Test fun timedOutCommandDisablesFurtherExecutionUntilRetry() {
        var result = ShellResult(0, "0")
        var calls = 0
        val shell = RootShell { _, _ -> calls++; result }
        shell.requestPermission()
        result = ShellResult(null)
        assertFalse(shell.execute("settings put system show_touches 1"))
        assertFalse(shell.isGranted)
        assertFalse(shell.execute("settings put system show_touches 0"))
        assertEquals(2, calls)
        result = ShellResult(0, "0")
        assertTrue(shell.requestPermission())
        assertTrue(shell.execute("settings put system show_touches 0"))
    }

    @Test fun revokedAuthorizationClearsPreviousGrant() {
        var allowed = true
        val shell = RootShell { _, _ -> if (allowed) ShellResult(0, "0") else ShellResult(1) }
        assertTrue(shell.requestPermission())
        allowed = false
        assertFalse(shell.requestPermission())
        assertFalse(shell.isGranted)
    }
}
