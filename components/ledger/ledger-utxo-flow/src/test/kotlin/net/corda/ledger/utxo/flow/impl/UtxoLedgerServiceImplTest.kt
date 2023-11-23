package net.corda.ledger.utxo.flow.impl

import net.corda.flow.pipeline.exceptions.FlowFatalException
import net.corda.flow.pipeline.sandbox.FlowSandboxGroupContext
import net.corda.flow.pipeline.sessions.protocol.FlowProtocolStore
import net.corda.ledger.common.testkit.publicKeyExample
import net.corda.ledger.utxo.test.UtxoLedgerTest
import net.corda.ledger.utxo.testkit.UtxoCommandExample
import net.corda.ledger.utxo.testkit.UtxoStateClassExample
import net.corda.ledger.utxo.testkit.getExampleStateAndRefImpl
import net.corda.ledger.utxo.testkit.getUtxoStateExample
import net.corda.ledger.utxo.testkit.notaryX500Name
import net.corda.ledger.utxo.testkit.utxoTimeWindowExample
import net.corda.sandbox.SandboxGroup
import net.corda.sandboxgroupcontext.VirtualNodeContext
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.SecureHash
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
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.security.PublicKey
import kotlin.test.assertEquals
import kotlin.test.assertIs

class UtxoLedgerServiceImplTest: UtxoLedgerTest() {

    @Test
    fun `createTransactionBuilder should return a Transaction Builder`() {
        val transactionBuilder = utxoLedgerService.createTransactionBuilder()
        assertIs<UtxoTransactionBuilder>(transactionBuilder)
    }

    @Test
    fun `UtxoLedgerServiceImpl createTransactionBuilder() can build a SignedTransaction`() {
        val transactionBuilder = utxoLedgerService.createTransactionBuilder()

        val inputStateAndRef = getExampleStateAndRefImpl(1)
        val inputStateRef = inputStateAndRef.ref
        val referenceStateAndRef = getExampleStateAndRefImpl(2)
        val referenceStateRef = referenceStateAndRef.ref

        whenever(mockUtxoLedgerStateQueryService.resolveStateRefs(any()))
            .thenReturn(listOf(inputStateAndRef, referenceStateAndRef))

        val command = UtxoCommandExample()

        val signedTransaction = transactionBuilder
            .setNotary(notaryX500Name)
            .setTimeWindowBetween(utxoTimeWindowExample.from, utxoTimeWindowExample.until)
            .addOutputState(getUtxoStateExample())
            .addInputState(inputStateRef)
            .addReferenceState(referenceStateRef)
            .addSignatories(listOf(publicKeyExample))
            .addCommand(command)
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
            whenever(this.name).thenReturn(notaryX500Name)
        }

        whenever(mockNotaryLookup.notaryServices).thenReturn(listOf(notaryService))

        assertThatThrownBy {
            utxoLedgerService.getPluggableNotaryClientFlow(MemberX500Name.parse("O=ExampleNotaryService2, L=London, C=GB"))
        }
            .isInstanceOf(CordaRuntimeException::class.java)
            .hasMessageContaining("has not been registered on the network")
    }

    @Test
    fun `getPluggableNotaryClientFlow fails with plugin that does not inherit from base class`() {

        class MyClientFlow

        val notaryService = mock<NotaryInfo>().apply {
            whenever(this.name).thenReturn(notaryX500Name)
            whenever(this.protocol).thenReturn("my-client-flow")
            whenever(this.protocolVersions).thenReturn(listOf(1))
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
            utxoLedgerService.getPluggableNotaryClientFlow(notaryX500Name)
        }
            .isInstanceOf(CordaRuntimeException::class.java)
            .hasMessageContaining("is invalid because it does not inherit from")
    }

    @Test
    fun `getPluggableNotaryClientFlow fails when protocol name not found in protocol store`() {

        class MyClientFlow: PluggableNotaryClientFlow {
            override fun call(): List<DigitalSignatureAndMetadata> { return emptyList() }
        }

        val notaryService = mock<NotaryInfo>().apply {
            whenever(this.name).thenReturn(notaryX500Name)
            whenever(this.protocol).thenReturn("my-client-flow")
            whenever(this.protocolVersions).thenReturn(listOf(1))
        }

        val sandboxGroup = mock<SandboxGroup>().apply {
            whenever(this.loadClassFromMainBundles(any())).thenReturn(MyClientFlow::class.java)
        }

        val protocolStore = mock<FlowProtocolStore>().apply {
            whenever(this.initiatorForProtocol(eq("my-client-flow"),any()))
                .thenThrow(FlowFatalException("Flow not found"))
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
            utxoLedgerService.getPluggableNotaryClientFlow(notaryX500Name)
        }
            .isInstanceOf(CordaRuntimeException::class.java)
            .hasMessageContaining("Flow not found")
    }

    @Test
    fun `getPluggableNotaryClientFlow fails when no compatible version found`() {

        class MyClientFlow : PluggableNotaryClientFlow {
            override fun call(): List<DigitalSignatureAndMetadata> { return emptyList() }
        }

        val notaryService = mock<NotaryInfo>().apply {
            whenever(this.name).thenReturn(notaryX500Name)
            whenever(this.protocol).thenReturn("my-client-flow")
            whenever(this.protocolVersions).thenReturn(listOf(2))
        }

        val sandboxGroup = mock<SandboxGroup>().apply {
            whenever(this.loadClassFromMainBundles(any())).thenReturn(MyClientFlow::class.java)
        }

        val protocolStore = mock<FlowProtocolStore>().apply {
            whenever(this.initiatorForProtocol(eq("my-client-flow"), eq(listOf(1))))
                .thenReturn(MyClientFlow::class.java.name)
            whenever(this.initiatorForProtocol(eq("my-client-flow"), eq(listOf(2))))
                .thenThrow(FlowFatalException("Protocol version not found"))
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
            utxoLedgerService.getPluggableNotaryClientFlow(notaryX500Name)
        }
            .isInstanceOf(CordaRuntimeException::class.java)
            .hasMessageContaining("Protocol version not found")
    }

    @Test
    fun `getPluggableNotaryClientFlow succeeds with valid notary service and plugin`() {

        class MyClientFlow : PluggableNotaryClientFlow {
            override fun call(): List<DigitalSignatureAndMetadata> { return emptyList() }
        }

        val notaryService = mock<NotaryInfo>().apply {
            whenever(this.name).thenReturn(notaryX500Name)
            whenever(this.protocol).thenReturn("my-client-flow")
            whenever(this.protocolVersions).thenReturn(listOf(1))
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
            utxoLedgerService.getPluggableNotaryClientFlow(notaryX500Name)
        }

        assertEquals(MyClientFlow::class.java.name, result.flowClass.name)
    }
}
