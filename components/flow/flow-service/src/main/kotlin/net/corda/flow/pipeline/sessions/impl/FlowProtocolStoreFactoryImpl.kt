package net.corda.flow.pipeline.sessions.impl

import net.corda.flow.pipeline.exceptions.FlowFatalException
import net.corda.flow.pipeline.sessions.FlowProtocolStore
import net.corda.flow.pipeline.sessions.FlowProtocolStoreFactory
import net.corda.sandbox.SandboxGroup
import net.corda.v5.application.flows.Flow
import net.corda.v5.application.flows.InitiatedBy
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.base.util.trace
import org.osgi.service.component.annotations.Component
import org.slf4j.LoggerFactory

@Component(service = [FlowProtocolStoreFactory::class])
class FlowProtocolStoreFactoryImpl : FlowProtocolStoreFactory {

    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private fun extractDataForFlow(
        flowName: String,
        flowClass: Class<*>,
        initiatorToProtocol: MutableMap<String, List<FlowProtocol>>,
        protocolToResponder: MutableMap<FlowProtocol, String>
    ) {
        when {
            flowClass.isAnnotationPresent(InitiatingFlow::class.java) -> {
                val protocol = flowClass.getAnnotation(InitiatingFlow::class.java).protocol
                val versions = flowClass.getAnnotation(InitiatingFlow::class.java).version
                val protocols = versions.map { FlowProtocol(protocol, it) }
                initiatorToProtocol[flowName] = protocols
            }

            flowClass.isAnnotationPresent(InitiatedBy::class.java) -> {
                if (!flowClass.interfaces.contains(ResponderFlow::class.java)) {
                    throw FlowFatalException(
                        "Flow ${flowClass.canonicalName} must implement ${ResponderFlow::class.simpleName}"
                    )
                }
                val protocol = flowClass.getAnnotation(InitiatedBy::class.java).protocol
                val versions = flowClass.getAnnotation(InitiatedBy::class.java).version
                val protocols = versions.map { FlowProtocol(protocol, it) }
                if (protocols.any { it in protocolToResponder }) {
                    throw FlowFatalException(
                        "Cannot declare multiple responders for the same protocol in the same CPI"
                    )
                }
                protocols.forEach {
                    protocolToResponder[it] = flowName
                }
            }
        }
    }

    override fun create(
        sandboxGroup: SandboxGroup,
    ): FlowProtocolStore {
        val initiatorToProtocol = mutableMapOf<String, List<FlowProtocol>>()
        val protocolToResponder = mutableMapOf<FlowProtocol, String>()

        sandboxGroup.metadata.flatMap { it.value.cordappManifest.flows }.forEach { flow ->
            logger.trace { "Reading flow $flow for protocols" }
            val flowClass = sandboxGroup.loadClassFromMainBundles(flow, Flow::class.java)
            extractDataForFlow(flow, flowClass, initiatorToProtocol, protocolToResponder)
        }

        return FlowProtocolStoreImpl(initiatorToProtocol, protocolToResponder)
    }
}
