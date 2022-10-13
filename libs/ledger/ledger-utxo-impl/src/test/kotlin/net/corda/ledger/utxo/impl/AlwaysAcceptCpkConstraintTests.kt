package net.corda.ledger.utxo.impl

import net.corda.v5.cipher.suite.DigestService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito

class AlwaysAcceptCpkConstraintTests {

    private val digestService = Mockito.mock(DigestService::class.java)
    private val constraintContext = Mockito.mock(CpkConstraintContext::class.java)

    @Test
    fun `AlwaysAcceptCpkConstraint isSatisfiedBy should return the expected result`() {
        val value = CpkConstraint.Companion.AlwaysAcceptCpkConstraint.isSatisfiedBy(digestService, constraintContext)
        assertEquals(true, value)
    }
}