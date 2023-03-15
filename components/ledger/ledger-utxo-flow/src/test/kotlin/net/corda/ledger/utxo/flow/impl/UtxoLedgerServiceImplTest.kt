package net.corda.ledger.utxo.flow.impl

import net.corda.crypto.core.SecureHashImpl
import net.corda.flow.pipeline.sandbox.FlowSandboxGroupContext
import net.corda.flow.pipeline.sessions.protocol.FlowProtocolStore
import net.corda.ledger.common.testkit.publicKeyExample
import net.corda.ledger.utxo.test.UtxoLedgerTest
import net.corda.ledger.utxo.testkit.UtxoCommandExample
import net.corda.ledger.utxo.testkit.UtxoStateClassExample
import net.corda.ledger.utxo.testkit.getExampleStateAndRefImpl
import net.corda.ledger.utxo.testkit.getUtxoStateExample
import net.corda.ledger.utxo.testkit.utxoNotaryExample
import net.corda.ledger.utxo.testkit.utxoTimeWindowExample
import net.corda.sandbox.SandboxGroup
import net.corda.sandboxgroupcontext.VirtualNodeContext
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.notary.plugin.api.PluggableNotaryClientFlow
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import net.corda.v5.ledger.utxo.transaction.UtxoTransactionBuilder
import net.corda.v5.membership.NotaryInfo
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.security.PublicKey
import kotlin.test.assertEquals
import kotlin.test.assertIs

class UtxoLedgerServiceImplTest: UtxoLedgerTest() {

    @Test
    fun `getTransactionBuilder should return a Transaction Builder`() {
        val transactionBuilder = utxoLedgerService.getTransactionBuilder()
        assertIs<UtxoTransactionBuilder>(transactionBuilder)
    }

    @Test
    fun `UtxoLedgerServiceImpl's getTransactionBuilder() can build a SignedTransaction`() {
        val transactionBuilder = utxoLedgerService.getTransactionBuilder()

        val inputStateAndRef = getExampleStateAndRefImpl(1)
        val inputStateRef = inputStateAndRef.ref
        val referenceStateAndRef = getExampleStateAndRefImpl(2)
        val referenceStateRef = referenceStateAndRef.ref

        whenever(mockUtxoLedgerStateQueryService.resolveStateRefs(any()))
            .thenReturn(listOf(inputStateAndRef, referenceStateAndRef))

        val command = UtxoCommandExample()
        val attachment = SecureHashImpl("SHA-256", ByteArray(12))

        val signedTransaction = transactionBuilder
            .setNotary(utxoNotaryExample)
            .setTimeWindowBetween(utxoTimeWindowExample.from, utxoTimeWindowExample.until)
            .addOutputState(getUtxoStateExample())
            .addInputState(inputStateRef)
            .addReferenceState(referenceStateRef)
            .addSignatories(listOf(publicKeyExample))
            .addCommand(command)
            .addAttachment(attachment)
            .toSignedTransaction()

        assertIs<UtxoSignedTransaction>(signedTransaction)
        assertIs<SecureHash>(signedTransaction.id)

        val ledgerTransaction = signedTransaction.toLedgerTransaction()

        assertIs<SecureHash>(ledgerTransaction.id)

        Assertions.assertEquals(utxoTimeWindowExample, ledgerTransaction.timeWindow)

        assertIs<List<ContractState>>(ledgerTransaction.outputContractStates)
        Assertions.assertEquals(1, ledgerTransaction.outputContractStates.size)
        Assertions.assertEquals(getUtxoStateExample(), ledgerTransaction.outputContractStates.first())
        assertIs<UtxoStateClassExample>(ledgerTransaction.outputContractStates.first())

        assertIs<List<PublicKey>>(ledgerTransaction.signatories)
        Assertions.assertEquals(1, ledgerTransaction.signatories.size)
        Assertions.assertEquals(publicKeyExample, ledgerTransaction.signatories.first())
        assertIs<PublicKey>(ledgerTransaction.signatories.first())
    }

