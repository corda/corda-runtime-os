package net.corda.flow.application.services.impl

import net.corda.flow.fiber.FlowFiberService
import net.corda.interop.identity.registry.InteropIdentityRegistryView
import net.corda.sandbox.type.UsedByFlow
import net.corda.v5.application.interop.InterOpIdentityInfo
import net.corda.v5.application.interop.InteropIdentityLookup
import net.corda.v5.application.interop.facade.FacadeId
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE
import org.slf4j.LoggerFactory

@Component(service = [InteropIdentityLookup::class, UsedByFlow::class], scope = PROTOTYPE)
class InteropIdentityLookupImpl @Activate constructor(
    @Reference(service = FlowFiberService::class)
    private val flowFiberService: FlowFiberService,
) : InteropIdentityLookup, UsedByFlow, SingletonSerializeAsToken {

    private val logger = LoggerFactory.getLogger(this::class.java)

    @Suspendable
    override fun lookup(applicationName: String): InterOpIdentityInfo? {
        val identityInfo = getInteropRegistry().getIdentityWithApplicationName(applicationName) ?: return null
        if (!identityInfo.enabled) {
            return null
        }
        return InteropIdentityInfoImpl(
            identityInfo.applicationName,
            identityInfo.facadeIds,
            identityInfo.x500Name,
            identityInfo.groupId
        )
    }

    @Suspendable
    override fun lookup(facadeId: FacadeId): List<InterOpIdentityInfo> {
        val identityInfo = getInteropRegistry().getIdentitiesByFacadeId(facadeId)
        return identityInfo.map { InteropIdentityInfoImpl(it.applicationName, it.facadeIds, it.x500Name, it.groupId) }
    }

    @Suspendable
    private fun getInteropRegistry(): InteropIdentityRegistryView {
        return flowFiberService.getExecutingFiber().getExecutionContext().interopIdentityRegistryView
    }
}

data class InteropIdentityInfoImpl(
    private val applicationName: String,
    private val facadeIds: List<FacadeId>,
    private val x500Name: String,
    private val groupId: String
) : InterOpIdentityInfo {
    override fun getApplicationName(): String {
        return applicationName
    }

    override fun getFacadeIds(): List<FacadeId> {
        return facadeIds
    }

    override fun getGroupId(): String {
        return groupId
    }

    override fun getX500Name(): String {
        return x500Name
    }
}
