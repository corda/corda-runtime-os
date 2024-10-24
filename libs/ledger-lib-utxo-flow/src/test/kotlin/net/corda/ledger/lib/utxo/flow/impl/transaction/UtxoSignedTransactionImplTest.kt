package net.corda.ledger.lib.utxo.flow.impl.transaction

import net.corda.crypto.impl.CompositeKeyProviderImpl
import net.corda.ledger.common.testkit.anotherPublicKeyExample
import net.corda.ledger.common.testkit.getSignatureWithMetadataExample
import net.corda.ledger.utxo.test.UtxoLedgerTest
import net.corda.ledger.utxo.testkit.UtxoCommandExample
import net.corda.ledger.utxo.testkit.getUtxoStateExample
import net.corda.ledger.utxo.testkit.utxoTimeWindowExample
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.membership.NotaryInfo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec

internal class UtxoSignedTransactionImplTest : UtxoLedgerTest() {
    private lateinit var signedTransaction: UtxoSignedTransactionInternal

    private val kpg: KeyPairGenerator = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }
    private val notaryNode1PublicKey = kpg.generateKeyPair().public.also { println(it) }
    private val notaryNode2PublicKey = kpg.generateKeyPair().public.also { println(it) }
    private val notaryKey =
        CompositeKeyProviderImpl().createFromKeys(listOf(notaryNode1PublicKey, notaryNode2PublicKey), 1).also { println(it) }
    private val notaryX500Name = MemberX500Name.parse("O=ExampleNotaryService, L=London, C=GB")
    private val notary = notaryX500Name

    @BeforeEach
    fun beforeEach() {
        val notaryInfo = mock<NotaryInfo>().also {
            whenever(it.name).thenReturn(notaryX500Name)
            whenever(it.publicKey).thenReturn(notaryKey)
        }
        whenever(mockNotaryLookup.lookup(notaryX500Name)).thenReturn(notaryInfo)
        signedTransaction = UtxoTransactionBuilderImpl(
            utxoSignedTransactionFactory,
            mockNotaryLookup
        )
            .setNotary(notary)
            .setTimeWindowBetween(utxoTimeWindowExample.from, utxoTimeWindowExample.until)
            .addOutputState(getUtxoStateExample())
            .addSignatories(listOf(anotherPublicKeyExample))
            .addCommand(UtxoCommandExample())
            .toSignedTransaction() as UtxoSignedTransactionInternal
    }

    @Test
    fun `receiving notary signature with key id not matching notary key throws`() {
        val notExistingNotaryKey = kpg.generateKeyPair().public
        val notMatchingSignatureKeyId = getSignatureWithMetadataExample(notExistingNotaryKey)
        assertThrows<CordaRuntimeException> {
            signedTransaction.verifyNotarySignature(notMatchingSignatureKeyId)
        }
    }
}
