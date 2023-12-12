package net.corda.ledger.utxo

import net.corda.v5.application.crypto.SigningService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.security.PublicKey
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VisibilityCheckerImplTests {

    private val aliceKey1 = mock<PublicKey>()
    private val aliceKey2 = mock<PublicKey>()
    private val bobKey1 = mock<PublicKey>()
    private val bobKey2 = mock<PublicKey>()

    private val aliceLedgerKeys = setOf(aliceKey1, aliceKey2)
    private val bobLedgerKeys = setOf(bobKey1, bobKey2)
    private val allLedgerKeys = aliceLedgerKeys + bobLedgerKeys

    private val aliceLedgerKeyMap = aliceLedgerKeys.associateWith { it }
    private val aliceSigningService = mock<SigningService>()

    @BeforeEach
    fun setup() {
        whenever(aliceSigningService.findMySigningKeys(aliceLedgerKeys)).thenReturn(aliceLedgerKeyMap)
        whenever(aliceSigningService.findMySigningKeys(allLedgerKeys)).thenReturn(aliceLedgerKeyMap)
        whenever(aliceSigningService.findMySigningKeys(bobLedgerKeys)).thenReturn(emptyMap())
    }

    @Test
    fun `VisibilityChecker_containsMySigningKeys should return true when all specified keys belong to Alice`() {
        // Arrange
        val sut = VisibilityCheckerImpl(aliceSigningService)

        // Act
        val actual = sut.containsMySigningKeys(aliceLedgerKeys)

        // Assert
        assertTrue(actual)
    }

    @Test
    fun `VisibilityChecker_containsMySigningKeys should return true when any specified keys belong to Alice`() {
        // Arrange
        val sut = VisibilityCheckerImpl(aliceSigningService)

        // Act
        val actual = sut.containsMySigningKeys(allLedgerKeys)

        // Assert
        assertTrue(actual)
    }

    @Test
    fun `VisibilityChecker_containsMySigningKeys should return true when no specified keys belong to Alice`() {
        // Arrange
        val sut = VisibilityCheckerImpl(aliceSigningService)

        // Act
        val actual = sut.containsMySigningKeys(bobLedgerKeys)

        // Assert
        assertFalse(actual)
    }
}
