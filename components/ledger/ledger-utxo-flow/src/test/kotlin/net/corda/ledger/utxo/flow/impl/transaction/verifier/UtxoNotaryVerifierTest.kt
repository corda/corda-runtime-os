package net.corda.ledger.utxo.flow.impl.transaction.verifier

import net.corda.crypto.core.parseSecureHash
import net.corda.ledger.common.data.transaction.TransactionMetadataInternal
import net.corda.ledger.common.testkit.anotherPublicKeyExample
import net.corda.ledger.common.testkit.publicKeyExample
import net.corda.ledger.utxo.testkit.anotherNotaryX500Name
import net.corda.ledger.utxo.testkit.notaryX500Name
import net.corda.membership.lib.SignedGroupParameters
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.membership.NotaryInfo
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class UtxoNotaryVerifierTest {

    private val signedGroupParameters = mock<SignedGroupParameters>()
    private val metadata = mock<TransactionMetadataInternal>()
    private val hash = parseSecureHash("XXX-9:123456")
    private val notary1 = mock<NotaryInfo>()
    private val notary2 = mock<NotaryInfo>()

    @BeforeEach
    fun beforeEach() {
        whenever(metadata.getMembershipGroupParametersHash()).thenReturn(hash.toString())
        whenever(signedGroupParameters.hash).thenReturn(hash)

        whenever(notary1.name).thenReturn(anotherNotaryX500Name)
        whenever(notary1.publicKey).thenReturn(anotherPublicKeyExample)
        whenever(notary2.name).thenReturn(MemberX500Name.parse("O=ThirdExampleNotaryService, L=London, C=GB"))
        whenever(notary2.publicKey).thenReturn(mock())

        whenever(signedGroupParameters.notaries).thenReturn(listOf(notary1, notary2))
    }

    @Test
    fun `verifyNotaryAllowed throws if tx have different group parameters than the received`() {
        whenever(metadata.getMembershipGroupParametersHash()).thenReturn("YYY:9876")
        assertThatThrownBy { verifyNotaryAllowed(notaryX500Name, publicKeyExample, metadata, signedGroupParameters) }
            .isExactlyInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("is not the one associated to the transaction")
    }

    @Test
    fun `verifyNotaryAllowed throws if no keys or names matches`() {
        assertThatThrownBy { verifyNotaryAllowed(notaryX500Name, publicKeyExample, metadata, signedGroupParameters) }
            .isExactlyInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("is not listed in the available notaries.")
    }

    @Test
    fun `verifyNotaryAllowed throws if only key matches, but not the name`() {
        whenever(notary1.publicKey).thenReturn(publicKeyExample)
        assertThatThrownBy { verifyNotaryAllowed(notaryX500Name, publicKeyExample, metadata, signedGroupParameters) }
            .isExactlyInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("is not listed in the available notaries.")
    }

    @Test
    fun `verifyNotaryAllowed throws if only name matches, but not the key`() {
        whenever(notary1.name).thenReturn(notaryX500Name)
        assertThatThrownBy { verifyNotaryAllowed(notaryX500Name, publicKeyExample, metadata, signedGroupParameters) }
            .isExactlyInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("is not matching against the related notary")
    }

    @Test
    fun `verifyNotaryAllowed does not throw if both key and name matches`() {
        whenever(notary1.name).thenReturn(notaryX500Name)
        whenever(notary1.publicKey).thenReturn(publicKeyExample)
        assertDoesNotThrow { verifyNotaryAllowed(notaryX500Name, publicKeyExample, metadata, signedGroupParameters) }
    }
}
