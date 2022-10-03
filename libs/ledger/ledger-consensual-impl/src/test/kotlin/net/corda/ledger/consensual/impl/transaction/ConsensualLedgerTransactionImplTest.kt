package net.corda.ledger.consensual.impl.transaction

import java.security.KeyPairGenerator
import java.security.PublicKey
import java.time.Instant
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlin.math.abs
import kotlin.test.assertIs

internal class ConsensualLedgerTransactionImplTest {
    private val jsonMarshallingService: JsonMarshallingService = JsonMarshallingServiceImpl()
    private val cipherSchemeMetadata: CipherSchemeMetadata = CipherSchemeMetadataImpl()
    private val digestService: DigestService = DigestServiceImpl(cipherSchemeMetadata, null)
    private val merkleTreeProvider: MerkleTreeProvider = MerkleTreeProviderImpl(digestService)
    private val signingService: SigningService = mock()
    private val serializationService: SerializationService =
        ConfiguredTestSerializationService.getTestSerializationService(cipherSchemeMetadata)

    @Test
    fun `ledger transaction contains the same data what it was created with`() {
        val testTimestamp = Instant.now()
        val signedTransaction = ConsensualTransactionBuilderImpl(
            cipherSchemeMetadata,
            digestService,
            jsonMarshallingService,
            merkleTreeProvider,
            serializationService,
            ConsensualTransactionMocks.mockSigningService(),
 	    ConsensualTransactionMocks.mockMemberLookup(),
            ConsensualTransactionMocks.mockSandboxCpks(),
        )
            .withStates(ConsensualTransactionMocks.testConsensualState)
            .signInitial(ConsensualTransactionMocks.testPublicKey)
        val ledgerTransaction = signedTransaction.toLedgerTransaction()
        assertTrue(abs(ledgerTransaction.timestamp.toEpochMilli() / 1000 - testTimestamp.toEpochMilli() / 1000) < 5)
        assertIs<List<ConsensualState>>(ledgerTransaction.states)
        assertEquals(1, ledgerTransaction.states.size)
        assertEquals(ConsensualTransactionMocks.testConsensualState, ledgerTransaction.states.first())
        assertIs<TestConsensualState>(ledgerTransaction.states.first())

        assertIs<SecureHash>(ledgerTransaction.id)
    }
}
