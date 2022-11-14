package net.corda.ledger.consensual.test

import net.corda.ledger.common.test.CommonLedgerTest
import net.corda.ledger.common.testkit.mockSigningService
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
        mockSigningService(),
        mock(),
        transactionMetadataFactory,
        wireTransactionFactory,
        flowFiberService,
        jsonMarshallingService,
        jsonValidator
    )
    val consensualLedgerService = ConsensualLedgerServiceImpl(consensualSignedTransactionFactory, flowEngine, mock())
    val consensualSignedTransactionKryoSerializer = ConsensualSignedTransactionKryoSerializer(
        serializationServiceWithWireTx,
        mockSigningService(),
        mock()
    )
    val consensualSignedTransactionAMQPSerializer =
        ConsensualSignedTransactionSerializer(serializationServiceNullCfg, mockSigningService(), mock())
    val consensualSignedTransactionExample = getConsensualSignedTransactionExample(
        digestService,
        merkleTreeProvider,
        serializationServiceWithWireTx,
        jsonMarshallingService,
        jsonValidator,
        mockSigningService(),
        mock()
    )

    // This is the only not stateless.
    val consensualTransactionBuilder = ConsensualTransactionBuilderImpl(
        consensualSignedTransactionFactory
    )
}