    @Test
    fun `getPluggableNotaryClientFlow fails for an unknown notary service`() {

        val notaryService = mock<NotaryInfo>().apply {
            whenever(this.name).thenReturn(utxoNotaryExample.name)
        }

        whenever(mockNotaryLookup.notaryServices).thenReturn(listOf(notaryService))

        assertThatThrownBy {
            utxoLedgerService.getPluggableNotaryClientFlow(Party(MemberX500Name.parse(
                "O=ExampleNotaryService, L=London, C=GB"), publicKeyExample))
        }
            .isInstanceOf(CordaRuntimeException::class.java)
            .hasMessageContaining("Plugin class not found for notary service")
    }

    @Test
    fun `getPluggableNotaryClientFlow fails with plugin that does not inherit from base class`() {

        class MyClientFlow

        val notaryService = mock<NotaryInfo>().apply {
            whenever(this.name).thenReturn(utxoNotaryExample.name)
            whenever(this.pluginClass).thenReturn("my-client-flow")
        }

        val sandboxGroup = mock<SandboxGroup>().apply {
            whenever(this.loadClassFromMainBundles(any())).thenReturn(MyClientFlow::class.java)
        }

        val protocolStore = mock<FlowProtocolStore>().apply {
            whenever(this.initiatorForProtocol(any(),any())).thenReturn(MyClientFlow::class.java.name)
        }

        val virtualNodeContext = mock<VirtualNodeContext>().apply {
            whenever(this.holdingIdentity).doReturn(mock())
        }

        val flowSandboxGroupContext = mock<FlowSandboxGroupContext>().apply {
            whenever(this.get<FlowProtocolStore>(any(), any())).thenReturn(protocolStore)
            whenever(this.sandboxGroup).thenReturn(sandboxGroup)
            whenever(this.virtualNodeContext).thenReturn(virtualNodeContext)
            whenever(this.protocolStore).thenReturn(protocolStore)
        }

        whenever(mockNotaryLookup.notaryServices).thenReturn(listOf(notaryService))

        whenever(mockCurrentSandboxGroupContext.get()).thenReturn(flowSandboxGroupContext)

        whenever(mockFlowSandboxService.get(any(), any())).thenReturn(flowSandboxGroupContext)

        assertThatThrownBy {
            utxoLedgerService.getPluggableNotaryClientFlow(utxoNotaryExample)
        }
            .isInstanceOf(CordaRuntimeException::class.java)
            .hasMessageContaining("is invalid because it does not inherit from")
    }

    @Test
    fun `getPluggableNotaryClientFlow succeeds with valid notary service and plugin`() {

        class MyClientFlow : PluggableNotaryClientFlow {
            override fun call(): List<DigitalSignatureAndMetadata> { return emptyList() }
        }

        val notaryService = mock<NotaryInfo>().apply {
            whenever(this.name).thenReturn(utxoNotaryExample.name)
            whenever(this.pluginClass).thenReturn("my-client-flow")
        }

        val sandboxGroup = mock<SandboxGroup>().apply {
            whenever(this.loadClassFromMainBundles(any())).thenReturn(MyClientFlow::class.java)
        }

        val protocolStore = mock<FlowProtocolStore>().apply {
            whenever(this.initiatorForProtocol(any(),any())).thenReturn(MyClientFlow::class.java.name)
        }

        val virtualNodeContext = mock<VirtualNodeContext>().apply {
            whenever(this.holdingIdentity).doReturn(mock())
        }

        val flowSandboxGroupContext = mock<FlowSandboxGroupContext>().apply {
            whenever(this.get<FlowProtocolStore>(any(), any())).thenReturn(protocolStore)
            whenever(this.sandboxGroup).thenReturn(sandboxGroup)
            whenever(this.virtualNodeContext).thenReturn(virtualNodeContext)
            whenever(this.protocolStore).thenReturn(protocolStore)
        }

        whenever(mockNotaryLookup.notaryServices).thenReturn(listOf(notaryService))

        whenever(mockCurrentSandboxGroupContext.get()).thenReturn(flowSandboxGroupContext)

        whenever(mockFlowSandboxService.get(any(), any())).thenReturn(flowSandboxGroupContext)

        val result = assertDoesNotThrow {
            utxoLedgerService.getPluggableNotaryClientFlow(utxoNotaryExample)
        }

        assertEquals(MyClientFlow::class.java.name, result.name)
    }
}
