package net.corda.simulator.runtime.ledger.utxo

import net.corda.simulator.factories.SimulatorConfigurationBuilder
import net.corda.simulator.runtime.serialization.BaseSerializationService
import net.corda.simulator.runtime.testutils.generateKey
import net.corda.simulator.runtime.testutils.generateKeys
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.application.persistence.PersistenceService
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.util.days
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.utxo.Command
import net.corda.v5.ledger.utxo.ContractState
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.security.PublicKey
import java.time.Clock
import java.time.Instant

class UtxoTransactionBuilderBaseTest {

    private val notaryX500 = MemberX500Name.parse("O=Notary,L=London,C=GB")
    private val publicKeys = generateKeys(2)
    private val notary = Party(notaryX500, generateKey())

    @Test
    fun `should be able to build a utxo transaction and sign it with a key`() {
        // Given a key has been generated on the node, so the SigningService can sign with it
        val signingService = mock<SigningService>()
        whenever(signingService.sign(any(), eq(publicKeys[0]), eq(SignatureSpec.ECDSA_SHA256)))
            .thenReturn(DigitalSignature.WithKey(publicKeys[0], "My fake signed things".toByteArray(), mapOf()))
        whenever(signingService.findMySigningKeys(any())).thenReturn(mapOf(publicKeys[0] to publicKeys[0]))

        // And our configuration has a special clock
        val clock = mock<Clock>()
        whenever(clock.instant()).thenReturn(Instant.EPOCH)
        val clockConfig = SimulatorConfigurationBuilder.create().withClock(clock).build()

        // When we build a transaction via the tx builder and sign it with a key
        val persistenceService = mock<PersistenceService>()
        val serializationService = BaseSerializationService()
        val builder = UtxoTransactionBuilderBase(
            signingService = signingService,
            serializer = serializationService,
            persistenceService = persistenceService,
            configuration = clockConfig
        )
        val command = TestUtxoCommand()
        val output = TestUtxoState("StateData", publicKeys)
        val tx = builder.addCommand(command)
            .addSignatories(listOf(publicKeys[0]))
            .addOutputState(output)
            .setNotary(notary)
            .setTimeWindowUntil(Instant.now().plusMillis(1.days.toMillis()))
            .toSignedTransaction()

        assertThat(tx.notary, `is`(notary))

        // Then the ledger transaction should have the data in it
        val ledgerTx = tx.toLedgerTransaction()
        assertThat(ledgerTx.commands, `is`(listOf(command)))
        assertThat(ledgerTx.outputContractStates, `is`(listOf(output)))

        // And the signatures should have come from the signing service
        assertThat(tx.signatures.size, `is`(1))
        assertThat(tx.signatures[0].by, `is`(publicKeys[0]))
        assertThat(String(tx.signatures[0].signature.bytes), `is`("My fake signed things"))
        assertThat(tx.signatures[0].metadata.timestamp, `is`(Instant.EPOCH))
    }

    class TestUtxoState(
        val name: String,
        override val participants: List<PublicKey>
    ) : ContractState

    class TestUtxoCommand: Command
}