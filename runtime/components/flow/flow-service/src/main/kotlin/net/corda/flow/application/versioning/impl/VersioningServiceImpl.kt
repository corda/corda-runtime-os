package net.corda.flow.application.versioning.impl

import net.corda.flow.application.services.VersioningService
import net.corda.flow.application.versioning.VersionedReceiveFlowFactory
import net.corda.flow.application.versioning.VersionedSendFlowFactory
import net.corda.flow.state.asFlowContext
import net.corda.sandbox.type.SandboxConstants
import net.corda.sandbox.type.UsedByFlow
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope

const val VERSIONING_PROPERTY_NAME = "corda.flow.versioning"
const val RESET_VERSIONING_MARKER = "RESET_VERSIONING_MARKER"

@Component(
    service = [VersioningService::class, UsedByFlow::class],
    property = [SandboxConstants.CORDA_SYSTEM_SERVICE],
    scope = ServiceScope.PROTOTYPE
)
class VersioningServiceImpl @Activate constructor(
    @Reference(service = FlowEngine::class)
    private val flowEngine: FlowEngine
) : VersioningService, UsedByFlow, SingletonSerializeAsToken {

    @Suspendable
    override fun <R> versionedSubFlow(versionedFlowFactory: VersionedSendFlowFactory<R>, sessions: List<FlowSession>): R {
        return flowEngine.subFlow(VersioningFlow(versionedFlowFactory, sessions))
    }

    @Suspendable
    override fun <R> versionedSubFlow(versionedFlowFactory: VersionedReceiveFlowFactory<R>, session: FlowSession): R {
        return flowEngine.subFlow(ReceiveVersioningFlow(versionedFlowFactory, session))
    }

    override fun peekCurrentVersioning(): Pair<Int, LinkedHashMap<String, Any>>? {
        return (flowEngine.flowContextProperties[VERSIONING_PROPERTY_NAME])?.let { value ->
            if (value == RESET_VERSIONING_MARKER) {
                null
            } else {
                value.toInt() to linkedMapOf()
            }
        }
    }

    override fun setCurrentVersioning(version: Int) {
        flowEngine.flowContextProperties.asFlowContext.platformProperties[VERSIONING_PROPERTY_NAME] = version.toString()
    }

    override fun resetCurrentVersioning() {
        val properties = flowEngine.flowContextProperties
        properties[VERSIONING_PROPERTY_NAME]?.let {
            properties.asFlowContext.platformProperties[VERSIONING_PROPERTY_NAME] = RESET_VERSIONING_MARKER
        }
    }
}