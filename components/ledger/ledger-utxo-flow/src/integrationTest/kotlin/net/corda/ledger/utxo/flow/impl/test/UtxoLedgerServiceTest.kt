package net.corda.ledger.utxo.flow.impl.test

import net.corda.ledger.common.testkit.anotherPublicKeyExample
import net.corda.ledger.common.testkit.publicKeyExample
import net.corda.ledger.utxo.testkit.UtxoLedgerIntegrationTest
import net.corda.sandboxgroupcontext.getSandboxSingletonService
import net.corda.testing.sandboxes.SandboxSetup
import net.corda.v5.application.crypto.CompositeKeyGenerator
import net.corda.v5.crypto.CompositeKeyNodeAndWeight
import net.corda.v5.ledger.utxo.transaction.UtxoTransactionBuilder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class UtxoLedgerServiceTest : UtxoLedgerIntegrationTest() {

    lateinit var compositeKeyGenerator: CompositeKeyGenerator
    override fun initialize(setup: SandboxSetup) {
        super.initialize(setup)
        compositeKeyGenerator = sandboxGroupContext.getSandboxSingletonService()
    }

    @Test
    @Suppress("FunctionName")
    fun `createTransactionBuilder should return a Transaction Builder`() {
        val transactionBuilder = utxoLedgerService.createTransactionBuilder()
        assertThat(transactionBuilder).isInstanceOf(UtxoTransactionBuilder::class.java)
    }

    @Test
    fun `Can use composite Keys`() {
        val transactionBuilder = utxoLedgerService.createTransactionBuilder()
        val aliceKey = publicKeyExample
        val bobKey = anotherPublicKeyExample
        val compositeKey = compositeKeyGenerator.create(
            listOf(
                CompositeKeyNodeAndWeight(aliceKey, 1),
                CompositeKeyNodeAndWeight(bobKey, 1),
            ),
            1
        )
        transactionBuilder
            .addSignatories(listOf(aliceKey, bobKey))
            .addSignatories(compositeKey)
    }
}
