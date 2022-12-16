package net.corda.flow.pipeline.sessions.impl

import net.corda.flow.pipeline.exceptions.FlowFatalException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock

class FlowProtocolStoreImplTest {

    companion object {
        private const val INITIATING_FLOW_A = "initiator-a"
        private const val INITIATING_FLOW_B = "initiator-b"
        private const val INITIATING_FLOW_C = "initiator-c"

        private const val RESPONDER_FLOW_A = "responder-a"
        private const val RESPONDER_FLOW_B = "responder-b"
        private const val RESPONDER_FLOW_C = "responder-c"

        private const val PROTOCOL_1 = "protocol-1"
        private const val PROTOCOL_2 = "protocol-2"
    }

    @Test
    fun `retrieves the correct list of protocol and versions for initiating flows`() {
        val initiatorsToProtocols = mapOf(
            INITIATING_FLOW_A to makeProtocols(PROTOCOL_1, listOf(1, 2, 3)),
            INITIATING_FLOW_B to makeProtocols(PROTOCOL_1, listOf(4)),
            INITIATING_FLOW_C to makeProtocols(PROTOCOL_2, listOf(1, 2))
        )
        val protocolStore = FlowProtocolStoreImpl(initiatorsToProtocols, mapOf(), mapOf())
        assertEquals(PROTOCOL_1, protocolStore.protocolsForInitiator(INITIATING_FLOW_A, mock()).first)
        assertEquals(listOf(1, 2, 3), protocolStore.protocolsForInitiator(INITIATING_FLOW_A, mock()).second)
        assertEquals(PROTOCOL_1, protocolStore.protocolsForInitiator(INITIATING_FLOW_B, mock()).first)
        assertEquals(listOf(4), protocolStore.protocolsForInitiator(INITIATING_FLOW_B, mock()).second)
        assertEquals(PROTOCOL_2, protocolStore.protocolsForInitiator(INITIATING_FLOW_C, mock()).first)
        assertEquals(listOf(1, 2), protocolStore.protocolsForInitiator(INITIATING_FLOW_C, mock()).second)
    }

    @Test
    fun `retrieves correct initiating flow for given supported protocols`() {
        val protocolsToResponders = mapOf(
            FlowProtocol(PROTOCOL_1, 1) to RESPONDER_FLOW_A,
            FlowProtocol(PROTOCOL_1, 2) to RESPONDER_FLOW_A,
            FlowProtocol(PROTOCOL_1, 3) to RESPONDER_FLOW_B,
            FlowProtocol(PROTOCOL_2, 1) to RESPONDER_FLOW_C
        )
        val protocolStore = FlowProtocolStoreImpl(mapOf(), mapOf(), protocolsToResponders)
        assertEquals(RESPONDER_FLOW_A, protocolStore.responderForProtocol(PROTOCOL_1, listOf(1), mock()))
        assertEquals(RESPONDER_FLOW_A, protocolStore.responderForProtocol(PROTOCOL_1, listOf(1, 2), mock()))
        assertEquals(RESPONDER_FLOW_B, protocolStore.responderForProtocol(PROTOCOL_1, listOf(1, 2, 3), mock()))
        assertEquals(RESPONDER_FLOW_B, protocolStore.responderForProtocol(PROTOCOL_1, listOf(1, 2, 3, 4), mock()))
        assertEquals(RESPONDER_FLOW_C, protocolStore.responderForProtocol(PROTOCOL_2, listOf(1), mock()))
    }

    @Test
    fun `exception is thrown if no protocols are configured for provided initiator`() {
        val initiatorsToProtocols = mapOf(
            INITIATING_FLOW_A to makeProtocols(PROTOCOL_1, listOf(1, 2, 3))
        )
        val protocolStore = FlowProtocolStoreImpl(initiatorsToProtocols, mapOf(), mapOf())
        assertThrows<FlowFatalException> {
            protocolStore.protocolsForInitiator(INITIATING_FLOW_B, mock())
        }
    }

    @Test
    fun `exception is thrown if no initiators are configured for provided protocols`() {
        val protocolsToInitiators = mapOf(
            FlowProtocol(PROTOCOL_1, 1) to INITIATING_FLOW_A,
            FlowProtocol(PROTOCOL_1, 2) to INITIATING_FLOW_A,
            FlowProtocol(PROTOCOL_1, 3) to INITIATING_FLOW_B
        )
        val protocolStore = FlowProtocolStoreImpl(mapOf(),protocolsToInitiators, mapOf())
        assertThrows<FlowFatalException> {
            protocolStore.initiatorForProtocol(PROTOCOL_2, listOf(1, 2, 3))
        }
        assertThrows<FlowFatalException> {
            protocolStore.initiatorForProtocol(PROTOCOL_1, listOf(4))
        }
    }

    @Test
    fun `exception is thrown if no responders are configured for provided protocols`() {
        val protocolsToResponders = mapOf(
            FlowProtocol(PROTOCOL_1, 1) to RESPONDER_FLOW_A,
            FlowProtocol(PROTOCOL_1, 2) to RESPONDER_FLOW_A,
            FlowProtocol(PROTOCOL_1, 3) to RESPONDER_FLOW_B
        )
        val protocolStore = FlowProtocolStoreImpl(mapOf(), mapOf(), protocolsToResponders)
        assertThrows<FlowFatalException> {
            protocolStore.responderForProtocol(PROTOCOL_2, listOf(1, 2, 3), mock())
        }
        assertThrows<FlowFatalException> {
            protocolStore.responderForProtocol(PROTOCOL_1, listOf(4), mock())
        }
    }

    @Test
    fun `exception is thrown if multiple protocol names are configured for an initiator`() {
        val initiatorsToProtocols = mapOf(
            INITIATING_FLOW_A to makeProtocols(PROTOCOL_1, listOf(1, 2, 3)) + makeProtocols(PROTOCOL_2, listOf(1))
        )
        val protocolStore = FlowProtocolStoreImpl(initiatorsToProtocols, mapOf(), mapOf())
        assertThrows<FlowFatalException> {
            protocolStore.protocolsForInitiator(INITIATING_FLOW_A, mock())
        }
    }

    private fun makeProtocols(name: String, versions: List<Int>) : List<FlowProtocol> {
        return versions.map { FlowProtocol(name, it) }
    }
}
