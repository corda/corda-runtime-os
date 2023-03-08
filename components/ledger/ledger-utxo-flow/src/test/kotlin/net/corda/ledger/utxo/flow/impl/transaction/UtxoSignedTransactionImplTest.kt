package net.corda.ledger.utxo.flow.impl.transaction

import net.corda.ledger.common.testkit.getSignatureWithMetadataExample
import net.corda.ledger.common.testkit.publicKeyExample
import net.corda.ledger.utxo.test.UtxoLedgerTest
import net.corda.ledger.utxo.testkit.UtxoCommandExample
import net.corda.ledger.utxo.testkit.getUtxoStateExample
import net.corda.ledger.utxo.testkit.utxoTimeWindowExample
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.common.transaction.TransactionSignatureException
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec

internal class UtxoSignedTransactionImplTest: UtxoLedgerTest() {
    private lateinit var signedTransaction: UtxoSignedTransactionInternal

    private val kpg: KeyPairGenerator = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }
    private val notaryKey = kpg.generateKeyPair().public
    private val notaryX500Name = MemberX500Name.parse("O=ExampleNotaryService, L=London, C=GB")
    private val notary = Party(notaryX500Name, notaryKey)

    @BeforeEach
    fun beforeEach() {
        signedTransaction = UtxoTransactionBuilderImpl(
            utxoSignedTransactionFactory
        )
            .setNotary(notary)
            .setTimeWindowBetween(utxoTimeWindowExample.from, utxoTimeWindowExample.until)
            .addOutputState(getUtxoStateExample())
            .addSignatories(listOf(publicKeyExample))
            .addCommand(UtxoCommandExample())
            .toSignedTransaction() as UtxoSignedTransactionInternal

    }

    @Test
    fun `verifyNotarySignatureAttached throws on unnotarized transaction`() {
        Assertions.assertThatThrownBy { signedTransaction.verifyNotarySignatureAttached() }.isInstanceOf(
            TransactionSignatureException::class.java)
            .hasMessageContainingAll("There are no notary signatures attached to the transaction.")

    }

    @Test
    fun `verifyNotarySignatureAttached does not throw on notarized transaction`() {
        signedTransaction = signedTransaction.addSignature(getSignatureWithMetadataExample(notaryKey))
        assertDoesNotThrow {
            signedTransaction.verifyNotarySignatureAttached()
        }
    }
}
