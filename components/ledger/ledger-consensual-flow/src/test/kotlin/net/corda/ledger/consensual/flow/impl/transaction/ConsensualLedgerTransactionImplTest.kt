package net.corda.ledger.consensual.flow.impl.transaction

import net.corda.application.impl.services.json.JsonMarshallingServiceImpl
import net.corda.cipher.suite.impl.CipherSchemeMetadataImpl
import net.corda.cipher.suite.impl.DigestServiceImpl
import net.corda.crypto.merkle.impl.MerkleTreeProviderImpl
import net.corda.internal.serialization.amqp.currentSandboxGroup
import net.corda.internal.serialization.amqp.helper.TestSerializationService
import net.corda.internal.serialization.amqp.helper.testSerializationContext
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
import net.corda.v5.ledger.consensual.ConsensualState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import java.time.Instant
import kotlin.math.abs
import kotlin.test.assertIs

internal class ConsensualLedgerTransactionImplTest {
    private val jsonMarshallingService: JsonMarshallingService = JsonMarshallingServiceImpl()
    private val cipherSchemeMetadata: CipherSchemeMetadata = CipherSchemeMetadataImpl()
    private val digestService: DigestService = DigestServiceImpl(cipherSchemeMetadata, null)
    private val merkleTreeProvider: MerkleTreeProvider = MerkleTreeProviderImpl(digestService)
    private val serializationService: SerializationService =
        TestSerializationService.getTestSerializationService({}, cipherSchemeMetadata)

    @Test
    fun `ledger transaction contains the same data what it was created with`() {
        val testTimestamp = Instant.now()
        val signedTransaction = ConsensualTransactionBuilderImpl(
            cipherSchemeMetadata,
            digestService,
            jsonMarshallingService,
            merkleTreeProvider,
            serializationService,
            mockSigningService(),
            mock(),
            testSerializationContext.currentSandboxGroup(),
            consensualTransactionMetaDataExample
        )
            .withStates(consensualStateExample)
            .sign(publicKeyExample)
        val ledgerTransaction = signedTransaction.toLedgerTransaction()
        assertTrue(abs(ledgerTransaction.timestamp.toEpochMilli() / 1000 - testTimestamp.toEpochMilli() / 1000) < 5)
        assertIs<List<ConsensualState>>(ledgerTransaction.states)
        assertEquals(1, ledgerTransaction.states.size)
        assertEquals(consensualStateExample, ledgerTransaction.states.first())
        assertIs<ConsensualStateClassExample>(ledgerTransaction.states.first())

        assertIs<SecureHash>(ledgerTransaction.id)
    }
}
