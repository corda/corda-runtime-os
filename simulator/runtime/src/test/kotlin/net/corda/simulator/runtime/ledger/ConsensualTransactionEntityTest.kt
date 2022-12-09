package net.corda.simulator.runtime.ledger

import net.corda.simulator.entities.ConsensualTransactionEntity
import net.corda.simulator.entities.ConsensualTransactionSignatureEntity
import net.corda.simulator.runtime.persistence.DbPersistenceService
import net.corda.v5.base.types.MemberX500Name
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import java.time.Instant

class ConsensualTransactionEntityTest {

    private val member = MemberX500Name.parse("CN=IRunCorDapps, OU=Application, O=R3, L=London, C=GB")

    @Test
    fun `should be able to persist a signed transaction entity`() {
        // Given Simulator's persistence service
        DbPersistenceService(member).use {
            // And a signed transaction with states and signatures
            val transaction = ConsensualTransactionEntity("my id", "some state data".toByteArray(), Instant.now())

            // Then add some signatures and states
            val signatures = setOf(
                ConsensualTransactionSignatureEntity(transaction, 0, "a_key".toByteArray(), Instant.now()),
                ConsensualTransactionSignatureEntity(transaction, 1, "b_key".toByteArray(), Instant.now())
            )

            transaction.signatures.addAll(signatures)

            // When we persist it
            it.persist(transaction)

            // Then we should be able to retrieve it
            val retrievedTransaction = it.find(ConsensualTransactionEntity::class.java, "my id")
                ?: fail("Could not find transaction by id \"${transaction.id}\"")

            assertThat(retrievedTransaction.id, `is`("my id"))

            assertThat(String(retrievedTransaction.stateData), `is`("some state data"))
            assertThat(retrievedTransaction.signatures, `is`(signatures))
        }
    }
}