package net.corda.ledger.utxo.flow.impl.transaction

import net.corda.ledger.common.data.transaction.CordaPackageSummaryImpl
import net.corda.ledger.common.test.dummyCpkSignerSummaryHash
import net.corda.ledger.common.testkit.publicKeyExample
import net.corda.ledger.utxo.test.UtxoLedgerTest
import net.corda.ledger.utxo.testkit.UtxoCommandExample
import net.corda.ledger.utxo.testkit.UtxoStateClassExample
import net.corda.ledger.utxo.testkit.getUtxoInvalidStateAndRef
import net.corda.ledger.utxo.testkit.utxoNotaryExample
import net.corda.ledger.utxo.testkit.utxoStateAndRefExample
import net.corda.ledger.utxo.testkit.utxoStateExample
import net.corda.ledger.utxo.testkit.utxoTimeWindowExample
import net.corda.v5.crypto.SecureHash
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import kotlin.test.assertIs

class UtxoTransactionBuilderImplTest : UtxoLedgerTest() {
    @Test
    fun `can build a simple Transaction`() {

        val inputStateAndRef = getUtxoInvalidStateAndRef()
        val inputStateRef = inputStateAndRef.ref
        val referenceStateAndRef = getUtxoInvalidStateAndRef()
        val referenceStateRef = referenceStateAndRef.ref

        whenever(mockUtxoLedgerStateQueryService.resolveStateRefs(any()))
            .thenReturn(listOf(inputStateAndRef))

        val tx = utxoTransactionBuilder
            .setNotary(utxoNotaryExample)
            .setTimeWindowBetween(utxoTimeWindowExample.from, utxoTimeWindowExample.until)
            .addOutputState(utxoStateExample)
            .addInputState(inputStateRef)
            .addReferenceState(referenceStateRef)
            .addSignatories(listOf(publicKeyExample))
            .addCommand(UtxoCommandExample())
            .addAttachment(SecureHash("SHA-256", ByteArray(12)))
            .let { it as UtxoTransactionBuilderInternal }
            .toSignedTransaction()
        assertIs<SecureHash>(tx.id)
        assertEquals(inputStateRef, tx.inputStateRefs.single())
        assertEquals(referenceStateRef, tx.referenceStateRefs.single())
        assertEquals(utxoStateExample, tx.outputStateAndRefs.single().state.contractState)
        assertEquals(utxoNotaryExample, tx.notary)
        assertEquals(utxoTimeWindowExample, tx.timeWindow)
        assertEquals(publicKeyExample, tx.signatories.first())
    }

    @Test
    fun `can build a simple Transaction with empty component groups`() {
        val tx = utxoTransactionBuilder
            .setNotary(utxoNotaryExample)
            .setTimeWindowBetween(utxoTimeWindowExample.from, utxoTimeWindowExample.until)
            .addOutputState(utxoStateExample)
            .addSignatories(listOf(publicKeyExample))
            .addCommand(UtxoCommandExample())
            .let { it as UtxoTransactionBuilderInternal }
            .toSignedTransaction()
        assertIs<SecureHash>(tx.id)
        assertThat(tx.inputStateRefs).isEmpty()
        assertThat(tx.referenceStateRefs).isEmpty()
        assertEquals(utxoStateExample, tx.outputStateAndRefs.single().state.contractState)
        assertEquals(utxoNotaryExample, tx.notary)
        assertEquals(utxoTimeWindowExample, tx.timeWindow)
        assertEquals(publicKeyExample, tx.signatories.first())
    }

    @Test
    fun `includes CPI and CPK information in metadata`() {
        val tx = utxoTransactionBuilder
            .setNotary(utxoNotaryExample)
            .setTimeWindowBetween(utxoTimeWindowExample.from, utxoTimeWindowExample.until)
            .addOutputState(utxoStateExample)
            .addSignatories(listOf(publicKeyExample))
            .addCommand(UtxoCommandExample())
            .addAttachment(SecureHash("SHA-256", ByteArray(12)))
            .let { it as UtxoTransactionBuilderInternal }
            .toSignedTransaction() as UtxoSignedTransactionImpl

        val metadata = tx.wireTransaction.metadata
        assertEquals(1, metadata.getLedgerVersion())

        val expectedCpiMetadata = CordaPackageSummaryImpl(
            "CPI name",
            "CPI version",
            "46616B652D76616C7565",
            "416E6F746865722D46616B652D76616C7565",
        )
        assertEquals(expectedCpiMetadata, metadata.getCpiMetadata())

        val expectedCpkMetadata = listOf(
            CordaPackageSummaryImpl(
                "MockCpk",
                "1",
                dummyCpkSignerSummaryHash.toString(),
                "SHA-256:0101010101010101010101010101010101010101010101010101010101010101"
            ),
            CordaPackageSummaryImpl(
                "MockCpk",
                "3",
                dummyCpkSignerSummaryHash.toString(),
                "SHA-256:0303030303030303030303030303030303030303030303030303030303030303"
            )
        )
        assertEquals(expectedCpkMetadata, metadata.getCpkMetadata())
    }

