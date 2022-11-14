package net.corda.ledger.utxo.testkit

import net.corda.ledger.common.testkit.CommonLedgerIntegrationTest
import net.corda.ledger.utxo.flow.impl.transaction.factory.UtxoSignedTransactionFactory
import net.corda.sandboxgroupcontext.getSandboxSingletonService
import net.corda.testing.sandboxes.SandboxSetup
import net.corda.v5.ledger.utxo.UtxoLedgerService
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction

abstract class UtxoLedgerIntegrationTest: CommonLedgerIntegrationTest() {
    override val testingCpb = "/META-INF/ledger-utxo-state-app.cpb"

    lateinit var utxoSignedTransactionFactory: UtxoSignedTransactionFactory
    lateinit var utxoLedgerService: UtxoLedgerService
    lateinit var utxoSignedTransaction: UtxoSignedTransaction

    override fun initialize(setup: SandboxSetup){
        super.initialize(setup)

        utxoSignedTransactionFactory = sandboxGroupContext.getSandboxSingletonService()
        utxoLedgerService = sandboxGroupContext.getSandboxSingletonService()
        utxoSignedTransaction = utxoSignedTransactionFactory.createExample(
            jsonMarshallingService,
            wireTransactionFactory,
            utxoSignedTransactionFactory
        )
    }
}