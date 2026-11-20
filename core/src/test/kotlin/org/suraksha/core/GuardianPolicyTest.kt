package org.suraksha.core

import org.junit.Assert.*
import org.junit.Test

class GuardianPolicyTest {
    @Test fun firstAttemptAndExactBoundaryAreAllowed() {
        assertTrue(GuardianPolicy.canAttempt(0, null))
        assertFalse(GuardianPolicy.canAttempt(0, 0))
        assertFalse(GuardianPolicy.canAttempt(GuardianPolicy.WINDOW_MS - 1, 0))
        assertTrue(GuardianPolicy.canAttempt(GuardianPolicy.WINDOW_MS, 0))
    }
    @Test fun clockRollbackCannotCreateAnSmsBurst() {
        assertFalse(GuardianPolicy.canAttempt(900, 1000))
        assertFalse(GuardianPolicy.canAttempt(-1, null))
        assertFalse(GuardianPolicy.canAttempt(1100, 1000))
    }
    @Test fun phoneFormattingIsNormalizedWithoutChangingTheDestination() {
        assertEquals("+919876543210", GuardianPolicy.normalizeNumber("+91 (98765) 43210"))
        assertEquals("9876543210", GuardianPolicy.normalizeNumber("98765-43210"))
    }
    @Test fun dialCodesExtensionsAndMalformedNumbersAreRejected() {
        for (number in listOf("*123#", "tel:+911234567890", "+919876543210;123", "1234567x89",
            "91+9876543210", "++919876543210", "123", "0000000", "+1234567890123456")) {
            assertNull(number, GuardianPolicy.normalizeNumber(number))
        }
    }
}