    @Test
    fun `Calculate encumbrance groups correctly`() {
        val inputStateAndRef = getUtxoInvalidStateAndRef()
        val inputStateRef = inputStateAndRef.ref
        val referenceStateAndRef = getUtxoInvalidStateAndRef()
        val referenceStateRef = referenceStateAndRef.ref

        whenever(mockUtxoLedgerStateQueryService.resolveStateRefs(any()))
            .thenReturn(listOf(inputStateAndRef))

        val tx = utxoTransactionBuilder
            .setNotary(utxoNotaryExample)
            .setTimeWindowBetween(utxoTimeWindowExample.from, utxoTimeWindowExample.until)
            .addEncumberedOutputStates(
                "encumbrance 1",
                UtxoStateClassExample("test 1", listOf(publicKeyExample)),
                UtxoStateClassExample("test 2", listOf(publicKeyExample))
            )
            .addOutputState(utxoStateExample)
            .addEncumberedOutputStates(
                "encumbrance 2",
                UtxoStateClassExample("test 3", listOf(publicKeyExample)),
                UtxoStateClassExample("test 4", listOf(publicKeyExample)),
                UtxoStateClassExample("test 5", listOf(publicKeyExample))
            )
            .addEncumberedOutputStates(
                "encumbrance 1",
                UtxoStateClassExample("test 6", listOf(publicKeyExample))
            )
            .addInputState(inputStateRef)
            .addReferenceState(referenceStateRef)
            .addSignatories(listOf(publicKeyExample))
            .addCommand(UtxoCommandExample())
            .addAttachment(SecureHash("SHA-256", ByteArray(12)))
            .let { it as UtxoTransactionBuilderInternal }
            .toSignedTransaction()

        assertThat(tx.outputStateAndRefs).hasSize(7)
        assertThat(tx.outputStateAndRefs[0].state.encumbrance).isNotNull.extracting { it?.tag }.isEqualTo("encumbrance 1")
        assertThat(tx.outputStateAndRefs[0].state.encumbrance).isNotNull.extracting { it?.size }.isEqualTo(3)

        assertThat(tx.outputStateAndRefs[1].state.encumbrance).isNotNull.extracting { it?.tag }.isEqualTo("encumbrance 1")
        assertThat(tx.outputStateAndRefs[1].state.encumbrance).isNotNull.extracting { it?.size }.isEqualTo(3)

        assertThat(tx.outputStateAndRefs[2].state.encumbrance).isNull()

        assertThat(tx.outputStateAndRefs[3].state.encumbrance).isNotNull.extracting { it?.tag }.isEqualTo("encumbrance 2")
        assertThat(tx.outputStateAndRefs[3].state.encumbrance).isNotNull.extracting { it?.size }.isEqualTo(3)

        assertThat(tx.outputStateAndRefs[4].state.encumbrance).isNotNull.extracting { it?.tag }.isEqualTo("encumbrance 2")
        assertThat(tx.outputStateAndRefs[4].state.encumbrance).isNotNull.extracting { it?.size }.isEqualTo(3)

        assertThat(tx.outputStateAndRefs[5].state.encumbrance).isNotNull.extracting { it?.tag }.isEqualTo("encumbrance 2")
        assertThat(tx.outputStateAndRefs[5].state.encumbrance).isNotNull.extracting { it?.size }.isEqualTo(3)

        assertThat(tx.outputStateAndRefs[6].state.encumbrance).isNotNull.extracting { it?.tag }.isEqualTo("encumbrance 1")
        assertThat(tx.outputStateAndRefs[6].state.encumbrance).isNotNull.extracting { it?.size }.isEqualTo(3)
    }

    @Test
    fun `setting the notary mutates and returns the current builder`() {
        val originalTransactionBuilder = utxoTransactionBuilder
        val mutatedTransactionBuilder = utxoTransactionBuilder.setNotary(utxoNotaryExample)
        assertThat(mutatedTransactionBuilder.notary).isEqualTo(utxoNotaryExample)
        assertThat(mutatedTransactionBuilder).isEqualTo(originalTransactionBuilder)
        assertThat(System.identityHashCode(mutatedTransactionBuilder)).isEqualTo(System.identityHashCode(originalTransactionBuilder))
    }

