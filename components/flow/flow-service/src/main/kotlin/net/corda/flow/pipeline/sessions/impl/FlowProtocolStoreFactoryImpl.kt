package net.corda.flow.pipeline.sessions.impl

import net.corda.flow.pipeline.exceptions.FlowProcessingException
import net.corda.flow.pipeline.sessions.FlowProtocolStore
import net.corda.flow.pipeline.sessions.FlowProtocolStoreFactory
import net.corda.libs.packaging.core.CpiMetadata
import net.corda.sandbox.SandboxGroup
import net.corda.v5.application.flows.Flow
import net.corda.v5.application.flows.InitiatedBy
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Component

//@Suppress("Unused")
@Component(service = [FlowProtocolStoreFactory::class])
class FlowProtocolStoreFactoryImpl : FlowProtocolStoreFactory {

    companion object {
        val logger = contextLogger()
    }

    override fun create(
        sandboxGroup: SandboxGroup,
        cpiMetadata: CpiMetadata
    ): FlowProtocolStore {
        val initiatorToProtocol = mutableMapOf<String, List<FlowProtocol>>()
        val protocolToResponder = mutableMapOf<FlowProtocol, String>()

        cpiMetadata.cpksMetadata.flatMap { it.cordappManifest.flows }.forEach { flow ->
            logger.info("Reading flow $flow for protocols")
            val flowClass = sandboxGroup.loadClassFromMainBundles(flow, Flow::class.java)
            when {
                flowClass.isAnnotationPresent(InitiatingFlow::class.java) -> {
                    val protocol = flowClass.getAnnotation(InitiatingFlow::class.java).protocol
                    val versions = flowClass.getAnnotation(InitiatingFlow::class.java).version
                    val protocols = versions.map { FlowProtocol(protocol, it) }
                    initiatorToProtocol[flow] = protocols
                }
                flowClass.isAnnotationPresent(InitiatedBy::class.java) -> {
                    if (!flowClass.interfaces.contains(ResponderFlow::class.java)) {
                        throw FlowProcessingException(
                            "Found a responder flow that does not implement ResponderFlow"
                        )
                    }
                    val protocol = flowClass.getAnnotation(InitiatedBy::class.java).protocol
                    val versions = flowClass.getAnnotation(InitiatedBy::class.java).version
                    val protocols = versions.map { FlowProtocol(protocol, it) }
                    if (protocols.any { it in protocolToResponder }) {
                        throw FlowProcessingException(
                            "Cannot declare multiple responders for the same protocol in the same CPI"
                        )
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