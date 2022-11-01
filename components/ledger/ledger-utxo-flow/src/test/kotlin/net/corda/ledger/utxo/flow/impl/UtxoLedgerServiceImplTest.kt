package net.corda.ledger.utxo.flow.impl

import net.corda.application.impl.services.json.JsonMarshallingServiceImpl
import net.corda.cipher.suite.impl.CipherSchemeMetadataImpl
import net.corda.cipher.suite.impl.DigestServiceImpl
import net.corda.crypto.merkle.impl.MerkleTreeProviderImpl
import net.corda.internal.serialization.amqp.helper.TestFlowFiberServiceWithSerialization
import net.corda.internal.serialization.amqp.helper.TestSerializationService
import net.corda.ledger.common.data.transaction.factory.WireTransactionFactoryImpl
import net.corda.ledger.common.flow.impl.transaction.factory.TransactionMetadataFactoryImpl
import net.corda.ledger.common.testkit.mockPlatformInfoProvider
import net.corda.ledger.common.testkit.mockSigningService
import net.corda.ledger.common.testkit.publicKeyExample
import net.corda.ledger.utxo.flow.impl.transaction.factory.UtxoSignedTransactionFactoryImpl
import net.corda.ledger.utxo.testkit.UtxoCommandExample
import net.corda.ledger.utxo.testkit.UtxoStateClassExample
import net.corda.ledger.utxo.testkit.getUtxoInvalidStateAndRef
import net.corda.ledger.utxo.testkit.utxoNotaryExample
import net.corda.ledger.utxo.testkit.utxoStateExample
import net.corda.ledger.utxo.testkit.utxoTimeWindowExample
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import net.corda.v5.ledger.utxo.transaction.UtxoTransactionBuilder
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import kotlin.test.assertIs

class UtxoLedgerServiceImplTest {
    private val jsonMarshallingService = JsonMarshallingServiceImpl()
    private val cipherSchemeMetadata = CipherSchemeMetadataImpl()
    private val digestService = DigestServiceImpl(cipherSchemeMetadata, null)
    private val merkleTreeProvider = MerkleTreeProviderImpl(digestService)
    private val flowFiberService = TestFlowFiberServiceWithSerialization()
    private val serializationService = TestSerializationService.getTestSerializationService({}, cipherSchemeMetadata)
    private val transactionMetadataFactory =
        TransactionMetadataFactoryImpl(flowFiberService, mockPlatformInfoProvider())
    private val wireTransactionFactory = WireTransactionFactoryImpl(
        merkleTreeProvider,
        digestService,
        jsonMarshallingService,
        cipherSchemeMetadata,
    )
    private val utxoSignedTransactionFactory = UtxoSignedTransactionFactoryImpl(
        serializationService,
        mockSigningService(),
        mock(),
        transactionMetadataFactory,
        wireTransactionFactory,
        flowFiberService,
        jsonMarshallingService
    )


    @Test
    fun `getTransactionBuilder should return a Transaction Builder`() {
        val service = UtxoLedgerServiceImpl(utxoSignedTransactionFactory)
        val transactionBuilder = service.getTransactionBuilder()
        assertIs<UtxoTransactionBuilder>(transactionBuilder)
    }

    @Test
    fun `UtxoLedgerServiceImpl's getTransactionBuilder() can build a SignedTransaction`() {
        val service = UtxoLedgerServiceImpl(utxoSignedTransactionFactory)
        val transactionBuilder = service.getTransactionBuilder()

        val inputStateAndRef = getUtxoInvalidStateAndRef()
        val referenceStateAndRef = getUtxoInvalidStateAndRef()
        val command = UtxoCommandExample()
        val attachment = SecureHash("SHA-256", ByteArray(12))

        val signedTransaction = transactionBuilder
            .setNotary(utxoNotaryExample)
            .setTimeWindowBetween(utxoTimeWindowExample.from, utxoTimeWindowExample.until)
            .addOutputState(utxoStateExample)
            .addInputState(inputStateAndRef)
            .addReferenceInputState(referenceStateAndRef)
            .addCommand(command)
            .addAttachment(attachment)
            .sign(publicKeyExample)

        assertIs<UtxoSignedTransaction>(signedTransaction)
        assertIs<SecureHash>(signedTransaction.id)

        val ledgerTransaction = signedTransaction.toLedgerTransaction()

        assertIs<SecureHash>(ledgerTransaction.id)

        Assertions.assertEquals(utxoTimeWindowExample, ledgerTransaction.timeWindow)

        assertIs<List<ContractState>>(ledgerTransaction.outputContractStates)
        Assertions.assertEquals(1, ledgerTransaction.outputContractStates.size)
        Assertions.assertEquals(utxoStateExample, ledgerTransaction.outputContractStates.first())
        assertIs<UtxoStateClassExample>(ledgerTransaction.outputContractStates.first())
    }
}
