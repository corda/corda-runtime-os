package net.corda.ledger.utxo.impl

import net.corda.v5.cipher.suite.DigestService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito

class AutomaticPlaceholderCpkConstraintTests {
    private val digestService = Mockito.mock(DigestService::class.java)
    private val constraintContext = Mockito.mock(CpkConstraintContext::class.java)

    @Test
    fun `AutomaticPlaceholderCpkConstraint isSatisfiedBy should throw the expected exception`() {
        val exception = assertThrows(java.lang.UnsupportedOperationException::class.java) {
            CpkConstraint.Companion.AutomaticPlaceholderCpkConstraint.isSatisfiedBy(digestService, constraintContext)
        }

        assertEquals("Contracts cannot be satisfied by AutomaticPlaceholderCpkConstraint.", exception.message)
    }
}
