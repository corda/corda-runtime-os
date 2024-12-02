package net.corda.flow.pipeline.sessions.impl

import net.corda.flow.pipeline.events.FlowEventContext
import net.corda.flow.pipeline.exceptions.FlowFatalException
import net.corda.flow.pipeline.sessions.protocol.FlowAndProtocolVersion
import net.corda.flow.pipeline.sessions.protocol.FlowProtocolStore
import org.slf4j.LoggerFactory

/**
 * Tracks initiator and responder flows for either side of flow protocols declared in the CPI.
 *
 * This is built along with the sandbox and cached with the sandbox context.
 */
class FlowProtocolStoreImpl(
    private val initiatorToProtocol: Map<String, List<FlowProtocol>>,
    private val protocolToInitiator: Map<FlowProtocol, String>,
    private val protocolToResponder: Map<FlowProtocol, String>
) : FlowProtocolStore {
    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun initiatorForProtocol(
        protocolName: String,
        supportedVersions: Collection<Int>
    ): String {
        val sortedProtocols = supportedVersions.sortedDescending().map { FlowProtocol(protocolName, it) }
        for (protocol in sortedProtocols) {
            val initiator = protocolToInitiator[protocol]
            if (initiator != null) {
                return initiator
            }
        }
        val protocolsStr = sortedProtocols.joinToString(", " )
        val respondersStr = protocolToInitiator.map { (p, i) -> "($p, $i)" }.joinToString(", " )
        log.info("No initiator found for protocols [$protocolsStr] among registered initiators [$respondersStr]")
        throw FlowFatalException(
            "No initiator is configured for protocol $protocolName at versions $supportedVersions"
        )
    }

    override fun responderForProtocol(
        protocolName: String,
        supportedVersions: Collection<Int>,
        context: FlowEventContext<*>
    ): FlowAndProtocolVersion {
        val sortedProtocols = supportedVersions.sortedDescending().map { FlowProtocol(protocolName, it) }
        for (protocol in sortedProtocols) {
            val responder = protocolToResponder[protocol]
            if (responder != null) {
                return FlowAndProtocolVersion(protocolName, responder, protocol.version)
            }
        }
        val protocolsStr = sortedProtocols.joinToString(", " )
        val respondersStr = protocolToResponder.map { (p, r) -> "($p, $r)" }.joinToString(", " )
        log.info("No responder found for protocols [$protocolsStr] among registered responders [$respondersStr]")
        throw FlowFatalException(
            "No responder is configured for protocol $protocolName at versions $supportedVersions"
        )
    }

    override fun protocolsForInitiator(initiator: String, context: FlowEventContext<*>): Pair<String, List<Int>> {
        val protocolList = initiatorToProtocol[initiator]
            ?: throw FlowFatalException("No protocols were found for initiating flow $initiator")
        val protocolNames = protocolList.map { it.protocol }.toSet()
        if (protocolNames.size != 1) {
            throw FlowFatalException(
                "Multiple protocol names were declared by initiating flow $initiator. Names: $protocolNames"
            )
        }
        return protocolList.run {
            Pair(protocolList.first().protocol, protocolList.map { it.version })
        }
    }
}
