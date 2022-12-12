package net.corda.simulator.runtime.ledger

import net.corda.simulator.SimulatorConfiguration
import net.corda.simulator.entities.ConsensualTransactionEntity
import net.corda.simulator.factories.SimulatorConfigurationBuilder
import net.corda.simulator.runtime.messaging.SimFiber
import net.corda.simulator.runtime.testutils.generateKeys
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.crypto.DigitalSignatureMetadata
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.application.persistence.PersistenceService
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.ledger.consensual.ConsensualState
import net.corda.v5.ledger.consensual.transaction.ConsensualLedgerTransaction
import net.corda.v5.ledger.consensual.transaction.ConsensualTransactionBuilder
import net.corda.v5.ledger.consensual.transaction.ConsensualTransactionValidator
import net.corda.v5.membership.MemberInfo
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.security.PublicKey
import java.time.Instant

class SimConsensualLedgerServiceTest {

    companion object {
        private val alice = MemberX500Name.parse("O=Alice,L=London,C=GB")
        private val publicKeys = generateKeys(3)
        private val defaultConfiguration = SimulatorConfigurationBuilder.create().build()
    }

    @Test
    fun `should be able to build a ConsensualTransactionBuilder using the given factory`() {
        // Given a factory for building CTBs that and something to capture what we build it with
        val builder = mock<ConsensualTransactionBuilder>()
        val fiber = mock<SimFiber>()
        val memberLookup = mock<MemberLookup>()
        val signingService = mock<SigningService>()
        whenever(fiber.createMemberLookup(any())).thenReturn(memberLookup)
        whenever(fiber.createSigningService(any())).thenReturn(signingService)

        lateinit var capture : Triple<SigningService, MemberLookup, SimulatorConfiguration>
        val builderFactory = ConsensualTransactionBuilderFactory { ss, ml, c ->
            capture = Triple(ss, ml, c)
            builder
        }

        // And a consensual ledger service that will build transactions using it
        val ledgerService = SimConsensualLedgerService(
            alice,
            fiber,
            defaultConfiguration,
            builderFactory
        )

        // When we get a builder
        val createdBuilder = ledgerService.getTransactionBuilder()

        // Then it should be created by the factory
        assertThat(createdBuilder, `is`(builder))

        // Using the same services we passed in
        assertThat(capture, `is`(Triple(signingService, memberLookup, defaultConfiguration)))
    }

    @Test
    fun `should get transaction signed from counterparties when finality is called`(){
        // Given a signed transaction is generated
        val ledgerInfo = ConsensualStateLedgerInfo(
            listOf(NameConsensualState("CordaDev", publicKeys)), Instant.now())
        val signingService = mock<SigningService>()

        publicKeys.forEach {
            whenever(signingService.sign(any(), eq(it), any())).thenReturn(toSignature(it).signature)
        }

        val unsignedTx = ConsensualSignedTransactionBase(
            listOf(),
            ledgerInfo,
            signingService,
            defaultConfiguration
        )
        val signedTransaction = unsignedTx.addSignature(publicKeys[0])

        // And a flow session is created
        val sessions = publicKeys.minus(publicKeys[0]).map {
            val signature = DigitalSignatureAndMetadata(
                toSignature(it).signature,
                DigitalSignatureMetadata(Instant.now(), mapOf())
            )
            val flowSession = mock<FlowSession>()
            whenever(flowSession.receive<Any>(any())).thenReturn(signature, Unit)
            flowSession
        }
        val simFiber = mock<SimFiber>()
        val memberLookup = mock<MemberLookup>()
        val persistenceService = mock<PersistenceService>()
        whenever(simFiber.createMemberLookup(any())).thenReturn(memberLookup)
        whenever(simFiber.getOrCreatePersistenceService(any())).thenReturn(persistenceService)

        //When the transaction is sent to the ledger service for finality
        val ledgerService = SimConsensualLedgerService(
            alice,
            simFiber,
            defaultConfiguration)
        val finalSignedTx = ledgerService.finalize(signedTransaction, sessions)

        // Then the transaction should get signed by the counterparty
        Assertions.assertNotNull(finalSignedTx)
        assertThat(finalSignedTx.signatures.map { it.by }.toSet(), `is`(publicKeys.toSet()))

        // And it should have been persisted
        verify(persistenceService, times(1)).persist(
            (finalSignedTx as ConsensualSignedTransactionBase).toEntity()
        )
    }

