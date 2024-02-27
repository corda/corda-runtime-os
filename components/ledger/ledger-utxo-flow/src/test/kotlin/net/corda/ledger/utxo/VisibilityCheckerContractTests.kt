package net.corda.ledger.utxo

import net.corda.v5.application.crypto.SigningService
import net.corda.v5.ledger.utxo.Contract
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.security.PublicKey
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VisibilityCheckerContractTests {

    private val aliceKey1 = mock<PublicKey>()
    private val bobKey1 = mock<PublicKey>()

    private val bobOnlyLedgerKeys = setOf(bobKey1)
    private val aliceAndBobLedgerKeys = setOf(aliceKey1, bobKey1)
    private val aliceLedgerKeyMap = mapOf(aliceKey1 to aliceKey1)

    private val aliceSigningService = mock<SigningService>()

    @BeforeEach
    fun setup() {
        whenever(aliceSigningService.findMySigningKeys(aliceAndBobLedgerKeys)).thenReturn(aliceLedgerKeyMap)
        whenever(aliceSigningService.findMySigningKeys(bobOnlyLedgerKeys)).thenReturn(emptyMap())
    }

    @Test
    fun `Contract_isVisible should return true when alice is a participant`() {
        // Arrange
        val state = TestUtxoContractState(aliceAndBobLedgerKeys)
        val contract = TestUtxoContract()
        val visibilityChecker = VisibilityCheckerImpl(aliceSigningService)

        // Act
        val actual = contract.isVisible(state, visibilityChecker)

        // Assert
        assertTrue(actual)
    }

    @Test
    fun `Contract_isVisible should return false when alice is not a participant`() {
        // Arrange
        val state = TestUtxoContractState(bobOnlyLedgerKeys)
        val contract = TestUtxoContract()
        val visibilityChecker = VisibilityCheckerImpl(aliceSigningService)

        // Act
        val actual = contract.isVisible(state, visibilityChecker)

        // Assert
        assertFalse(actual)
    }

    private class TestUtxoContractState(private val participants: Set<PublicKey>) : ContractState {
        override fun getParticipants(): List<PublicKey> = participants.toList()
    }

    private class TestUtxoContract : Contract {
        override fun verify(transaction: UtxoLedgerTransaction) = Unit
    }
}
