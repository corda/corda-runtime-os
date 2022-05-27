package net.corda.flow.pipeline.sessions

import net.corda.flow.pipeline.FlowEventContext

/**
 * Storage for protocol mappings for initiating and responder flows.
 *
 * An instance of this interface should be attached to the flow sandbox and constructed at sandbox construction time.
 */
interface FlowProtocolStore {

    /**
     * Find the responder flow class name for the given set of received protocol versions.
     *
     * This will select the responder flow class that supports the highest version provided on input.
     *
     * @param protocolName The name of the protocol that the responder flow must implement
     * @param supportedVersions The versions of the protocol that the initiating side supports. The highest supported
     *                          version will be selected.
     * @param context The flow event context. Used in the event of a failure.
     * @return The name of the responder flow class to be invoked.
     * @throws FlowFatalException if there are no responder flows that match the provided inputs.
     */
    fun responderForProtocol(
        protocolName: String,
        supportedVersions: Collection<Int>,
        context: FlowEventContext<*>
    ): String

    /**
     * Retrieve the protocols supported by this initiating flow.
     *
     * @param initiator The class name of the initiating flow
     * @param context The flow event context. Used in the event of a failure.
     * @return The protocol name and the list of versions supported by this protocol
     * @throws FlowFatalException if there are no protocols supported for this initiating flow
     */
    fun protocolsForInitiator(initiator: String, context: FlowEventContext<*>): Pair<String, List<Int>>
}