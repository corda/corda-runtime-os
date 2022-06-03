package net.corda.flow.pipeline.sessions.impl

import net.corda.libs.packaging.core.CordappManifest
import net.corda.libs.packaging.core.CpiMetadata
import net.corda.libs.packaging.core.CpkMetadata
import net.corda.sandbox.SandboxGroup
import net.corda.v5.application.flows.Flow
import net.corda.v5.application.flows.InitiatedBy
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.messaging.FlowSession
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class FlowProtocolStoreFactoryImplTest {

    companion object {
        private const val INITIATING_FLOW = "initiating-flow"
        private const val INITIATED_FLOW = "initiated-flow"

        private const val PROTOCOL = "protocol"
    }

    @Test
    fun `created protocol store has correct behaviour when retrieving initiating and responder protocols`() {
        val cpiMetadata = makeMockCPIMetadata(listOf(
            listOf(INITIATING_FLOW),
            listOf(INITIATED_FLOW)
        ))
        val sandboxGroup = makeMockSandboxGroup()
        val protocolStore = FlowProtocolStoreFactoryImpl().create(sandboxGroup, cpiMetadata)
        assertEquals(Pair(PROTOCOL, listOf(1)), protocolStore.protocolsForInitiator(INITIATING_FLOW, mock()))
        assertEquals(INITIATED_FLOW, protocolStore.responderForProtocol(PROTOCOL, listOf(1), mock()))
    }

    private fun makeMockCPIMetadata(flows: List<List<String>>) : CpiMetadata {
        val cpiMetadata = mock<CpiMetadata>()
        val cpks = flows.map { makeMockCPKMetadata(it) }
        whenever(cpiMetadata.cpksMetadata).thenReturn(cpks)
        return cpiMetadata
    }

    private fun makeMockCPKMetadata(flows: List<String>) : CpkMetadata {
        val cpkMetadata = mock<CpkMetadata>()
        val manifest = mock<CordappManifest>()
        whenever(manifest.flows).thenReturn(flows.toSet())
        whenever(cpkMetadata.cordappManifest).thenReturn(manifest)
        return cpkMetadata
    }

    private fun makeMockSandboxGroup() : SandboxGroup {
        val sandboxGroup = mock<SandboxGroup>()
        whenever(sandboxGroup.loadClassFromMainBundles(INITIATING_FLOW, Flow::class.java)).thenReturn(MyInitiatingFlow::class.java)
        whenever(sandboxGroup.loadClassFromMainBundles(INITIATED_FLOW, Flow::class.java)).thenReturn(MyResponderFlow::class.java)
        return sandboxGroup
    }

    @InitiatingFlow(protocol = PROTOCOL)
    private class MyInitiatingFlow : Flow<Unit> {
        override fun call() {
        }
    }

    @InitiatedBy(protocol = PROTOCOL)
    private class MyResponderFlow : ResponderFlow<Unit> {
        override fun call(session: FlowSession) {
        }
    }
}