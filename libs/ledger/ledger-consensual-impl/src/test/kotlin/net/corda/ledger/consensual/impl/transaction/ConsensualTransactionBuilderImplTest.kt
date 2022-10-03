package net.corda.ledger.consensual.impl.transaction

import java.security.KeyPairGenerator
import java.security.PublicKey
import net.corda.application.impl.services.json.JsonMarshallingServiceImpl
import net.corda.cipher.suite.impl.CipherSchemeMetadataImpl
import net.corda.cipher.suite.impl.DigestServiceImpl
import net.corda.crypto.merkle.impl.MerkleTreeProviderImpl
import net.corda.ledger.consensual.impl.PartyImpl
import net.corda.ledger.consensual.impl.helper.ConfiguredTestSerializationService
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.DigestService
import net.corda.v5.cipher.suite.merkle.MerkleTreeProvider
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.consensual.ConsensualState
import net.corda.v5.ledger.consensual.Party
import net.corda.v5.ledger.consensual.transaction.ConsensualLedgerTransaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
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
            .withStates(testConsensualState)
            .signInitial(testPublicKey)
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
                .signInitial(testPublicKey)
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
