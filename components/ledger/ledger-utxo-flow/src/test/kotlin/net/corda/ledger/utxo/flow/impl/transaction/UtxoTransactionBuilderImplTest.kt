package net.corda.ledger.utxo.flow.impl.transaction

import net.corda.crypto.core.SecureHashImpl
import net.corda.ledger.common.data.transaction.CordaPackageSummaryImpl
import net.corda.ledger.common.data.transaction.TransactionMetadataInternal
import net.corda.ledger.common.test.dummyCpkSignerSummaryHash
import net.corda.ledger.common.testkit.publicKeyExample
import net.corda.ledger.utxo.test.UtxoLedgerTest
import net.corda.ledger.utxo.testkit.UtxoCommandExample
import net.corda.ledger.utxo.testkit.UtxoStateClassExample
import net.corda.ledger.utxo.testkit.getExampleStateAndRefImpl
import net.corda.ledger.utxo.testkit.getUtxoStateExample
import net.corda.ledger.utxo.testkit.utxoNotaryExample
import net.corda.ledger.utxo.testkit.utxoTimeWindowExample
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.utxo.StateRef
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import kotlin.test.assertIs

class UtxoTransactionBuilderImplTest : UtxoLedgerTest() {
    private val stateRef1 = StateRef(SecureHashImpl("SHA", byteArrayOf(1, 1, 1, 1)), 0)
    private val stateRef2 = StateRef(SecureHashImpl("SHA", byteArrayOf(1, 1, 1, 2)), 0)
    private val stateRef3 = StateRef(SecureHashImpl("SHA", byteArrayOf(1, 1, 1, 3)), 0)
    private val state1 = UtxoStateClassExample("test 1", listOf(publicKeyExample))
    private val state2 = UtxoStateClassExample("test 2", listOf(publicKeyExample))
    private val state3 = UtxoStateClassExample("test 3", listOf(publicKeyExample))

    @Test
    fun `can build a simple Transaction`() {

        val inputStateAndRef = getExampleStateAndRefImpl()
        val inputStateRef = inputStateAndRef.ref
        val referenceStateAndRef = getExampleStateAndRefImpl(2)
        val referenceStateRef = referenceStateAndRef.ref

        whenever(mockUtxoLedgerStateQueryService.resolveStateRefs(any()))
            .thenReturn(listOf(inputStateAndRef, referenceStateAndRef))

        val tx = utxoTransactionBuilder
            .setNotary(utxoNotaryExample)
            .setTimeWindowBetween(utxoTimeWindowExample.from, utxoTimeWindowExample.until)
            .addOutputState(getUtxoStateExample())
            .addInputState(inputStateRef)
            .addReferenceState(referenceStateRef)
            .addSignatories(listOf(publicKeyExample))
            .addCommand(UtxoCommandExample())
            .addAttachment(SecureHashImpl("SHA-256", ByteArray(12)))
            .toSignedTransaction()
        assertIs<SecureHash>(tx.id)
        assertEquals(inputStateRef, tx.inputStateRefs.single())
        assertEquals(referenceStateRef, tx.referenceStateRefs.single())
        assertEquals(getUtxoStateExample(), tx.outputStateAndRefs.single().state.contractState)
        assertEquals(utxoNotaryExample, tx.notary)
        assertEquals(utxoTimeWindowExample, tx.timeWindow)
        assertEquals(publicKeyExample, tx.signatories.first())
    }

    @Test
    fun `can build a simple Transaction with empty component groups`() {
        val tx = utxoTransactionBuilder
            .setNotary(utxoNotaryExample)
            .setTimeWindowBetween(utxoTimeWindowExample.from, utxoTimeWindowExample.until)
            .addOutputState(getUtxoStateExample())
            .addSignatories(listOf(publicKeyExample))
            .addCommand(UtxoCommandExample())
            .toSignedTransaction()
        assertIs<SecureHash>(tx.id)
        assertThat(tx.inputStateRefs).isEmpty()
        assertThat(tx.referenceStateRefs).isEmpty()
        assertEquals(getUtxoStateExample(), tx.outputStateAndRefs.single().state.contractState)
        assertEquals(utxoNotaryExample, tx.notary)
        assertEquals(utxoTimeWindowExample, tx.timeWindow)
        assertEquals(publicKeyExample, tx.signatories.first())
    }

