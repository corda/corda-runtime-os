package net.corda.ledger.utxo.flow.impl.transaction

import net.corda.application.impl.services.json.JsonMarshallingServiceImpl
import net.corda.cipher.suite.impl.CipherSchemeMetadataImpl
import net.corda.cipher.suite.impl.DigestServiceImpl
import net.corda.crypto.merkle.impl.MerkleTreeProviderImpl
import net.corda.internal.serialization.amqp.currentSandboxGroup
import net.corda.internal.serialization.amqp.helper.TestSerializationService
import net.corda.internal.serialization.amqp.helper.testSerializationContext
import net.corda.ledger.common.data.transaction.CordaPackageSummary
import net.corda.ledger.common.testkit.mockSigningService
import net.corda.ledger.common.testkit.publicKeyExample
import net.corda.ledger.utxo.testkit.UtxoCommandExample
import net.corda.ledger.utxo.testkit.getUtxoInvalidStateAndRef
import net.corda.ledger.utxo.testkit.utxoNotaryExample
import net.corda.ledger.utxo.testkit.utxoStateExample
import net.corda.ledger.utxo.testkit.utxoTimeWindowExample
import net.corda.ledger.utxo.testkit.utxoTransactionMetadataExample
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.DigestService
import net.corda.v5.cipher.suite.merkle.MerkleTreeProvider
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.utxo.transaction.UtxoTransactionBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import kotlin.test.assertIs

internal class UtxoTransactionBuilderImplTest {
    private val jsonMarshallingService: JsonMarshallingService = JsonMarshallingServiceImpl()
    private val cipherSchemeMetadata: CipherSchemeMetadata = CipherSchemeMetadataImpl()
    private val digestService: DigestService = DigestServiceImpl(cipherSchemeMetadata, null)
    private val merkleTreeFactory: MerkleTreeProvider = MerkleTreeProviderImpl(digestService)
    private val serializationService: SerializationService = TestSerializationService.getTestSerializationService({}, cipherSchemeMetadata)

    @Test
    fun `can build a simple Transaction`() {
        val tx = makeTransactionBuilder()
            .setNotary(utxoNotaryExample)
            .setTimeWindowBetween(utxoTimeWindowExample.from, utxoTimeWindowExample.until)
            .addOutputState(utxoStateExample)
            .addInputState(getUtxoInvalidStateAndRef())
            .addReferenceInputState(getUtxoInvalidStateAndRef())
            .addCommand(UtxoCommandExample())
            .addAttachment(SecureHash("SHA-256", ByteArray(12)))
            .sign(publicKeyExample)
        assertIs<SecureHash>(tx.id)
    }

    // TODO Add tests for verification failures.

    @Test
    fun `includes CPI and CPK information in metadata`() {
        val tx = makeTransactionBuilder()
            .setNotary(utxoNotaryExample)
            .setTimeWindowBetween(utxoTimeWindowExample.from, utxoTimeWindowExample.until)
            .addOutputState(utxoStateExample)
            .addInputState(getUtxoInvalidStateAndRef())
            .addReferenceInputState(getUtxoInvalidStateAndRef())
            .addCommand(UtxoCommandExample())
            .addAttachment(SecureHash("SHA-256", ByteArray(12)))
            .sign(publicKeyExample) as UtxoSignedTransactionImpl

        val metadata = tx.wireTransaction.metadata
        assertEquals("0.001", metadata.getLedgerVersion())

        val expectedCpiMetadata = CordaPackageSummary(
            "CPI name",
            "CPI version",
            "46616B652D76616C7565",
            "416E6F746865722D46616B652D76616C7565",
        )
        assertEquals(expectedCpiMetadata, metadata.getCpiMetadata())

        val expectedCpkMetadata = listOf(
            CordaPackageSummary(
                "MockCpk",
                "1",
                "",
                "0101010101010101010101010101010101010101010101010101010101010101"
            ),
            CordaPackageSummary(
                "MockCpk",
                "3",
                "",
                "0303030303030303030303030303030303030303030303030303030303030303"
            )
        )
        assertEquals(expectedCpkMetadata, metadata.getCpkMetadata())
    }

    private fun makeTransactionBuilder(): UtxoTransactionBuilder {
        return UtxoTransactionBuilderImpl(
            cipherSchemeMetadata,
            digestService,
            jsonMarshallingService,
            merkleTreeFactory,
            serializationService,
            mockSigningService(),
            mock(),
            testSerializationContext.currentSandboxGroup(),
            utxoTransactionMetadataExample
        )
    }
}
