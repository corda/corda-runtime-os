package net.corda.flow.manager.impl.handlers

import net.corda.flow.manager.FlowSandboxContextTypes
import net.corda.flow.manager.FlowSandboxService
import net.corda.sandboxgroupcontext.getObjectByKey
import net.corda.v5.application.services.serialization.SerializationService
import net.corda.virtualnode.HoldingIdentity

fun FlowSandboxService.getSerializationService(holdingIdentity: HoldingIdentity): SerializationService {
    return get(holdingIdentity).getObjectByKey(FlowSandboxContextTypes.AMQP_P2P_SERIALIZATION_SERVICE)
        ?: throw FlowProcessingException("P2P serialization service not found within the sandbox for identity: $holdingIdentity")
}