package net.corda.ledger.utxo.test

import net.corda.flow.external.events.executor.ExternalEventExecutor
import net.corda.flow.persistence.query.ResultSetFactory
import net.corda.flow.pipeline.sandbox.FlowSandboxService
import net.corda.ledger.common.flow.impl.transaction.filtered.factory.FilteredTransactionFactoryImpl
import net.corda.ledger.common.test.CommonLedgerTest
import net.corda.ledger.common.testkit.anotherPublicKeyExample
import net.corda.ledger.common.testkit.fakeTransactionSignatureService
import net.corda.ledger.common.testkit.publicKeyExample
import net.corda.ledger.utxo.flow.impl.UtxoLedgerServiceImpl
import net.corda.ledger.utxo.flow.impl.persistence.UtxoLedgerGroupParametersPersistenceService
import net.corda.ledger.utxo.flow.impl.persistence.UtxoLedgerPersistenceService
import net.corda.ledger.utxo.flow.impl.persistence.UtxoLedgerStateQueryService
import net.corda.ledger.utxo.flow.impl.transaction.UtxoTransactionBuilderImpl
import net.corda.ledger.utxo.flow.impl.transaction.factory.impl.UtxoLedgerTransactionFactoryImpl
import net.corda.ledger.utxo.flow.impl.transaction.factory.impl.UtxoSignedTransactionFactoryImpl
import net.corda.ledger.utxo.flow.impl.transaction.filtered.factory.UtxoFilteredTransactionFactoryImpl
import net.corda.ledger.utxo.flow.impl.transaction.serializer.amqp.UtxoSignedTransactionSerializer
import net.corda.ledger.utxo.flow.impl.transaction.serializer.kryo.UtxoSignedTransactionKryoSerializer
import net.corda.ledger.utxo.flow.impl.transaction.verifier.UtxoLedgerTransactionVerificationService
import net.corda.ledger.utxo.testkit.anotherNotaryX500Name
import net.corda.ledger.utxo.testkit.getUtxoSignedTransactionExample
import net.corda.ledger.utxo.testkit.notaryX500Name
import net.corda.ledger.utxo.flow.impl.groupparameters.verifier.SignedGroupParametersVerifier
import net.corda.sandboxgroupcontext.CurrentSandboxGroupContext
import net.corda.v5.ledger.common.NotaryLookup
import net.corda.v5.membership.NotaryInfo
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

abstract class UtxoLedgerTest : CommonLedgerTest() {
    private val mockUtxoLedgerPersistenceService = mock<UtxoLedgerPersistenceService>()
    private val mockUtxoLedgerTransactionVerificationService = mock<UtxoLedgerTransactionVerificationService>()
    private val mockUtxoLedgerGroupParametersPersistenceService = mock<UtxoLedgerGroupParametersPersistenceService>()
    private val mockGroupParametersLookup = mockGroupParametersLookup()
    private val mockSignedGroupParametersVerifier = mock<SignedGroupParametersVerifier>()

    val mockUtxoLedgerStateQueryService = mock<UtxoLedgerStateQueryService>()
    val mockCurrentSandboxGroupContext = mock<CurrentSandboxGroupContext>()
    val mockFlowSandboxService = mock<FlowSandboxService>()
    private val mockExternalEventExecutor = mock<ExternalEventExecutor>()
    private val mockResultSetFactory = mock<ResultSetFactory>()

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
        mockUtxoLedgerStateQueryService,
        mockUtxoLedgerGroupParametersPersistenceService,
        mockGroupParametersLookup
    )
    val utxoSignedTransactionFactory = UtxoSignedTransactionFactoryImpl(
        currentSandboxGroupContext,
        jsonMarshallingService,
        jsonValidator,
        serializationServiceNullCfg,
        fakeTransactionSignatureService(),
        transactionMetadataFactory,
        wireTransactionFactory,
        utxoLedgerTransactionFactory,
        mockUtxoLedgerTransactionVerificationService,
        mockUtxoLedgerGroupParametersPersistenceService,
        mockGroupParametersLookup,
        mockSignedGroupParametersVerifier
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
        mockResultSetFactory,
        mockUtxoLedgerTransactionVerificationService
    )
    val utxoSignedTransactionKryoSerializer = UtxoSignedTransactionKryoSerializer(
        serializationServiceWithWireTx,
        fakeTransactionSignatureService(),
        utxoLedgerTransactionFactory
    )
    val utxoSignedTransactionAMQPSerializer =
        UtxoSignedTransactionSerializer(
            serializationServiceNullCfg,
            fakeTransactionSignatureService(),
            utxoLedgerTransactionFactory
        )
    val utxoSignedTransactionExample = getUtxoSignedTransactionExample(
        digestService,
        merkleTreeProvider,
        serializationServiceWithWireTx,
        jsonMarshallingService,
        jsonValidator,
        fakeTransactionSignatureService(),
        utxoLedgerTransactionFactory
    )

    // This is the only not stateless.
    val utxoTransactionBuilder = UtxoTransactionBuilderImpl(utxoSignedTransactionFactory, mockNotaryLookup)
}
