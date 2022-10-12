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
    companion object {
        private lateinit var testPublicKey: PublicKey
        private lateinit var testConsensualState: ConsensualState

        private val testMemberX500Name = MemberX500Name("R3", "London", "GB")

        class TestConsensualState(
            val testField: String,
            override val participants: List<Party>
        ) : ConsensualState {
            override fun verify(ledgerTransaction: ConsensualLedgerTransaction) {}
        }

        @BeforeAll
        @JvmStatic
        fun setup() {
            val kpg = KeyPairGenerator.getInstance("RSA")
            kpg.initialize(512) // Shortest possible to not slow down tests.
            testPublicKey = kpg.genKeyPair().public

            testConsensualState = TestConsensualState(
                "test",
                listOf(PartyImpl(testMemberX500Name, testPublicKey))
            )
        }
    }

    private val jsonMarshallingService: JsonMarshallingService = JsonMarshallingServiceImpl()
    private val cipherSchemeMetadata: CipherSchemeMetadata = CipherSchemeMetadataImpl()
    private val digestService: DigestService = DigestServiceImpl(cipherSchemeMetadata, null)
    private val merkleTreeFactory: MerkleTreeProvider = MerkleTreeProviderImpl(digestService)
    private val signingService: SigningService = mock()
    private val serializationService: SerializationService =
        ConfiguredTestSerializationService.getTestSerializationService(cipherSchemeMetadata)

    @Test
    fun `can build a simple Transaction`() {
        whenever(signingService.sign(any(), any(), any())).thenReturn(
            DigitalSignature.WithKey(
                testPublicKey,
                byteArrayOf(1),
                emptyMap()
            )
        )

        val tx = ConsensualTransactionBuilderImpl(
            cipherSchemeMetadata,
            digestService,
            jsonMarshallingService,
            merkleTreeFactory,
            serializationService,
            signingService
        )
            .withStates(testConsensualState)
            .signInitial(testPublicKey)
        assertIs<SecureHash>(tx.id)
    }

    @Test
    fun `cannot build Transaction without Consensual States`() {
        whenever(signingService.sign(any(), any(), any())).thenReturn(
            DigitalSignature.WithKey(
                testPublicKey,
                byteArrayOf(1),
                emptyMap()
            )
        )

        val exception = assertThrows(IllegalArgumentException::class.java) {
            ConsensualTransactionBuilderImpl(
                cipherSchemeMetadata,
                digestService,
                jsonMarshallingService,
                merkleTreeFactory,
                serializationService,
                signingService
            )
                .signInitial(testPublicKey)
        }
        assertEquals("At least one Consensual State is required", exception.message)
    }

    @Test
    fun `cannot build Transaction with Consensual States without participants`() {
        whenever(signingService.sign(any(), any(), any())).thenReturn(
            DigitalSignature.WithKey(
                testPublicKey,
                byteArrayOf(1),
                emptyMap()
            )
        )

        val exception = assertThrows(IllegalArgumentException::class.java) {
            ConsensualTransactionBuilderImpl(
                cipherSchemeMetadata,
                digestService,
                jsonMarshallingService,
                merkleTreeFactory,
                serializationService,
                signingService
            )
                .withStates(testConsensualState)
                .withStates(TestConsensualState("test", emptyList()))
                .signInitial(testPublicKey)
        }
        assertEquals("All consensual states needs to have participants", exception.message)
    }
}