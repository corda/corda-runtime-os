package net.corda.ledger.consensual.flow.impl.transaction

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
import net.corda.ledger.consensual.testkit.ConsensualStateClassExample
import net.corda.ledger.consensual.testkit.consensualStateExample
import net.corda.ledger.consensual.testkit.consensualTransactionMetaDataExample
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.DigestService
import net.corda.v5.cipher.suite.merkle.MerkleTreeProvider
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.consensual.transaction.ConsensualTransactionBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import kotlin.test.assertIs

internal class ConsensualTransactionBuilderImplTest {
    private val jsonMarshallingService: JsonMarshallingService = JsonMarshallingServiceImpl()
    private val cipherSchemeMetadata: CipherSchemeMetadata = CipherSchemeMetadataImpl()
    private val digestService: DigestService = DigestServiceImpl(cipherSchemeMetadata, null)
    private val merkleTreeFactory: MerkleTreeProvider = MerkleTreeProviderImpl(digestService)
    private val serializationService: SerializationService = TestSerializationService.getTestSerializationService({}, cipherSchemeMetadata)

    @Test
    fun `can build a simple Transaction`() {
        val tx = makeTransactionBuilder()
            .withStates(consensualStateExample)
            .sign(publicKeyExample)
        assertIs<SecureHash>(tx.id)
    }

    @Test
    fun `cannot build Transaction without Consensual States`() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            makeTransactionBuilder().sign(publicKeyExample)
        }
        assertEquals("At least one consensual state is required", exception.message)
    }

    @Test
    fun `cannot build Transaction with Consensual States without participants`() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            makeTransactionBuilder()
                .withStates(consensualStateExample)
                .withStates(ConsensualStateClassExample("test", emptyList()))
                .sign(publicKeyExample)
        }
        assertEquals("All consensual states must have participants", exception.message)
    }

    @Test
    fun `includes CPI and CPK information in metadata`() {
        val tx = makeTransactionBuilder()
            .withStates(consensualStateExample)
            .sign(publicKeyExample) as ConsensualSignedTransactionImpl

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

    private fun makeTransactionBuilder(): ConsensualTransactionBuilder {
        return ConsensualTransactionBuilderImpl(
            cipherSchemeMetadata,
            digestService,
            jsonMarshallingService,
            merkleTreeFactory,
            serializationService,
            mockSigningService(),
            mock(),
            testSerializationContext.currentSandboxGroup(),
            consensualTransactionMetaDataExample
        )
    }
}
