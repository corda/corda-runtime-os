package net.corda.simulator.runtime.ledger.utxo

import net.corda.crypto.core.fullIdHash
import net.corda.crypto.core.parseSecureHash
import net.corda.simulator.entities.UtxoTransactionOutputEntity
import net.corda.simulator.entities.UtxoTransactionOutputEntityId
import net.corda.simulator.factories.SimulatorConfigurationBuilder
import net.corda.simulator.runtime.notary.BaseNotaryInfo
import net.corda.simulator.runtime.serialization.BaseSerializationService
import net.corda.simulator.runtime.testutils.generateKey
import net.corda.simulator.runtime.testutils.generateKeys
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.application.persistence.PersistenceService
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.ledger.common.NotaryLookup
import net.corda.v5.ledger.utxo.StateRef
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.Instant
import kotlin.time.Duration.Companion.days

class UtxoTransactionBuilderBaseTest {

    private val notaryX500 = MemberX500Name.parse("O=Notary,L=London,C=GB")
    private val publicKeys = generateKeys(2)
    private val notaryKey =  generateKey()

    @Test
    fun `should be able to build a utxo transaction and sign it with a key`() {
        // Given a key has been generated on the node, so the SigningService can sign with it
        val signingService = mock<SigningService>()
        whenever(signingService.sign(any(), eq(publicKeys[0]), eq(SignatureSpec.ECDSA_SHA256)))
            .thenReturn(DigitalSignature.WithKeyId(publicKeys[0].fullIdHash(), "My fake signed things".toByteArray()))
        whenever(signingService.findMySigningKeys(any())).thenReturn(mapOf(publicKeys[0] to publicKeys[0]))

        val notaryLookup = mock<NotaryLookup>()
        whenever(notaryLookup.notaryServices).thenReturn(listOf( BaseNotaryInfo(notaryX500, "", emptySet(), notaryKey)))

        // And our configuration has a special clock
        val clock = mock<Clock>()
        whenever(clock.instant()).thenReturn(Instant.EPOCH)
        val clockConfig = SimulatorConfigurationBuilder.create().withClock(clock).build()

        // When we build a transaction via the tx builder and sign it with a key
        val persistenceService = mock<PersistenceService>()
        val builder = UtxoTransactionBuilderBase(
            signingService = signingService,
            persistenceService = persistenceService,
            configuration = clockConfig,
            notaryLookup = notaryLookup
        )
        val command = TestUtxoCommand()
        val output = TestUtxoState("StateData", publicKeys)
        val serializer = BaseSerializationService()
        val refState = TestUtxoState("ReferenceState", publicKeys)
        val outputEntity = UtxoTransactionOutputEntity(
            "SHA-256:9407A4B8D56871A27AD9AE800D2AC78D486C25C375CEE80EE7997CB0E6105F9D",
            TestUtxoState::class.java.canonicalName,
            serializer.serialize(listOf<SimEncumbranceGroup>()).bytes,
            serializer.serialize(refState).bytes,
            0,
            false
        )
        whenever(persistenceService.find(eq(UtxoTransactionOutputEntity::class.java),
            eq(UtxoTransactionOutputEntityId(
                "SHA-256:9407A4B8D56871A27AD9AE800D2AC78D486C25C375CEE80EE7997CB0E6105F9D", 0))))
            .thenReturn(outputEntity)
        val refStateRef = StateRef(
            parseSecureHash(
                "SHA-256:9407A4B8D56871A27AD9AE800D2AC78D486C25C375CEE80EE7997CB0E6105F9D")
            , 0)

        val tx = builder.addCommand(command)
            .addSignatories(listOf(publicKeys[0]))
            .addOutputState(output)
            .setNotary(notaryX500)
            .setTimeWindowUntil(Instant.now().plusMillis(1.days.inWholeMilliseconds))
            .addReferenceState(refStateRef)
            .toSignedTransaction()

        assertThat(tx.notaryName, `is`(notaryX500))
        assertThat(tx.notaryKey, `is`(notaryKey))

        // Then the ledger transaction should have the data in it
        val ledgerTx = tx.toLedgerTransaction()
        assertThat(ledgerTx.commands, `is`(listOf(command)))
        assertThat(ledgerTx.outputContractStates, `is`(listOf(output)))

        // And the signatures should have come from the signing service
        assertThat(tx.signatures.size, `is`(1))
        assertThat(tx.signatures[0].by, `is`(publicKeys[0].fullIdHash()))
        assertThat(String(tx.signatures[0].signature.bytes), `is`("My fake signed things"))
        assertThat(tx.signatures[0].metadata.timestamp, `is`(Instant.EPOCH))
        assertThat(ledgerTx.referenceStateRefs.size, `is`(1))
        assertThat(ledgerTx.referenceStateRefs[0], `is`(refStateRef))
    }

    @Test
    fun `should fail when mandatory fields are missing in the transactions`() {
        val notaryLookup = mock<NotaryLookup>()
        whenever(notaryLookup.notaryServices).thenReturn(listOf( BaseNotaryInfo(notaryX500, "", emptySet(), notaryKey)))
        val builder = UtxoTransactionBuilderBase(
            signingService = mock(),
            persistenceService = mock(),
            configuration = mock(),
            notaryLookup = notaryLookup
        )

        assertThatThrownBy { builder.toSignedTransaction() }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("The notary of UtxoTransactionBuilder must not be null.")

        assertThatThrownBy { builder.setNotary(notaryX500).toSignedTransaction() }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("The time window of UtxoTransactionBuilder must not be null.")

        assertThatThrownBy {
            builder.setNotary(notaryX500)
            .setTimeWindowUntil(Instant.now().plusMillis(1.days.inWholeMilliseconds))
            .toSignedTransaction()
        }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("At least one signatory signing key must be applied to the current transaction.")

        assertThatThrownBy {
            builder.setNotary(notaryX500)
                .setTimeWindowUntil(Instant.now().plusMillis(1.days.inWholeMilliseconds))
                .addSignatories(publicKeys)
                .toSignedTransaction()
        }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("At least one input state, or " +
                    "one output state must be applied to the current transaction.")

        assertThatThrownBy {
            builder.setNotary(notaryX500)
                .setTimeWindowUntil(Instant.now().plusMillis(1.days.inWholeMilliseconds))
                .addSignatories(publicKeys)
                .addOutputState(TestUtxoState("StateData", publicKeys))
                .toSignedTransaction()
        }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("At least one command must be applied to the current transaction.")
    }

}