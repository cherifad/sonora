package com.adlen.sonora.app.data.preferences

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockRuleTest {
    @Test fun locksOnlyWhenAskedForAndThePhoneCanProveWhoHoldsIt() {
        assertTrue(LockRule.wanted(enabled = true, deviceSecure = true, leftApp = true))
    }

    @Test fun neverWithoutAScreenLockOrAfterTheUserTurnedItOff() {
        assertFalse(LockRule.wanted(enabled = false, deviceSecure = true, leftApp = true))
        assertFalse(LockRule.wanted(enabled = true, deviceSecure = false, leftApp = true))
    }

    @Test fun notWhileTheUserIsStillInTheApp() {
        assertFalse(LockRule.wanted(enabled = true, deviceSecure = true, leftApp = false))
    }
}
