package net.corda.ledger.utxo.test

import net.corda.flow.external.events.executor.ExternalEventExecutor
import net.corda.flow.pipeline.sandbox.FlowSandboxService
import net.corda.ledger.common.flow.impl.transaction.filtered.factory.FilteredTransactionFactoryImpl
import net.corda.ledger.common.test.CommonLedgerTest
import net.corda.ledger.common.testkit.anotherPublicKeyExample
import net.corda.ledger.common.testkit.mockTransactionSignatureService
import net.corda.ledger.common.testkit.publicKeyExample
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
import net.corda.ledger.utxo.testkit.anotherNotaryX500Name
import net.corda.ledger.utxo.testkit.getUtxoSignedTransactionExample
import net.corda.ledger.utxo.testkit.notaryX500Name
import net.corda.sandboxgroupcontext.CurrentSandboxGroupContext
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.ledger.common.NotaryLookup
import net.corda.v5.membership.NotaryInfo
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

abstract class UtxoLedgerTest : CommonLedgerTest() {
    val mockUtxoLedgerPersistenceService = mock<UtxoLedgerPersistenceService>()
    val mockUtxoLedgerTransactionVerificationService = mock<UtxoLedgerTransactionVerificationService>()
    val mockUtxoLedgerStateQueryService = mock<UtxoLedgerStateQueryService>()
    val mockCurrentSandboxGroupContext = mock<CurrentSandboxGroupContext>()
    val mockFlowSandboxService = mock<FlowSandboxService>()
    val mockExternalEventExecutor = mock<ExternalEventExecutor>()
    val mockSerializationService = mock<SerializationService>()


    val mockNotaryLookup = mock<NotaryLookup>().also{
        val notaryExampleInfo = mock<NotaryInfo>().also {
            whenever(it.name).thenReturn(notaryX500Name)
            whenever(it.publicKey).thenReturn(publicKeyExample)
        }

        val anotherNotaryExampleInfo = mock<NotaryInfo>().also {
            whenever(it.name).thenReturn(anotherNotaryX500Name)
            whenever(it.publicKey).thenReturn(anotherPublicKeyExample)
        }

        whenever(it.lookup(notaryX500Name)).thenReturn(notaryExampleInfo)
        whenever(it.lookup(anotherNotaryX500Name)).thenReturn(anotherNotaryExampleInfo)
    }

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
        mockNotaryLookup,
        mockExternalEventExecutor,
        mockSerializationService
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
    val utxoTransactionBuilder = UtxoTransactionBuilderImpl(utxoSignedTransactionFactory, mockNotaryLookup)
}