    @Test
    fun `should sign transaction when receive finality is called then receive fully-signed transaction`(){
        // Given a signed transaction is generated
        val ledgerInfo = ConsensualStateLedgerInfo(
            listOf(NameConsensualState("CordaDev", publicKeys)), Instant.now())
        val signingService = mock<SigningService>()

        publicKeys.forEach {
            whenever(signingService.sign(any(), eq(it), any())).thenReturn(toSignature(it).signature)
        }

        val unsignedTx = ConsensualSignedTransactionBase(
            listOf(),
            ledgerInfo,
            signingService,
            defaultConfiguration
        )
        val signedTransaction = unsignedTx.addSignature(publicKeys[0])
        val twiceSignedTransaction = signedTransaction
            .addSignature(publicKeys[1])
        val thriceSignedTransaction = twiceSignedTransaction
            .addSignature(publicKeys[2])

        // And a flow session is created that will send the first transaction to be signed,
        // followed by the fully-signed transaction for counterparty records
        val flowSession = mock<FlowSession>()
        whenever(flowSession.receive<Any>(any())).thenReturn(signedTransaction, thriceSignedTransaction)

        //When the ledger service is called for receive finality
        val simFiber = mock<SimFiber>()
        val memberLookup = mock<MemberLookup>()
        val persistenceService = mock<PersistenceService>()
        whenever(simFiber.createMemberLookup(any())).thenReturn(memberLookup)
        whenever(simFiber.createSigningService(any())).thenReturn(signingService)
        whenever(simFiber.getOrCreatePersistenceService(alice)).thenReturn(persistenceService)

        val memberInfo = mock<MemberInfo>()
        val validator = mock<ConsensualTransactionValidator>()
        whenever(memberLookup.myInfo()).thenReturn(memberInfo)
        whenever(memberInfo.ledgerKeys).thenReturn(listOf(publicKeys[1]))

        val ledgerService = SimConsensualLedgerService(alice, simFiber, defaultConfiguration)
        val finalSignedTx = ledgerService.receiveFinality(flowSession, validator)

        // Then the verifier should have been called
        verify(validator, times(1)).checkTransaction(signedTransaction.toLedgerTransaction())

        // And the final signed transaction should be the one that has been signed by all parties
        assertThat(finalSignedTx, `is`(thriceSignedTransaction))

        // And it should have been persisted
        verify(persistenceService, times(1)).persist(thriceSignedTransaction.toEntity())
    }

    @Test
    fun `should be able to retrieve stored transaction from the persistence service`() {

        // Given a persistence service in which we stored a transaction entity
        val transaction = ConsensualSignedTransactionBase(
            publicKeys.map { toSignature(it) },
            ConsensualStateLedgerInfo(listOf(
                NameConsensualState("Gnu", publicKeys),
                NameConsensualState("Wildebeest", publicKeys)
            ),
            Instant.now()),
            mock(),
            defaultConfiguration
        )
        val persistenceService = mock<PersistenceService>()
        whenever(persistenceService.find(ConsensualTransactionEntity::class.java, transaction.toEntity().id))
            .thenReturn(transaction.toEntity())

        // When we retrieve it or the ledger transaction from the ledger service
        val fiber = mock<SimFiber>()
        whenever(fiber.getOrCreatePersistenceService(alice)).thenReturn(persistenceService)
        whenever(fiber.createSigningService(alice)).thenReturn(mock())
        val ledgerService = SimConsensualLedgerService(alice, fiber, defaultConfiguration)
        val retrievedTransaction = ledgerService.findSignedTransaction(transaction.id)
            ?: fail("No transaction retrieved")
        val retrievedLedgerTransaction = ledgerService.findLedgerTransaction(transaction.id)

        // Then it should be converted successfully back into a transaction again
        assertThat(retrievedLedgerTransaction, `is`(transaction.toLedgerTransaction()))
        assertThat(retrievedTransaction.signatures, `is`(transaction.signatures))
        assertThat(retrievedTransaction.id, `is`(transaction.id))
    }

    private fun toSignature(key: PublicKey) = DigitalSignatureAndMetadata(
        DigitalSignature.WithKey(key, "some bytes".toByteArray(), mapOf()),
        DigitalSignatureMetadata(Instant.now(), mapOf())
    )

    data class NameConsensualState(val name: String, override val participants: List<PublicKey>) : ConsensualState {
        override fun verify(ledgerTransaction: ConsensualLedgerTransaction) {}
    }
}