package net.corda.ledger.consensual.testkit

import net.corda.ledger.common.testkit.CommonLedgerIntegrationTest
import net.corda.ledger.consensual.flow.impl.transaction.factory.ConsensualSignedTransactionFactory
import net.corda.sandboxgroupcontext.getSandboxSingletonService
import net.corda.testing.sandboxes.SandboxSetup
import net.corda.v5.ledger.consensual.ConsensualLedgerService
import net.corda.v5.ledger.consensual.transaction.ConsensualSignedTransaction

abstract class ConsensualLedgerIntegrationTest: CommonLedgerIntegrationTest() {
    lateinit var consensualSignedTransactionFactory: ConsensualSignedTransactionFactory
    lateinit var consensualLedgerService: ConsensualLedgerService
    lateinit var consensualSignedTransaction: ConsensualSignedTransaction

    override fun initialize(setup: SandboxSetup){
        super.initialize(setup)

        consensualSignedTransactionFactory = sandboxGroupContext.getSandboxSingletonService()
        consensualLedgerService = sandboxGroupContext.getSandboxSingletonService()
        consensualSignedTransaction = consensualSignedTransactionFactory.createExample(
            jsonMarshallingService,
            wireTransactionFactory,
            consensualSignedTransactionFactory
        )
    }
}