    @Test
    fun `setting the time window mutates and returns the current builder`() {
        val originalTransactionBuilder = utxoTransactionBuilder
        val mutatedTransactionBuilder = utxoTransactionBuilder
            .setTimeWindowBetween(utxoTimeWindowExample.from, utxoTimeWindowExample.until)
        assertThat((mutatedTransactionBuilder as UtxoTransactionBuilderInternal).timeWindow).isEqualTo(utxoTimeWindowExample)
        assertThat(mutatedTransactionBuilder).isEqualTo(originalTransactionBuilder)
        assertThat(System.identityHashCode(mutatedTransactionBuilder)).isEqualTo(System.identityHashCode(originalTransactionBuilder))
    }

    @Test
    fun `adding attachments mutates and returns the current builder`() {
        val attachmentId = SecureHash("SHA-256", ByteArray(12))
        val originalTransactionBuilder = utxoTransactionBuilder
        val mutatedTransactionBuilder = utxoTransactionBuilder.addAttachment(attachmentId)
        assertThat((mutatedTransactionBuilder as UtxoTransactionBuilderInternal).attachments).isEqualTo(listOf(attachmentId))
        assertThat(mutatedTransactionBuilder).isEqualTo(originalTransactionBuilder)
        assertThat(System.identityHashCode(mutatedTransactionBuilder)).isEqualTo(System.identityHashCode(originalTransactionBuilder))
    }

    @Test
    fun `adding commands mutates and returns the current builder`() {
        val command = UtxoCommandExample()
        val originalTransactionBuilder = utxoTransactionBuilder
        val mutatedTransactionBuilder = utxoTransactionBuilder.addCommand(command)
        assertThat((mutatedTransactionBuilder as UtxoTransactionBuilderInternal).commands).isEqualTo(listOf(command))
        assertThat(mutatedTransactionBuilder).isEqualTo(originalTransactionBuilder)
        assertThat(System.identityHashCode(mutatedTransactionBuilder)).isEqualTo(System.identityHashCode(originalTransactionBuilder))
    }

    @Test
    fun `adding signatories mutates and returns the current builder`() {
        val signatories = listOf(publicKeyExample)
        val originalTransactionBuilder = utxoTransactionBuilder
        val mutatedTransactionBuilder = utxoTransactionBuilder.addSignatories(signatories)
        assertThat((mutatedTransactionBuilder as UtxoTransactionBuilderInternal).signatories).isEqualTo(signatories)
        assertThat(mutatedTransactionBuilder).isEqualTo(originalTransactionBuilder)
        assertThat(System.identityHashCode(mutatedTransactionBuilder)).isEqualTo(System.identityHashCode(originalTransactionBuilder))
    }

    @Test
    fun `adding input states mutates and returns the current builder`() {
        val inputState = utxoStateAndRefExample.ref
        val originalTransactionBuilder = utxoTransactionBuilder

        val mutatedTransactionBuilder = utxoTransactionBuilder.addInputState(inputState)
        assertThat((mutatedTransactionBuilder as UtxoTransactionBuilderInternal).inputStateRefs).isEqualTo(listOf(inputState))
        assertThat(mutatedTransactionBuilder).isEqualTo(originalTransactionBuilder)
        assertThat(System.identityHashCode(mutatedTransactionBuilder)).isEqualTo(System.identityHashCode(originalTransactionBuilder))

        val mutatedTransactionBuilder2 = utxoTransactionBuilder.addInputStates(listOf(inputState))
        assertThat((mutatedTransactionBuilder2 as UtxoTransactionBuilderInternal).inputStateRefs).isEqualTo(listOf(inputState, inputState))
        assertThat(mutatedTransactionBuilder).isEqualTo(originalTransactionBuilder)
        assertThat(System.identityHashCode(mutatedTransactionBuilder2)).isEqualTo(System.identityHashCode(originalTransactionBuilder))

        val mutatedTransactionBuilder3 = utxoTransactionBuilder.addInputStates(inputState)
        assertThat((mutatedTransactionBuilder3 as UtxoTransactionBuilderInternal).inputStateRefs)
            .isEqualTo(listOf(inputState, inputState, inputState))
        assertThat(mutatedTransactionBuilder).isEqualTo(originalTransactionBuilder)
        assertThat(System.identityHashCode(mutatedTransactionBuilder3)).isEqualTo(System.identityHashCode(originalTransactionBuilder))
    }

