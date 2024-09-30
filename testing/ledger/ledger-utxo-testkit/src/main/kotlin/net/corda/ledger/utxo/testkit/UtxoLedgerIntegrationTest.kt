package net.corda.ledger.utxo.testkit

import net.corda.ledger.common.integration.test.CommonLedgerIntegrationTest
import net.corda.ledger.lib.utxo.flow.impl.transaction.UtxoSignedTransactionInternal
import net.corda.ledger.lib.utxo.flow.impl.transaction.factory.UtxoSignedTransactionFactory
import net.corda.ledger.utxo.data.transaction.UtxoLedgerTransactionImpl
import net.corda.ledger.utxo.data.transaction.UtxoLedgerTransactionInternal
import net.corda.ledger.utxo.data.transaction.WrappedUtxoWireTransaction
import net.corda.ledger.utxo.flow.impl.persistence.UtxoLedgerPersistenceService
import net.corda.sandboxgroupcontext.getSandboxSingletonService
import net.corda.testing.sandboxes.SandboxSetup
import net.corda.v5.ledger.utxo.UtxoLedgerService

abstract class UtxoLedgerIntegrationTest : CommonLedgerIntegrationTest() {
    override val testingCpb = "/META-INF/ledger-utxo-state-app.cpb"

    lateinit var utxoSignedTransactionFactory: UtxoSignedTransactionFactory
    lateinit var utxoLedgerService: UtxoLedgerService
    lateinit var utxoLedgerPersistenceService: UtxoLedgerPersistenceService
    lateinit var utxoSignedTransaction: UtxoSignedTransactionInternal
    lateinit var utxoLedgerTransaction: UtxoLedgerTransactionInternal

    override fun initialize(setup: SandboxSetup) {
        super.initialize(setup)

        utxoSignedTransactionFactory = sandboxGroupContext.getSandboxSingletonService()
        utxoLedgerService = sandboxGroupContext.getSandboxSingletonService()
        utxoLedgerPersistenceService = sandboxGroupContext.getSandboxSingletonService()
        utxoSignedTransaction = utxoSignedTransactionFactory.createExample(
            jsonMarshallingService,
            jsonValidator,
            wireTransactionFactory
        )
        utxoLedgerTransaction = UtxoLedgerTransactionImpl(
            WrappedUtxoWireTransaction(utxoSignedTransaction.wireTransaction, serializationService),
            emptyList(),
            emptyList(),
            null
        )
    }
}
