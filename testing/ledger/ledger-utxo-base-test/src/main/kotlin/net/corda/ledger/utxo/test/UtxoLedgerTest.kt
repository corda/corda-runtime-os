package net.corda.ledger.utxo.test

import net.corda.flow.pipeline.sandbox.FlowSandboxService
import net.corda.ledger.common.flow.impl.transaction.filtered.factory.FilteredTransactionFactoryImpl
import net.corda.ledger.common.test.CommonLedgerTest
import net.corda.ledger.common.testkit.mockTransactionSignatureService
import net.corda.ledger.utxo.flow.impl.UtxoLedgerServiceImpl
import net.corda.ledger.utxo.flow.impl.persistence.UtxoLedgerPersistenceService
import net.corda.ledger.utxo.flow.impl.persistence.UtxoLedgerStateQueryService
import net.corda.ledger.utxo.flow.impl.transaction.UtxoTransactionBuilderImpl
import net.corda.ledger.utxo.flow.impl.transaction.factory.impl.UtxoLedgerTransactionFactoryImpl
import net.corda.ledger.utxo.flow.impl.transaction.filtered.factory.UtxoFilteredTransactionFactoryImpl
import net.corda.ledger.utxo.flow.impl.transaction.factory.impl.UtxoSignedTransactionFactoryImpl
import net.corda.ledger.utxo.flow.impl.transaction.serializer.amqp.UtxoSignedTransactionSerializer
import net.corda.ledger.utxo.flow.impl.transaction.serializer.kryo.UtxoSignedTransactionKryoSerializer
import net.corda.ledger.utxo.flow.impl.transaction.verifier.UtxoLedgerTransactionVerificationService
import net.corda.ledger.utxo.testkit.getUtxoSignedTransactionExample
import net.corda.sandboxgroupcontext.CurrentSandboxGroupContext
import net.corda.v5.ledger.common.NotaryLookup
import org.mockito.kotlin.mock

abstract class UtxoLedgerTest : CommonLedgerTest() {
    val mockUtxoLedgerPersistenceService = mock<UtxoLedgerPersistenceService>()
    val mockUtxoLedgerTransactionVerificationService = mock<UtxoLedgerTransactionVerificationService>()
    val mockUtxoLedgerStateQueryService = mock<UtxoLedgerStateQueryService>()
    val mockCurrentSandboxGroupContext = mock<CurrentSandboxGroupContext>()
    val mockFlowSandboxService = mock<FlowSandboxService>()
    val mockNotaryLookup = mock<NotaryLookup>()
    private val utxoFilteredTransactionFactory = UtxoFilteredTransactionFactoryImpl(
        FilteredTransactionFactoryImpl(
            jsonMarshallingService,
            merkleTreeProvider,
            serializationServiceWithWireTx
        ), serializationServiceWithWireTx
    )
    private val utxoLedgerTransactionFactory = UtxoLedgerTransactionFactoryImpl(
        serializationServiceWithWireTx,
        mockUtxoLedgerStateQueryService
    )
    val utxoSignedTransactionFactory = UtxoSignedTransactionFactoryImpl(
        currentSandboxGroupContext,
        jsonMarshallingService,
        jsonValidator,
        serializationServiceNullCfg,
        mockTransactionSignatureService(),
        transactionMetadataFactory,
        wireTransactionFactory,
        utxoLedgerTransactionFactory,
        mockUtxoLedgerTransactionVerificationService
    )
    val utxoLedgerService = UtxoLedgerServiceImpl(
        utxoFilteredTransactionFactory,
        utxoSignedTransactionFactory,
        flowEngine,
        mockUtxoLedgerPersistenceService,
        mockUtxoLedgerStateQueryService,
        mockCurrentSandboxGroupContext,
        mockFlowSandboxService,
        mockNotaryLookup
    )
    val utxoSignedTransactionKryoSerializer = UtxoSignedTransactionKryoSerializer(
        serializationServiceWithWireTx,
        mockTransactionSignatureService(),
        utxoLedgerTransactionFactory
    )
    val utxoSignedTransactionAMQPSerializer =
        UtxoSignedTransactionSerializer(
            serializationServiceNullCfg,
            mockTransactionSignatureService(),
            utxoLedgerTransactionFactory
        )
    val utxoSignedTransactionExample = getUtxoSignedTransactionExample(
        digestService,
        merkleTreeProvider,
        serializationServiceWithWireTx,
        jsonMarshallingService,
        jsonValidator,
        mockTransactionSignatureService(),
        utxoLedgerTransactionFactory
    )
    
    // This is the only not stateless.
    val utxoTransactionBuilder = UtxoTransactionBuilderImpl(utxoSignedTransactionFactory)
}
