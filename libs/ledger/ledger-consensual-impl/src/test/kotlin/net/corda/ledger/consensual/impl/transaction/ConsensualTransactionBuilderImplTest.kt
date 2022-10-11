package net.corda.ledger.consensual.impl.transaction

import net.corda.application.impl.services.json.JsonMarshallingServiceImpl
import net.corda.cipher.suite.impl.CipherSchemeMetadataImpl
import net.corda.cipher.suite.impl.DigestServiceImpl
import net.corda.crypto.merkle.impl.MerkleTreeProviderImpl
import net.corda.ledger.consensual.impl.ConsensualTransactionMocks
import net.corda.ledger.consensual.impl.TestConsensualState
import net.corda.ledger.consensual.impl.helper.ConfiguredTestSerializationService
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.DigestService
import net.corda.v5.cipher.suite.merkle.MerkleTreeProvider
import net.corda.v5.crypto.SecureHash
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import kotlin.test.assertIs

internal class ConsensualTransactionBuilderImplTest {
    private val jsonMarshallingService: JsonMarshallingService = JsonMarshallingServiceImpl()
    private val cipherSchemeMetadata: CipherSchemeMetadata = CipherSchemeMetadataImpl()
    private val digestService: DigestService = DigestServiceImpl(cipherSchemeMetadata, null)
    private val merkleTreeFactory: MerkleTreeProvider = MerkleTreeProviderImpl(digestService)
    private val serializationService: SerializationService =
        ConfiguredTestSerializationService.getTestSerializationService(cipherSchemeMetadata)

    @Test
    fun `can build a simple Transaction`() {
        val tx = ConsensualTransactionBuilderImpl(
            cipherSchemeMetadata,
            digestService,
            jsonMarshallingService,
            merkleTreeFactory,
            serializationService,
            ConsensualTransactionMocks.mockSigningService(),
            ConsensualTransactionMocks.mockMemberLookup(),
            ConsensualTransactionMocks.mockSandboxCpks()
        )
            .withStates(ConsensualTransactionMocks.testConsensualState)
            .signInitial(ConsensualTransactionMocks.testPublicKey)
        assertIs<SecureHash>(tx.id)
    }

    @Test
    fun `cannot build Transaction without Consensual States`() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            ConsensualTransactionBuilderImpl(
                cipherSchemeMetadata,
                digestService,
                jsonMarshallingService,
                merkleTreeFactory,
                serializationService,
                ConsensualTransactionMocks.mockSigningService(),
                ConsensualTransactionMocks.mockMemberLookup(),
                ConsensualTransactionMocks.mockSandboxCpks()
            )
                .signInitial(ConsensualTransactionMocks.testPublicKey)

        }
        assertEquals("At least one Consensual State is required", exception.message)
    }

    @Test
    fun `cannot build Transaction with Consensual States without participants`() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            ConsensualTransactionBuilderImpl(
                cipherSchemeMetadata,
                digestService,
                jsonMarshallingService,
                merkleTreeFactory,
                serializationService,
                ConsensualTransactionMocks.mockSigningService(),
                ConsensualTransactionMocks.mockMemberLookup(),
                ConsensualTransactionMocks.mockSandboxCpks()
            )
                .withStates(ConsensualTransactionMocks.testConsensualState)
                .withStates(TestConsensualState("test", emptyList()))
                .signInitial(ConsensualTransactionMocks.testPublicKey)
        }
        assertEquals("All consensual states needs to have participants", exception.message)
    }
}
