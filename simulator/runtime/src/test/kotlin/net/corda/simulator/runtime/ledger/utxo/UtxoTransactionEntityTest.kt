package net.corda.simulator.runtime.ledger.utxo

import net.corda.simulator.entities.UtxoTransactionEntity
import net.corda.simulator.entities.UtxoTransactionOutputEntity
import net.corda.simulator.entities.UtxoTransactionOutputEntityId
import net.corda.simulator.entities.UtxoTransactionSignatureEntity
import net.corda.simulator.runtime.persistence.DbPersistenceService
import net.corda.v5.base.types.MemberX500Name
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import java.time.Instant

class UtxoTransactionEntityTest {

    private val member = MemberX500Name.parse("CN=IRunCorDapps, OU=Application, O=R3, L=London, C=GB")

    @Test
    fun `should be able to persist a utxo signed transaction entity`() {
        // Given Simulator's persistence service
        DbPersistenceService(member).use {
            val transaction = UtxoTransactionEntity(
                "myId", "commandData".toByteArray(), "inputData".toByteArray(), "notaryData".toByteArray(),
                "referenceStateDate".toByteArray(), "sigData".toByteArray(), "twData".toByteArray(),
                "outputData".toByteArray(), "attachmentData".toByteArray()
            )

            // Then add some signatures and states
            val signatures = setOf(
                UtxoTransactionSignatureEntity(transaction, 0, "a_key".toByteArray(), Instant.now()),
                UtxoTransactionSignatureEntity(transaction, 1, "b_key".toByteArray(), Instant.now())
            )

            transaction.signatures.addAll(signatures)

            // When we persist it
            it.persist(transaction)

            // Then we should be able to retrieve it
            val retrievedTransaction = it.find(UtxoTransactionEntity::class.java, "myId")
                ?: fail("Could not find transaction by id \"${transaction.id}\"")

            assertThat(retrievedTransaction.id, `is`("myId"))
            assertThat(String(retrievedTransaction.commandData), `is`("commandData"))
            assertThat(String(retrievedTransaction.inputData), `is`("inputData"))
            assertThat(String(retrievedTransaction.notaryData), `is`("notaryData"))
            assertThat(String(retrievedTransaction.referenceStateDate), `is`("referenceStateDate"))
            assertThat(String(retrievedTransaction.signatoriesDate), `is`("sigData"))
            assertThat(String(retrievedTransaction.timeWindowDate), `is`("twData"))
            assertThat(String(retrievedTransaction.outputData), `is`("outputData"))
            assertThat(String(retrievedTransaction.attachmentData), `is`("attachmentData"))
            assertThat(retrievedTransaction.signatures, `is`(signatures))
        }
    }

    @Test
    fun `should be able to persist transaction output entity`(){
        // Given Simulator's persistence service
        DbPersistenceService(member).use {
            val txOutputEntity = UtxoTransactionOutputEntity(
                "myId", "someType", "encumbrance".toByteArray(), "some state data".toByteArray(),
                2, false
            )

            // When we persist it
            it.persist(txOutputEntity)

            // Then we should be able to retrieve it
            val retrievedOutput = it.find(UtxoTransactionOutputEntity::class.java,
                UtxoTransactionOutputEntityId("myId", 2))
                ?: fail("Could not find output by id \"${txOutputEntity.transactionId}\"")

            assertThat(retrievedOutput.transactionId, `is`("myId"))
            assertThat(retrievedOutput.type, `is`("someType"))
            assertThat(String(retrievedOutput.stateData), `is`("some state data"))
            assertThat(retrievedOutput.index, `is`(2))
            assertThat(retrievedOutput.isConsumed, `is`(false))
        }
    }

    @Test
    fun `should be able to use named query to retrieve unconsumed output`(){
        // Given Simulator's persistence service
        DbPersistenceService(member).use {
            val unconsumedOutput1 = UtxoTransactionOutputEntity(
                "myId", "someType", "encumbrance".toByteArray(), "some state data".toByteArray(),
                1, false
            )
            val unconsumedOutput2 = UtxoTransactionOutputEntity(
                "myId", "someType", "encumbrance".toByteArray(), "some state data".toByteArray(),
                2, false
            )
            val unconsumedOutput3 = UtxoTransactionOutputEntity(
                "myId1", "someOtherType", "encumbrance".toByteArray(), "some state data".toByteArray(),
                1, false
            )
            val consumedOutput = UtxoTransactionOutputEntity(
                "myId1", "someOtherType", "encumbrance".toByteArray(), "some state data".toByteArray(),
                2, true
            )

            // When we persist it
            it.persist(listOf(unconsumedOutput1, unconsumedOutput2, unconsumedOutput3, consumedOutput))

            // Then we should be able to retrieve the unconsumed output
            val outputs = it.query("UtxoTransactionOutputEntity.findUnconsumedStatesByType",
                UtxoTransactionOutputEntity::class.java)
                .setParameter("type", "someType").execute()

            assertThat(outputs.size, `is`(2))
            assertThat(outputs[0].transactionId, `is`("myId"))
            assertThat(outputs[1].transactionId, `is`("myId"))
            assertThat(outputs[0].index, `is`(1))
            assertThat(outputs[1].index, `is`(2))

            val moreOutputs = it.query("UtxoTransactionOutputEntity.findUnconsumedStatesByType",
                UtxoTransactionOutputEntity::class.java)
                .setParameter("type", "someOtherType").execute()

            assertThat(moreOutputs.size, `is`(1))
            assertThat(moreOutputs[0].transactionId, `is`("myId1"))
            assertThat(moreOutputs[0].index, `is`(1))
        }
    }
}