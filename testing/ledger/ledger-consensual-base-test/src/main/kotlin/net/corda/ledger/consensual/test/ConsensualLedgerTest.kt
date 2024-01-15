package net.corda.ledger.consensual.test

import net.corda.ledger.common.test.CommonLedgerTest
import net.corda.ledger.common.testkit.fakeTransactionSignatureService
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
        fakeTransactionSignatureService(),
        transactionMetadataFactory,
        wireTransactionFactory,
        currentSandboxGroupContext,
        jsonMarshallingService,
        jsonValidator,
        privacySaltProviderService
    )
    val consensualLedgerService = ConsensualLedgerServiceImpl(consensualSignedTransactionFactory, flowEngine, mock())
    val consensualSignedTransactionKryoSerializer = ConsensualSignedTransactionKryoSerializer(
        serializationServiceWithWireTx,
        fakeTransactionSignatureService()
    )
    val consensualSignedTransactionAMQPSerializer =
        ConsensualSignedTransactionSerializer(serializationServiceNullCfg, fakeTransactionSignatureService())
    val consensualSignedTransactionExample = getConsensualSignedTransactionExample(
        digestService,
        merkleTreeProvider,
        serializationServiceWithWireTx,
        jsonMarshallingService,
        jsonValidator,
        fakeTransactionSignatureService()
    )

    // This is the only not stateless.
    val consensualTransactionBuilder = ConsensualTransactionBuilderImpl(
        consensualSignedTransactionFactory
    )
}
