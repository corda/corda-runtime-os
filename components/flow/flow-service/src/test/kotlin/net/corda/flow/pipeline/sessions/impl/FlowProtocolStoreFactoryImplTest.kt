package net.corda.flow.pipeline.sessions.impl

import net.corda.flow.pipeline.exceptions.FlowFatalException
import net.corda.libs.packaging.core.CordappManifest
import net.corda.libs.packaging.core.CpkMetadata
import net.corda.sandbox.SandboxGroup
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.Flow
import net.corda.v5.application.flows.InitiatedBy
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.flows.RestRequestBody
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.application.messaging.FlowSession
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.osgi.framework.Bundle

class FlowProtocolStoreFactoryImplTest {

    companion object {
        private const val INITIATING_FLOW = "initiating-flow"
        private const val INITIATED_FLOW = "initiated-flow"
        private const val RPC_FLOW = "rpc-flow"
        private const val BAD_RESPONDER = "bad-responder"
        private const val INVALID_RESPONDER = "invalid-responder"
        private const val PROTOCOL = "protocol"
    }

    @Test
    fun `created protocol store has correct behaviour when retrieving initiating and responder protocols`() {
        val sandboxGroup = makeMockSandboxGroup(
            listOf(
                CpkFlowClassNameLists(listOf(INITIATING_FLOW), listOf(), listOf()),
                CpkFlowClassNameLists(listOf(RPC_FLOW, INITIATED_FLOW), listOf(RPC_FLOW), listOf(INITIATED_FLOW))
            )
        )
        val protocolStore = FlowProtocolStoreFactoryImpl().create(sandboxGroup)
        assertEquals(Pair(PROTOCOL, listOf(1)), protocolStore.protocolsForInitiator(INITIATING_FLOW, mock()))
        assertEquals(INITIATED_FLOW, protocolStore.responderForProtocol(PROTOCOL, listOf(1), mock()))
        assertEquals(Pair(PROTOCOL, listOf(1)), protocolStore.protocolsForInitiator(RPC_FLOW, mock()))
    }

    @Test
    fun `adding two responders with the same protocol results in an error`() {
        val sandboxGroup = makeMockSandboxGroup(
            listOf(
                CpkFlowClassNameLists(listOf(INITIATING_FLOW, BAD_RESPONDER), listOf(), listOf(BAD_RESPONDER)),
                CpkFlowClassNameLists(listOf(RPC_FLOW, INITIATED_FLOW), listOf(RPC_FLOW), listOf(INITIATED_FLOW))
            )
        )
        val thrownException = assertThrows<FlowFatalException> {
            FlowProtocolStoreFactoryImpl().create(sandboxGroup)
        }
        assertEquals(
            "Cannot declare multiple responders for the same protocol in the same CPI",
            thrownException.message
        )
    }

    @Test
    fun `error is returned when flow annotated with @InitiatedBy does not implement ResponderFlow`() {
        val sandboxGroup = makeMockSandboxGroup(
            listOf(
                CpkFlowClassNameLists(listOf(INITIATING_FLOW, INVALID_RESPONDER), listOf(), listOf(INVALID_RESPONDER)),
                CpkFlowClassNameLists(listOf(RPC_FLOW, INITIATED_FLOW), listOf(RPC_FLOW), listOf(INITIATED_FLOW))
            )
        )
        val thrownException = assertThrows<FlowFatalException> {
            FlowProtocolStoreFactoryImpl().create(sandboxGroup)
        }
        assertEquals(
            "Flow ${InvalidResponderFlow::class.java.canonicalName} must implement ${ResponderFlow::class.java.simpleName}",
            thrownException.message
        )
    }

    private fun makeMockCPKMetadata(flows: CpkFlowClassNameLists): CpkMetadata {
        val cpkMetadata = mock<CpkMetadata>()
        val manifest = mock<CordappManifest>()
        whenever(manifest.flows).thenReturn(flows.flows.toSet())
        whenever(manifest.clientStartableFlows).thenReturn(flows.rpcFlows.toSet())
        whenever(manifest.initiatedFlows).thenReturn(flows.initiatedFlows.toSet())
        whenever(cpkMetadata.cordappManifest).thenReturn(manifest)
        return cpkMetadata
    }

    private fun makeMockSandboxGroup(flows: List<CpkFlowClassNameLists>): SandboxGroup {
        val cpks = flows.map { makeMockCPKMetadata(it) }
        val metadata = cpks.map { Pair(mock<Bundle>(), it) }.toMap()
        val sandboxGroup = mock<SandboxGroup>()
        whenever(sandboxGroup.metadata).thenReturn(metadata)
        whenever(
            sandboxGroup.loadClassFromMainBundles(
                INITIATING_FLOW,
                Flow::class.java
            )
        ).thenReturn(MyInitiatingFlow::class.java)
        whenever(sandboxGroup.loadClassFromMainBundles(INITIATED_FLOW, Flow::class.java)).thenReturn(
            MyResponderFlow::class.java
        )
        whenever(sandboxGroup.loadClassFromMainBundles(RPC_FLOW, Flow::class.java)).thenReturn(
            MyRPCFlow::class.java
        )
        whenever(sandboxGroup.loadClassFromMainBundles(BAD_RESPONDER, Flow::class.java)).thenReturn(
            BadResponderFlow::class.java
        )
        whenever(sandboxGroup.loadClassFromMainBundles(INVALID_RESPONDER, Flow::class.java)).thenReturn(
            InvalidResponderFlow::class.java
        )

        return sandboxGroup
    }

    @InitiatingFlow(protocol = PROTOCOL)
    private class MyInitiatingFlow : SubFlow<Unit> {
        override fun call() {
        }
    }

    @InitiatedBy(protocol = PROTOCOL)
    private class MyResponderFlow : ResponderFlow {
        override fun call(session: FlowSession) {
        }
    }

    @InitiatingFlow(protocol = PROTOCOL)
    private class MyRPCFlow : ClientStartableFlow {
        override fun call(requestBody: RestRequestBody): String {
            return ""
        }
    }

    @InitiatedBy(protocol = PROTOCOL)
    private class BadResponderFlow : ResponderFlow {
        override fun call(session: FlowSession) {
        }
    }

    @InitiatedBy(protocol = PROTOCOL)
    private class InvalidResponderFlow : Flow

    private data class CpkFlowClassNameLists(
        val flows: List<String>,
        val rpcFlows: List<String>,
        val initiatedFlows: List<String>
    )
}