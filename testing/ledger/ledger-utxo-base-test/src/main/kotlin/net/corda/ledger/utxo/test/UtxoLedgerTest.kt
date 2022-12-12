package net.corda.ledger.utxo.test

import net.corda.ledger.common.flow.impl.transaction.filtered.factory.FilteredTransactionFactoryImpl
import net.corda.ledger.common.test.CommonLedgerTest
import net.corda.ledger.common.testkit.mockTransactionSignatureService
import net.corda.ledger.utxo.flow.impl.UtxoLedgerServiceImpl
import net.corda.ledger.utxo.flow.impl.transaction.UtxoTransactionBuilderImpl
import net.corda.ledger.utxo.flow.impl.transaction.filtered.factory.UtxoFilteredTransactionFactoryImpl
import net.corda.ledger.utxo.flow.impl.transaction.factory.UtxoSignedTransactionFactoryImpl
import net.corda.ledger.utxo.flow.impl.transaction.serializer.amqp.UtxoSignedTransactionSerializer
import net.corda.ledger.utxo.flow.impl.transaction.serializer.kryo.UtxoSignedTransactionKryoSerializer
import net.corda.ledger.utxo.testkit.getUtxoSignedTransactionExample
import org.mockito.kotlin.mock

abstract class UtxoLedgerTest : CommonLedgerTest() {
    private val utxoFilteredTransactionFactory = UtxoFilteredTransactionFactoryImpl(
        FilteredTransactionFactoryImpl(
            jsonMarshallingService,
            merkleTreeProvider,
            serializationServiceWithWireTx
        ), serializationServiceWithWireTx
    )
    val utxoSignedTransactionFactory = UtxoSignedTransactionFactoryImpl(
        currentSandboxGroupContext,
        jsonMarshallingService,
        jsonValidator,
        serializationServiceNullCfg,
        mockTransactionSignatureService(),
        transactionMetadataFactory,
        wireTransactionFactory
    )
    val utxoLedgerService = UtxoLedgerServiceImpl(utxoFilteredTransactionFactory, utxoSignedTransactionFactory, flowEngine, mock())
    val utxoSignedTransactionKryoSerializer = UtxoSignedTransactionKryoSerializer(
        serializationServiceWithWireTx,
        mockTransactionSignatureService()
    )
    val utxoSignedTransactionAMQPSerializer =
        UtxoSignedTransactionSerializer(serializationServiceNullCfg, mockTransactionSignatureService())
    val utxoSignedTransactionExample = getUtxoSignedTransactionExample(
        digestService,
        merkleTreeProvider,
        serializationServiceWithWireTx,
        jsonMarshallingService,
        jsonValidator,
        mockTransactionSignatureService()
    )
    
    // This is the only not stateless.
    val utxoTransactionBuilder = UtxoTransactionBuilderImpl(utxoSignedTransactionFactory)
}