    @Test
    fun `adding reference states mutates and returns the current builder`() {
        val referenceState = utxoStateAndRefExample.ref
        val originalTransactionBuilder = utxoTransactionBuilder

        val mutatedTransactionBuilder = utxoTransactionBuilder.addReferenceState(referenceState)
        assertThat((mutatedTransactionBuilder as UtxoTransactionBuilderInternal).referenceStateRefs).isEqualTo(listOf(referenceState))
        assertThat(mutatedTransactionBuilder).isEqualTo(originalTransactionBuilder)
        assertThat(System.identityHashCode(mutatedTransactionBuilder)).isEqualTo(System.identityHashCode(originalTransactionBuilder))

        val mutatedTransactionBuilder2 = utxoTransactionBuilder.addReferenceStates(listOf(referenceState))
        assertThat((mutatedTransactionBuilder2 as UtxoTransactionBuilderInternal).referenceStateRefs)
            .isEqualTo(listOf(referenceState, referenceState))
        assertThat(mutatedTransactionBuilder).isEqualTo(originalTransactionBuilder)
        assertThat(System.identityHashCode(mutatedTransactionBuilder2)).isEqualTo(System.identityHashCode(originalTransactionBuilder))

        val mutatedTransactionBuilder3 = utxoTransactionBuilder.addReferenceStates(referenceState)
        assertThat((mutatedTransactionBuilder3 as UtxoTransactionBuilderInternal).referenceStateRefs)
            .isEqualTo(listOf(referenceState, referenceState, referenceState))
        assertThat(mutatedTransactionBuilder).isEqualTo(originalTransactionBuilder)
        assertThat(System.identityHashCode(mutatedTransactionBuilder3)).isEqualTo(System.identityHashCode(originalTransactionBuilder))
    }

    @Test
    fun `adding output states mutates and returns the current builder`() {
        val originalTransactionBuilder = utxoTransactionBuilder

        val mutatedTransactionBuilder = utxoTransactionBuilder.addOutputState(utxoStateExample)
        assertThat((mutatedTransactionBuilder as UtxoTransactionBuilderInternal).outputStates).isEqualTo(
            listOf(
                ContractStateAndEncumbranceTag(utxoStateExample, null)
            )
        )
        assertThat(mutatedTransactionBuilder).isEqualTo(originalTransactionBuilder)
        assertThat(System.identityHashCode(mutatedTransactionBuilder)).isEqualTo(System.identityHashCode(originalTransactionBuilder))

        val mutatedTransactionBuilder2 = utxoTransactionBuilder.addOutputStates(listOf(utxoStateExample))
        assertThat((mutatedTransactionBuilder2 as UtxoTransactionBuilderInternal).outputStates).isEqualTo(
            listOf(
                ContractStateAndEncumbranceTag(utxoStateExample, null),
                ContractStateAndEncumbranceTag(utxoStateExample, null)
            )
        )
        assertThat(mutatedTransactionBuilder).isEqualTo(originalTransactionBuilder)
        assertThat(System.identityHashCode(mutatedTransactionBuilder2)).isEqualTo(System.identityHashCode(originalTransactionBuilder))

        val mutatedTransactionBuilder3 = utxoTransactionBuilder.addOutputStates(utxoStateExample)
        assertThat((mutatedTransactionBuilder3 as UtxoTransactionBuilderInternal).outputStates).isEqualTo(
            listOf(
                ContractStateAndEncumbranceTag(utxoStateExample, null),
                ContractStateAndEncumbranceTag(utxoStateExample, null),
                ContractStateAndEncumbranceTag(utxoStateExample, null)
            )
        )
        assertThat(mutatedTransactionBuilder).isEqualTo(originalTransactionBuilder)
        assertThat(System.identityHashCode(mutatedTransactionBuilder3)).isEqualTo(System.identityHashCode(originalTransactionBuilder))
    }

    @Test
    fun `adding output states with an encumbrance group mutates and returns the current builder`() {
        val tag = "tag"
        val originalTransactionBuilder = utxoTransactionBuilder

        val mutatedTransactionBuilder = utxoTransactionBuilder.addEncumberedOutputStates(tag, listOf(utxoStateExample))
        assertThat((mutatedTransactionBuilder as UtxoTransactionBuilderInternal).outputStates).isEqualTo(
            listOf(
                ContractStateAndEncumbranceTag(utxoStateExample, tag)
            )
        )
        assertThat(mutatedTransactionBuilder).isEqualTo(originalTransactionBuilder)
        assertThat(System.identityHashCode(mutatedTransactionBuilder)).isEqualTo(System.identityHashCode(originalTransactionBuilder))

        val mutatedTransactionBuilder2 = utxoTransactionBuilder.addEncumberedOutputStates(tag, utxoStateExample)
        assertThat((mutatedTransactionBuilder2 as UtxoTransactionBuilderInternal).outputStates).isEqualTo(
            listOf(
                ContractStateAndEncumbranceTag(utxoStateExample, tag),
                ContractStateAndEncumbranceTag(utxoStateExample, tag)
            )
        )
        assertThat(mutatedTransactionBuilder).isEqualTo(originalTransactionBuilder)
        assertThat(System.identityHashCode(mutatedTransactionBuilder2)).isEqualTo(System.identityHashCode(originalTransactionBuilder))
    }
}