    @Test
    fun `includes CPI and CPK information in metadata`() {
        val tx = utxoTransactionBuilder
            .setNotary(utxoNotaryExample)
            .setTimeWindowBetween(utxoTimeWindowExample.from, utxoTimeWindowExample.until)
            .addOutputState(getUtxoStateExample())
            .addSignatories(listOf(publicKeyExample))
            .addCommand(UtxoCommandExample())
            .addAttachment(SecureHashImpl("SHA-256", ByteArray(12)))
            .toSignedTransaction() as UtxoSignedTransactionImpl

        val metadata = tx.wireTransaction.metadata as TransactionMetadataInternal
        assertEquals(1, metadata.ledgerVersion)

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
    fun `can't sign twice`() {
        assertThrows(IllegalStateException::class.java) {
            val builder = utxoTransactionBuilder
                .setNotary(utxoNotaryExample)
                .setTimeWindowBetween(utxoTimeWindowExample.from, utxoTimeWindowExample.until)
                .addOutputState(getUtxoStateExample())
                .addSignatories(listOf(publicKeyExample))
                .addCommand(UtxoCommandExample())
                .addAttachment(SecureHashImpl("SHA-256", ByteArray(12)))

            builder.toSignedTransaction()
            builder.toSignedTransaction()
        }
    }

    @Test
    fun `Calculate encumbranceGroup groups correctly`() {
        val inputStateAndRef = getExampleStateAndRefImpl()
        val inputStateRef = inputStateAndRef.ref
        val referenceStateAndRef = getExampleStateAndRefImpl(2)
        val referenceStateRef = referenceStateAndRef.ref

        whenever(mockUtxoLedgerStateQueryService.resolveStateRefs(any()))
            .thenReturn(listOf(inputStateAndRef, referenceStateAndRef))

        val tx = utxoTransactionBuilder
            .setNotary(utxoNotaryExample)
            .setTimeWindowBetween(utxoTimeWindowExample.from, utxoTimeWindowExample.until)
            .addEncumberedOutputStates(
                "encumbranceGroup 1",
                UtxoStateClassExample("test 1", listOf(publicKeyExample)),
                UtxoStateClassExample("test 2", listOf(publicKeyExample))
            )
            .addOutputState(getUtxoStateExample())
            .addEncumberedOutputStates(
                "encumbranceGroup 2",
                UtxoStateClassExample("test 3", listOf(publicKeyExample)),
                UtxoStateClassExample("test 4", listOf(publicKeyExample)),
                UtxoStateClassExample("test 5", listOf(publicKeyExample))
            )
            .addEncumberedOutputStates(
                "encumbranceGroup 1",
                UtxoStateClassExample("test 6", listOf(publicKeyExample))
            )
            .addInputState(inputStateRef)
            .addReferenceState(referenceStateRef)
            .addSignatories(listOf(publicKeyExample))
            .addCommand(UtxoCommandExample())
            .addAttachment(SecureHashImpl("SHA-256", ByteArray(12)))
            .toSignedTransaction()

        assertThat(tx.outputStateAndRefs).hasSize(7)
        assertThat(tx.outputStateAndRefs[0].state.encumbranceGroup).isNotNull.extracting { it?.tag }
            .isEqualTo("encumbranceGroup 1")
        assertThat(tx.outputStateAndRefs[0].state.encumbranceGroup).isNotNull.extracting { it?.size }.isEqualTo(3)

        assertThat(tx.outputStateAndRefs[1].state.encumbranceGroup).isNotNull.extracting { it?.tag }
            .isEqualTo("encumbranceGroup 1")
        assertThat(tx.outputStateAndRefs[1].state.encumbranceGroup).isNotNull.extracting { it?.size }.isEqualTo(3)

        assertThat(tx.outputStateAndRefs[2].state.encumbranceGroup).isNull()

        assertThat(tx.outputStateAndRefs[3].state.encumbranceGroup).isNotNull.extracting { it?.tag }
            .isEqualTo("encumbranceGroup 2")
        assertThat(tx.outputStateAndRefs[3].state.encumbranceGroup).isNotNull.extracting { it?.size }.isEqualTo(3)

        assertThat(tx.outputStateAndRefs[4].state.encumbranceGroup).isNotNull.extracting { it?.tag }
            .isEqualTo("encumbranceGroup 2")
        assertThat(tx.outputStateAndRefs[4].state.encumbranceGroup).isNotNull.extracting { it?.size }.isEqualTo(3)

        assertThat(tx.outputStateAndRefs[5].state.encumbranceGroup).isNotNull.extracting { it?.tag }
            .isEqualTo("encumbranceGroup 2")
        assertThat(tx.outputStateAndRefs[5].state.encumbranceGroup).isNotNull.extracting { it?.size }.isEqualTo(3)

        assertThat(tx.outputStateAndRefs[6].state.encumbranceGroup).isNotNull.extracting { it?.tag }
            .isEqualTo("encumbranceGroup 1")
        assertThat(tx.outputStateAndRefs[6].state.encumbranceGroup).isNotNull.extracting { it?.size }.isEqualTo(3)
    }

    @Test
    fun `setting the notary mutates and returns the current builder`() {
        val originalTransactionBuilder = utxoTransactionBuilder
        val mutatedTransactionBuilder = utxoTransactionBuilder.setNotary(utxoNotaryExample)
        assertThat(mutatedTransactionBuilder.notary).isEqualTo(utxoNotaryExample)
        assertThat(mutatedTransactionBuilder).isEqualTo(originalTransactionBuilder)
        assertThat(System.identityHashCode(mutatedTransactionBuilder)).isEqualTo(
            System.identityHashCode(
                originalTransactionBuilder
            )
        )
    }

    @Test
    fun `setting the time window mutates and returns the current builder`() {
        val originalTransactionBuilder = utxoTransactionBuilder
        val mutatedTransactionBuilder = utxoTransactionBuilder
            .setTimeWindowBetween(utxoTimeWindowExample.from, utxoTimeWindowExample.until)
        assertThat((mutatedTransactionBuilder as UtxoTransactionBuilderInternal).timeWindow).isEqualTo(
            utxoTimeWindowExample
        )
        assertThat(mutatedTransactionBuilder).isEqualTo(originalTransactionBuilder)
        assertThat(System.identityHashCode(mutatedTransactionBuilder)).isEqualTo(
            System.identityHashCode(
                originalTransactionBuilder
            )
        )
    }

    @Test
    fun `adding attachments mutates and returns the current builder`() {
        val attachmentId = SecureHashImpl("SHA-256", ByteArray(12))
        val originalTransactionBuilder = utxoTransactionBuilder
        val mutatedTransactionBuilder = utxoTransactionBuilder.addAttachment(attachmentId)
        assertThat((mutatedTransactionBuilder as UtxoTransactionBuilderInternal).attachments).isEqualTo(
            listOf(
                attachmentId
            )
        )
        assertThat(mutatedTransactionBuilder).isEqualTo(originalTransactionBuilder)
        assertThat(System.identityHashCode(mutatedTransactionBuilder)).isEqualTo(
            System.identityHashCode(
                originalTransactionBuilder
            )
        )
    }

    @Test
    fun `adding commands mutates and returns the current builder`() {
        val command = UtxoCommandExample()
        val originalTransactionBuilder = utxoTransactionBuilder
        val mutatedTransactionBuilder = utxoTransactionBuilder.addCommand(command)
        assertThat((mutatedTransactionBuilder as UtxoTransactionBuilderInternal).commands).isEqualTo(listOf(command))
        assertThat(mutatedTransactionBuilder).isEqualTo(originalTransactionBuilder)
        assertThat(System.identityHashCode(mutatedTransactionBuilder)).isEqualTo(
            System.identityHashCode(
                originalTransactionBuilder
            )
        )
    }

    @Test
    fun `adding signatories mutates and returns the current builder`() {
        val signatories = listOf(publicKeyExample)
        val originalTransactionBuilder = utxoTransactionBuilder
        val mutatedTransactionBuilder = utxoTransactionBuilder.addSignatories(signatories)
        assertThat((mutatedTransactionBuilder as UtxoTransactionBuilderInternal).signatories).isEqualTo(signatories)
        assertThat(mutatedTransactionBuilder).isEqualTo(originalTransactionBuilder)
        assertThat(System.identityHashCode(mutatedTransactionBuilder)).isEqualTo(
            System.identityHashCode(
                originalTransactionBuilder
            )
        )
    }

    @Test
    fun `adding input states mutates and returns the current builder`() {
        val originalTransactionBuilder = utxoTransactionBuilder

        val mutatedTransactionBuilder = utxoTransactionBuilder.addInputState(stateRef1)
        assertThat((mutatedTransactionBuilder as UtxoTransactionBuilderInternal).inputStateRefs).isEqualTo(
            listOf(
                stateRef1
            )
        )
        assertThat(mutatedTransactionBuilder).isEqualTo(originalTransactionBuilder)
        assertThat(System.identityHashCode(mutatedTransactionBuilder)).isEqualTo(
            System.identityHashCode(
                originalTransactionBuilder
            )
        )

        val mutatedTransactionBuilder2 = utxoTransactionBuilder.addInputStates(listOf(stateRef2))
        assertThat((mutatedTransactionBuilder2 as UtxoTransactionBuilderInternal).inputStateRefs).isEqualTo(
            listOf(
                stateRef1,
                stateRef2
            )
        )
        assertThat(mutatedTransactionBuilder).isEqualTo(originalTransactionBuilder)
        assertThat(System.identityHashCode(mutatedTransactionBuilder2)).isEqualTo(
            System.identityHashCode(
                originalTransactionBuilder
            )
        )

        val mutatedTransactionBuilder3 = utxoTransactionBuilder.addInputStates(stateRef3)
        assertThat((mutatedTransactionBuilder3 as UtxoTransactionBuilderInternal).inputStateRefs)
            .isEqualTo(listOf(stateRef1, stateRef2, stateRef3))
        assertThat(mutatedTransactionBuilder).isEqualTo(originalTransactionBuilder)
        assertThat(System.identityHashCode(mutatedTransactionBuilder3)).isEqualTo(
            System.identityHashCode(
                originalTransactionBuilder
            )
        )
    }

    @Test
    fun `adding reference states mutates and returns the current builder`() {
        val originalTransactionBuilder = utxoTransactionBuilder

        val mutatedTransactionBuilder = utxoTransactionBuilder.addReferenceState(stateRef1)
        assertThat((mutatedTransactionBuilder as UtxoTransactionBuilderInternal).referenceStateRefs).isEqualTo(
            listOf(
                stateRef1
            )
        )
        assertThat(mutatedTransactionBuilder).isEqualTo(originalTransactionBuilder)
        assertThat(System.identityHashCode(mutatedTransactionBuilder)).isEqualTo(
            System.identityHashCode(
                originalTransactionBuilder
            )
        )

        val mutatedTransactionBuilder2 = utxoTransactionBuilder.addReferenceStates(listOf(stateRef2))
        assertThat((mutatedTransactionBuilder2 as UtxoTransactionBuilderInternal).referenceStateRefs)
            .isEqualTo(listOf(stateRef1, stateRef2))
        assertThat(mutatedTransactionBuilder).isEqualTo(originalTransactionBuilder)
        assertThat(System.identityHashCode(mutatedTransactionBuilder2)).isEqualTo(
            System.identityHashCode(
                originalTransactionBuilder
            )
        )

        val mutatedTransactionBuilder3 = utxoTransactionBuilder.addReferenceStates(stateRef3)
        assertThat((mutatedTransactionBuilder3 as UtxoTransactionBuilderInternal).referenceStateRefs)
            .isEqualTo(listOf(stateRef1, stateRef2, stateRef3))
        assertThat(mutatedTransactionBuilder).isEqualTo(originalTransactionBuilder)
        assertThat(System.identityHashCode(mutatedTransactionBuilder3)).isEqualTo(
            System.identityHashCode(
                originalTransactionBuilder
            )
        )
    }

    @Test
    fun `adding output states mutates and returns the current builder`() {
        val originalTransactionBuilder = utxoTransactionBuilder

        val mutatedTransactionBuilder = utxoTransactionBuilder.addOutputState(state1)
        assertThat((mutatedTransactionBuilder as UtxoTransactionBuilderInternal).outputStates).isEqualTo(
            listOf(ContractStateAndEncumbranceTag(state1, null))
        )
        assertThat(mutatedTransactionBuilder).isEqualTo(originalTransactionBuilder)
        assertThat(System.identityHashCode(mutatedTransactionBuilder)).isEqualTo(
            System.identityHashCode(
                originalTransactionBuilder
            )
        )

        val mutatedTransactionBuilder2 = utxoTransactionBuilder.addOutputStates(listOf(state2))
        assertThat((mutatedTransactionBuilder2 as UtxoTransactionBuilderInternal).outputStates).isEqualTo(
            listOf(
                ContractStateAndEncumbranceTag(state1, null),
                ContractStateAndEncumbranceTag(state2, null)
            )
        )
        assertThat(mutatedTransactionBuilder).isEqualTo(originalTransactionBuilder)
        assertThat(System.identityHashCode(mutatedTransactionBuilder2)).isEqualTo(
            System.identityHashCode(
                originalTransactionBuilder
            )
        )

        val mutatedTransactionBuilder3 = utxoTransactionBuilder.addOutputStates(state3)
        assertThat((mutatedTransactionBuilder3 as UtxoTransactionBuilderInternal).outputStates).isEqualTo(
            listOf(
                ContractStateAndEncumbranceTag(state1, null),
                ContractStateAndEncumbranceTag(state2, null),
                ContractStateAndEncumbranceTag(state3, null)
            )
        )
        assertThat(mutatedTransactionBuilder).isEqualTo(originalTransactionBuilder)
        assertThat(System.identityHashCode(mutatedTransactionBuilder3)).isEqualTo(
            System.identityHashCode(
                originalTransactionBuilder
            )
        )
    }

    @Test
    fun `adding output states with an encumbranceGroup group mutates and returns the current builder`() {
        val tag = "tag"
        val originalTransactionBuilder = utxoTransactionBuilder

        val mutatedTransactionBuilder = utxoTransactionBuilder.addEncumberedOutputStates(tag, listOf(state1))
        assertThat((mutatedTransactionBuilder as UtxoTransactionBuilderInternal).outputStates).isEqualTo(
            listOf(ContractStateAndEncumbranceTag(state1, tag))
        )
        assertThat(mutatedTransactionBuilder).isEqualTo(originalTransactionBuilder)
        assertThat(System.identityHashCode(mutatedTransactionBuilder)).isEqualTo(
            System.identityHashCode(
                originalTransactionBuilder
            )
        )

        val mutatedTransactionBuilder2 = utxoTransactionBuilder.addEncumberedOutputStates(tag, state2)
        assertThat((mutatedTransactionBuilder2 as UtxoTransactionBuilderInternal).outputStates).isEqualTo(
            listOf(
                ContractStateAndEncumbranceTag(state1, tag),
                ContractStateAndEncumbranceTag(state2, tag)
            )
        )
        assertThat(mutatedTransactionBuilder).isEqualTo(originalTransactionBuilder)
        assertThat(System.identityHashCode(mutatedTransactionBuilder2)).isEqualTo(
            System.identityHashCode(
                originalTransactionBuilder
            )
        )
    }

    @Test
    fun `Duplicating attachments throws`() {
        val attachmentId = SecureHashImpl("SHA", byteArrayOf(1, 1, 1, 1))
        utxoTransactionBuilder
            .addAttachment(attachmentId)
        assertThatThrownBy {
            utxoTransactionBuilder
                .addAttachment(attachmentId)
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `Duplicating signatories throws`() {
        assertThatThrownBy {
            utxoTransactionBuilder
                .addSignatories(List(2) { publicKeyExample })
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `Duplicating input states one by one throws`() {
        utxoTransactionBuilder
            .addInputState(stateRef1)
        assertThatThrownBy {
            utxoTransactionBuilder
                .addInputState(stateRef1)
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `Duplicating input states adding as a list throws`() {
        assertThatThrownBy {
            utxoTransactionBuilder
                .addInputStates(List(2) { stateRef1 })
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `Duplicating reference states one by one throws`() {
        utxoTransactionBuilder
            .addReferenceState(stateRef1)
        assertThatThrownBy {
            utxoTransactionBuilder
                .addReferenceState(stateRef1)
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `Duplicating reference states adding as a list throws`() {
        assertThatThrownBy {
            utxoTransactionBuilder
                .addReferenceStates(List(2) { stateRef1 })
        }.isInstanceOf(IllegalArgumentException::class.java)
    }
}
