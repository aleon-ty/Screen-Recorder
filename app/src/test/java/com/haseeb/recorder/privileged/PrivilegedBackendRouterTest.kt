package com.haseeb.recorder.privileged

import org.junit.Assert.*
import org.junit.Test

class PrivilegedBackendRouterTest {
    @Test fun selectedRootWorksWithoutShizuku() {
        val commands = mutableListOf<String>()
        val router = PrivilegedBackendRouter({ true }, { false },
            { commands.add(it); true }, { fail("Shizuku must not be used"); false })
        assertTrue(router.isGranted(true))
        assertTrue(router.execute(true, "settings put system show_touches 1"))
        assertEquals(listOf("settings put system show_touches 1"), commands)
    }

    @Test fun selectedShizukuDoesNotTryRoot() {
        val router = PrivilegedBackendRouter({ fail("Root must not be probed"); false }, { true },
            { fail("Root must not be used"); false }, { true })
        assertTrue(router.execute(false, "settings delete global policy_control"))
    }

    @Test fun deniedRootDoesNotFallBackToShizuku() {
        val router = PrivilegedBackendRouter({ false }, { true },
            { fail("Unauthorized root command"); false }, { fail("Implicit Shizuku fallback"); false })
        assertFalse(router.isGranted(true))
        assertFalse(router.execute(true, "settings put system show_touches 1"))
    }

    @Test fun failedRootCommandDoesNotSwitchBackend() {
        val router = PrivilegedBackendRouter({ true }, { true },
            { false }, { fail("Implicit fallback after root failure"); false })
        assertFalse(router.execute(true, "unsupported-command"))
    }

    @Test fun disconnectedShizukuDoesNotRequestRoot() {
        val router = PrivilegedBackendRouter({ true }, { false },
            { fail("Unexpected root access"); false }, { fail("Disconnected Shizuku command"); false })
        assertFalse(router.execute(false, "settings put system show_touches 1"))
    }
}
