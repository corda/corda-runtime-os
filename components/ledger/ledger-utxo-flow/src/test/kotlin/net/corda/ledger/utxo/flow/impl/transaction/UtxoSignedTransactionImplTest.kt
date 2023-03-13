package net.corda.ledger.utxo.flow.impl.transaction

import net.corda.crypto.impl.CompositeKeyProviderImpl
import net.corda.ledger.common.testkit.getSignatureWithMetadataExample
import net.corda.ledger.common.testkit.publicKeyExample
import net.corda.ledger.utxo.test.UtxoLedgerTest
import net.corda.ledger.utxo.testkit.UtxoCommandExample
import net.corda.ledger.utxo.testkit.getUtxoStateExample
import net.corda.ledger.utxo.testkit.utxoTimeWindowExample
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.KeyUtils
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
    private val notaryNode1PublicKey = kpg.generateKeyPair().public.also{println(it)}
    private val notaryNode2PublicKey = kpg.generateKeyPair().public.also{println(it)}
    private val notaryKey =
        CompositeKeyProviderImpl().createFromKeys(listOf(notaryNode1PublicKey, notaryNode2PublicKey), 1).also{println(it)}
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
            .hasMessageContaining("There are no notary")

    }

    @Test
    fun `verifyNotarySignatureAttached does not throw on notarized transaction`() {
        val sig = getSignatureWithMetadataExample(notaryNode1PublicKey)
        println(sig.by)
        signedTransaction = signedTransaction.addSignature(sig)

        val publicKeys = signedTransaction.signatures.map { it.by }

        println("notary.owningKey: ${notary.owningKey}")
        println("signedTransaction.notary.owningKey: ${signedTransaction.notary.owningKey}")

        if (KeyUtils.isKeyInSet(notary.owningKey, publicKeys)) {
            println("KeyUtils.isKeyInSet(notary.owningKey, publicKeys) passed")
        } else {
            println("KeyUtils.isKeyInSet(notary.owningKey, publicKeys) not passed")
        }
        if (KeyUtils.isKeyFulfilledBy(notary.owningKey, publicKeys)) {
            println("KeyUtils.isKeyFulfilledBy(notary.owningKey, publicKeys) passed")
        } else {
            println("KeyUtils.isKeyFulfilledBy(notary.owningKey, publicKeys) not passed")
        }
        if (notary.owningKey == signedTransaction.notary.owningKey) {
            println("notary.owningKey  == signedTransaction.notary.owningKey passed")
        } else {
            println("notary.owningKey  == signedTransaction.notary.owningKey not passed")
        }
        if (KeyUtils.isKeyInSet(signedTransaction.notary.owningKey, publicKeys)) {
            println("KeyUtils.isKeyInSet(signedTransaction.notary.owningKey, publicKeys) passed")
        } else {
            println("KeyUtils.isKeyInSet(signedTransaction.notary.owningKey, publicKeys) not passed")
        }
        if (KeyUtils.isKeyFulfilledBy(signedTransaction.notary.owningKey, publicKeys)) {
            println("KeyUtils.isKeyFulfilledBy(signedTransaction.notary.owningKey, publicKeys) passed")
        } else {
            println("KeyUtils.isKeyFulfilledBy(signedTransaction.notary.owningKey, publicKeys) not passed")
        }

        assertDoesNotThrow {
            signedTransaction.verifyNotarySignatureAttached()
        }
    }
}
