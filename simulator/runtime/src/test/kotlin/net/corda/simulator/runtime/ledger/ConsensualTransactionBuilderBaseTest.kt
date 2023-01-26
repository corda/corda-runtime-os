package net.corda.simulator.runtime.ledger

import net.corda.simulator.factories.SimulatorConfigurationBuilder
import net.corda.simulator.runtime.testutils.generateKeys
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.ledger.consensual.ConsensualState
import net.corda.v5.ledger.consensual.transaction.ConsensualLedgerTransaction
import net.corda.v5.membership.MemberInfo
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.security.PublicKey
import java.time.Clock
import java.time.Instant

class ConsensualTransactionBuilderBaseTest {

    private val publicKeys = generateKeys(3)
    private val signingService = mock<SigningService>()
    private val configuration = SimulatorConfigurationBuilder.create().build()
    private val memberLookup = mock<MemberLookup>()
    private val myLedgerKey = publicKeys[0]
    private val myMemberInfo = mock<MemberInfo>()

    @BeforeEach
    fun `set up signing service mock`() {
        publicKeys.map {
            whenever(signingService.sign(any(), eq(it), any()))
                .thenReturn(DigitalSignature.WithKey(it, "some bytes".toByteArray(), mapOf()))
        }
        whenever(myMemberInfo.ledgerKeys).thenReturn(listOf(myLedgerKey))
        whenever(memberLookup.myInfo()).thenReturn(myMemberInfo)
    }

    @Test
    fun `should produce a transaction on being signed with keys`() {
        val builder = ConsensualTransactionBuilderBase(
            listOf(MyConsensualState(publicKeys)),
            signingService,
            memberLookup,
            configuration
        )

        val tx = builder.toSignedTransaction()
        assertThat(tx.signatures.map {it.by}, `is`(listOf(myLedgerKey)))
    }

    @Test
    fun `should be able to build a consensual transaction and sign with a key`() {
        // Given a key has been generated on the node, so the SigningService can sign with it
        whenever(signingService.sign(any(), eq(publicKeys[0]), eq(SignatureSpec.ECDSA_SHA256)))
            .thenReturn(DigitalSignature.WithKey(publicKeys[0], "My fake signed things".toByteArray(), mapOf()))

        // And our configuration has a special clock
        val clock = mock<Clock>()
        whenever(clock.instant()).thenReturn(Instant.EPOCH)
        val clockConfig = SimulatorConfigurationBuilder.create().withClock(clock).build()

        // When we build a transaction via the ledger service and sign it with a key
        val states = listOf(MyConsensualState(publicKeys))
        val builder = ConsensualTransactionBuilderBase(
            states,
            signingService,
            memberLookup,
            clockConfig
        )
        val tx = builder.toSignedTransaction()

        // Then the ledger transaction version should have the states in it
        assertThat(tx.toLedgerTransaction().states, `is`(states))

        // And the signatures should have come from the signing service, with timestamp from our clock
        assertThat(tx.signatures.size, `is`(1))
        assertThat(tx.signatures[0].by, `is`(publicKeys[0]))
        assertThat(String(tx.signatures[0].signature.bytes), `is`("My fake signed things"))
        assertThat(tx.signatures[0].metadata.timestamp, `is`(Instant.EPOCH))

        // And the timestamp on the ledger transaction should be from our clock
        assertThat(tx.toLedgerTransaction().timestamp, `is`(Instant.EPOCH))
    }

    class MyConsensualState(override val participants: List<PublicKey>) : ConsensualState {
        override fun verify(ledgerTransaction: ConsensualLedgerTransaction) {}
    }
}