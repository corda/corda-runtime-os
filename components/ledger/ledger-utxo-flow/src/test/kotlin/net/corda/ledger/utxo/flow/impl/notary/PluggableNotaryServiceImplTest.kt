package net.corda.ledger.utxo.flow.impl.notary

import net.corda.flow.pipeline.exceptions.FlowFatalException
import net.corda.flow.pipeline.sandbox.FlowSandboxGroupContext
import net.corda.flow.pipeline.sessions.protocol.FlowProtocolStore
import net.corda.ledger.notary.worker.selection.NotaryVirtualNodeSelectorService
import net.corda.ledger.utxo.test.UtxoLedgerTest
import net.corda.ledger.utxo.testkit.notaryX500Name
import net.corda.sandbox.SandboxGroup
import net.corda.sandboxgroupcontext.VirtualNodeContext
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.ledger.notary.plugin.api.PluggableNotaryClientFlow
import net.corda.v5.membership.NotaryInfo
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class PluggableNotaryServiceImplTest : UtxoLedgerTest() {

    private val notaryVirtualNodeSelectorService = mock<NotaryVirtualNodeSelectorService>()
    private val pluggableNotaryService = PluggableNotaryServiceImpl(
        mockCurrentSandboxGroupContext,
        mockNotaryLookup,
        notaryVirtualNodeSelectorService
    )

    @Test
    fun `getPluggableNotaryClientFlow fails for an unknown notary service`() {
        val notaryService = mock<NotaryInfo>().apply {
            whenever(this.name).thenReturn(notaryX500Name)
        }

        whenever(mockNotaryLookup.notaryServices).thenReturn(listOf(notaryService))

        assertThatThrownBy {
            pluggableNotaryService.get(MemberX500Name.parse("O=ExampleNotaryService2, L=London, C=GB"))
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
            whenever(this.initiatorForProtocol(any(), any())).thenReturn(MyClientFlow::class.java.name)
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

        assertThatThrownBy { pluggableNotaryService.get(notaryX500Name) }
            .isInstanceOf(CordaRuntimeException::class.java)
            .hasMessageContaining("is invalid because it does not inherit from")
    }

    @Test
    fun `getPluggableNotaryClientFlow fails when protocol name not found in protocol store`() {
        class MyClientFlow : PluggableNotaryClientFlow {
            override fun call(): List<DigitalSignatureAndMetadata> {
                return emptyList()
            }
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
            whenever(this.initiatorForProtocol(eq("my-client-flow"), any()))
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

        assertThatThrownBy { pluggableNotaryService.get(notaryX500Name) }
            .isInstanceOf(CordaRuntimeException::class.java)
            .hasMessageContaining("Flow not found")
    }

    @Test
    fun `getPluggableNotaryClientFlow fails when no compatible version found`() {
        class MyClientFlow : PluggableNotaryClientFlow {
            override fun call(): List<DigitalSignatureAndMetadata> {
                return emptyList()
            }
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

        assertThatThrownBy { pluggableNotaryService.get(notaryX500Name) }
            .isInstanceOf(CordaRuntimeException::class.java)
            .hasMessageContaining("Protocol version not found")
    }

    @Test
    fun `getPluggableNotaryClientFlow succeeds with valid notary service and plugin`() {
        class MyClientFlow : PluggableNotaryClientFlow {
            override fun call(): List<DigitalSignatureAndMetadata> {
                return emptyList()
            }
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
            whenever(this.initiatorForProtocol(any(), any())).thenReturn(MyClientFlow::class.java.name)
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

        val result = assertDoesNotThrow { pluggableNotaryService.get(notaryX500Name) }

        assertThat(result.flowClass.name).isEqualTo(MyClientFlow::class.java.name)
    }
}
