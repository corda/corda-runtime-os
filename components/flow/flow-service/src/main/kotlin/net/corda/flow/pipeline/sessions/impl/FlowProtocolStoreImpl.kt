package net.corda.flow.pipeline.sessions.impl

import net.corda.flow.pipeline.FlowProcessingException
import net.corda.flow.pipeline.sessions.FlowProtocolStore
import net.corda.libs.packaging.CpiMetadata
import net.corda.sandbox.SandboxGroup
import net.corda.v5.application.flows.Flow
import net.corda.v5.application.flows.InitiatedBy
import net.corda.v5.application.flows.InitiatingFlow

/**
 * Tracks initiator and responder flows for either side of flow protocols declared in the CPI.
 *
 * This is built along with the sandbox and cached with the sandbox context.
 */
class FlowProtocolStoreImpl(
    private val initiatorToProtocol: Map<String, List<FlowProtocol>>,
    private val protocolToResponder: Map<FlowProtocol, String>
) : FlowProtocolStore {

    companion object {

        fun build(sandboxGroup: SandboxGroup, cpiMetadata: CpiMetadata) : FlowProtocolStore {
            val initiatorToProtocol = mutableMapOf<String, List<FlowProtocol>>()
            val protocolToResponder = mutableMapOf<FlowProtocol, String>()

            cpiMetadata.cpksMetadata.flatMap { it.cordappManifest.flows }.forEach { flow ->
                val flowClass = sandboxGroup.loadClassFromMainBundles(flow, Flow::class.java)
                when {
                    flowClass.isAnnotationPresent(InitiatingFlow::class.java) -> {
                        val protocol = flowClass.getAnnotation(InitiatingFlow::class.java).protocol
                        val versions = flowClass.getAnnotation(InitiatingFlow::class.java).version
                        val protocols = versions.map { FlowProtocol(protocol, it) }
                        initiatorToProtocol[flow] = protocols
                    }
                    flowClass.isAnnotationPresent(InitiatedBy::class.java) -> {
                        val protocol = flowClass.getAnnotation(InitiatedBy::class.java).protocol
                        val versions = flowClass.getAnnotation(InitiatedBy::class.java).version
                        val protocols = versions.map { FlowProtocol(protocol, it) }
                        if (protocols.any { it in protocolToResponder }) {
                            throw IllegalArgumentException("Cannot declare multiple responders for the same protocol in the same CPI")
                        }
                        protocols.forEach {
                            protocolToResponder[it] = flow
                        }
                    }
                }
            }

            return FlowProtocolStoreImpl(initiatorToProtocol, protocolToResponder)
        }
    }

    override fun responderForProtocol(protocolName: String, supportedVersions: Collection<Int>) : String {
        val sortedProtocols = supportedVersions.sortedDescending().map { FlowProtocol(protocolName, it) }
        for (protocol in sortedProtocols) {
            val responder = protocolToResponder[protocol]
            if (responder != null) {
                return responder
            }
        }
        throw FlowProcessingException("No responder is configured for protocol $protocolName at versions $supportedVersions")
    }

    override fun protocolsForInitiator(initiator: String): Pair<String, List<Int>> {
        val protocolList = initiatorToProtocol[initiator]
            ?: throw FlowProcessingException("No protocols were found for initiating flow $initiator")
        return protocolList.run {
            Pair(protocolList.first().protocol, protocolList.map { it.version })
        }
    }
}