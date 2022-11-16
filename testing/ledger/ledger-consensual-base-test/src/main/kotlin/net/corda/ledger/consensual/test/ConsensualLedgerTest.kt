package net.corda.ledger.consensual.test

import net.corda.ledger.common.test.CommonLedgerTest
import net.corda.ledger.common.testkit.mockTransactionSignatureService
import net.corda.ledger.consensual.flow.impl.ConsensualLedgerServiceImpl
import net.corda.ledger.consensual.flow.impl.transaction.ConsensualTransactionBuilderImpl
import net.corda.ledger.consensual.flow.impl.transaction.factory.ConsensualSignedTransactionFactoryImpl
import net.corda.ledger.consensual.flow.impl.transaction.serializer.amqp.ConsensualSignedTransactionSerializer
import net.corda.ledger.consensual.flow.impl.transaction.serializer.kryo.ConsensualSignedTransactionKryoSerializer
import net.corda.ledger.consensual.testkit.getConsensualSignedTransactionExample
import org.mockito.kotlin.mock

abstract class ConsensualLedgerTest : CommonLedgerTest() {
    val consensualSignedTransactionFactory = ConsensualSignedTransactionFactoryImpl(
        serializationServiceNullCfg,
        mockTransactionSignatureService(),
        transactionMetadataFactory,
        wireTransactionFactory,
        flowFiberService,
        jsonMarshallingService
    )
    val consensualLedgerService = ConsensualLedgerServiceImpl(consensualSignedTransactionFactory, flowEngine, mock())
    val consensualSignedTransactionKryoSerializer = ConsensualSignedTransactionKryoSerializer(
        serializationServiceWithWireTx,
        mockTransactionSignatureService()
    )
    val consensualSignedTransactionAMQPSerializer =
        ConsensualSignedTransactionSerializer(serializationServiceNullCfg, mockTransactionSignatureService())
    val consensualSignedTransactionExample = getConsensualSignedTransactionExample(
        digestService,
        merkleTreeProvider,
        serializationServiceWithWireTx,
        jsonMarshallingService,
        mockTransactionSignatureService()
    )

    // This is the only not stateless.
    val consensualTransactionBuilder = ConsensualTransactionBuilderImpl(
        consensualSignedTransactionFactory
    )
}
