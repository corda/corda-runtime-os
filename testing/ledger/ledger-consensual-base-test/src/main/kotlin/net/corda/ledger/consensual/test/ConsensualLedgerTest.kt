package net.corda.ledger.consensual.test

import net.corda.ledger.common.test.CommonLedgerTest
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
        transactionSignatureService,
        transactionMetadataFactory,
        wireTransactionFactory,
        currentSandboxGroupContext,
        jsonMarshallingService,
        jsonValidator,
        mockPrivacySaltProviderService
    )
    val consensualLedgerService = ConsensualLedgerServiceImpl(consensualSignedTransactionFactory, flowEngine, mock())
    val consensualSignedTransactionKryoSerializer = ConsensualSignedTransactionKryoSerializer(
        serializationServiceWithWireTx,
        transactionSignatureService
    )
    val consensualSignedTransactionAMQPSerializer =
        ConsensualSignedTransactionSerializer(serializationServiceNullCfg, transactionSignatureService)
    val consensualSignedTransactionExample = getConsensualSignedTransactionExample(
        digestService,
        merkleTreeProvider,
        serializationServiceWithWireTx,
        jsonMarshallingService,
        jsonValidator,
        transactionSignatureService
    )

    // This is the only not stateless.
    val consensualTransactionBuilder = ConsensualTransactionBuilderImpl(
        consensualSignedTransactionFactory
    )
}
