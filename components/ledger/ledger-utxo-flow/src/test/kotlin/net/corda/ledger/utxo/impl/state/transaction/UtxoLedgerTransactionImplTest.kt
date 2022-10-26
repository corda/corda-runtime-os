package net.corda.ledger.utxo.impl.state.transaction

import net.corda.application.impl.services.json.JsonMarshallingServiceImpl
import net.corda.cipher.suite.impl.CipherSchemeMetadataImpl
import net.corda.cipher.suite.impl.DigestServiceImpl
import net.corda.crypto.merkle.impl.MerkleTreeProviderImpl
import net.corda.internal.serialization.amqp.currentSandboxGroup
import net.corda.internal.serialization.amqp.helper.TestSerializationService
import net.corda.internal.serialization.amqp.helper.testSerializationContext
import net.corda.ledger.common.testkit.mockSigningService
import net.corda.ledger.common.testkit.publicKeyExample
import net.corda.ledger.utxo.impl.transaction.UtxoTransactionBuilderImpl
import net.corda.ledger.utxo.testkit.UtxoCommandExample
import net.corda.ledger.utxo.testkit.UtxoStateClassExample
import net.corda.ledger.utxo.testkit.getUtxoInvalidStateAndRef
import net.corda.ledger.utxo.testkit.utxoNotaryExample
import net.corda.ledger.utxo.testkit.utxoStateExample
import net.corda.ledger.utxo.testkit.utxoTimeWindowExample
import net.corda.ledger.utxo.testkit.utxoTransactionMetaDataExample
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.DigestService
import net.corda.v5.cipher.suite.merkle.MerkleTreeProvider
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.utxo.Command
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.StateAndRef
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import kotlin.test.assertIs

internal class UtxoLedgerTransactionImplTest {
    private val jsonMarshallingService: JsonMarshallingService = JsonMarshallingServiceImpl()
    private val cipherSchemeMetadata: CipherSchemeMetadata = CipherSchemeMetadataImpl()
    private val digestService: DigestService = DigestServiceImpl(cipherSchemeMetadata, null)
    private val merkleTreeProvider: MerkleTreeProvider = MerkleTreeProviderImpl(digestService)
    private val serializationService: SerializationService =
        TestSerializationService.getTestSerializationService({}, cipherSchemeMetadata)

    @Test
    fun `ledger transaction contains the same data what it was created with`() {

        val inputStateAndRef = getUtxoInvalidStateAndRef()
        val referenceStateAndRef = getUtxoInvalidStateAndRef()
        val command = UtxoCommandExample()
        val attachment = SecureHash("SHA-256", ByteArray(12))

        val signedTransaction = UtxoTransactionBuilderImpl(
            cipherSchemeMetadata,
            digestService,
            jsonMarshallingService,
            merkleTreeProvider,
            serializationService,
            mockSigningService(),
            mock(),
            testSerializationContext.currentSandboxGroup(),
            utxoTransactionMetaDataExample,
            utxoNotaryExample,
            utxoTimeWindowExample,
        )
            .addOutputState(utxoStateExample)
            .addInputState(inputStateAndRef)
            .addReferenceInputState(referenceStateAndRef)
            .addCommand(command)
            .addAttachment(attachment)
            .sign(publicKeyExample)
        val ledgerTransaction = signedTransaction.toLedgerTransaction()

        assertIs<SecureHash>(ledgerTransaction.id)

        assertEquals(utxoTimeWindowExample, ledgerTransaction.timeWindow)

        assertIs<List<ContractState>>(ledgerTransaction.outputContractStates)
        assertEquals(1, ledgerTransaction.outputContractStates.size)
        assertEquals(utxoStateExample, ledgerTransaction.outputContractStates.first())
        assertIs<UtxoStateClassExample>(ledgerTransaction.outputContractStates.first())

        /** TODO When inputStateAndRefs or referenceInputStateAndRefs will get available
        assertIs<List<StateAndRef<UtxoStateClassExample>>>(ledgerTransaction.inputStateAndRefs)
        assertEquals(1, ledgerTransaction.inputStateAndRefs.size)
        assertEquals(inputStateAndRef, ledgerTransaction.inputStateAndRefs.first())
        assertIs<StateAndRef<UtxoStateClassExample>>(ledgerTransaction.inputStateAndRefs.first())

        assertIs<List<StateAndRef<UtxoStateClassExample>>>(ledgerTransaction.referenceInputStateAndRefs)
        assertEquals(1, ledgerTransaction.referenceInputStateAndRefs.size)
        assertEquals(referenceStateAndRef, ledgerTransaction.referenceInputStateAndRefs.first())
        assertIs<StateAndRef<UtxoStateClassExample>>(ledgerTransaction.referenceInputStateAndRefs.first())
        */

        // Also test Commands and Attachments
    }
}
