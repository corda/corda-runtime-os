package net.corda.flow.application.services.impl

import net.corda.flow.fiber.FlowFiberService
import net.corda.interop.identity.cache.InteropIdentityRegistryView
import net.corda.sandbox.type.UsedByFlow
import net.corda.v5.application.interop.InteropIdentityLookUp
import net.corda.v5.application.interop.facade.FacadeId
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.interop.InterOpIdentityInfo
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE

@Component(service = [InteropIdentityLookUp::class, UsedByFlow::class], scope = PROTOTYPE)
class InteropIdentityLookUpImpl @Activate constructor(
    @Reference(service = FlowFiberService::class)
    private val flowFiberService: FlowFiberService,
    ) :
    InteropIdentityLookUp, UsedByFlow, SingletonSerializeAsToken {

    @Suspendable
    override fun lookup(applicationName: String): InterOpIdentityInfo? {
        val identityInfo = getInteropRegistry().getIdentitiesByApplicationName()[applicationName] ?: return null
        return InteropIdentityInfoImpl(identityInfo.applicationName,identityInfo.facadeIds,identityInfo.x500Name,identityInfo.groupId)
    }

    @Suspendable
    override fun lookup(facadeId: FacadeId): List<InterOpIdentityInfo> {
        val identityInfo = getInteropRegistry().getIdentitiesByFacadeId()[facadeId.toString()]  ?: return emptyList()
        return identityInfo.map { InteropIdentityInfoImpl(it.applicationName, it.facadeIds, it.x500Name,it.groupId) }
    }

    @Suspendable
    private fun getInteropRegistry(): InteropIdentityRegistryView {
        return flowFiberService.getExecutingFiber().getExecutionContext().interopIdentityRegistryView
    }
}

data class InteropIdentityInfoImpl(
    private val applicationName: String,
    private val facadeIds: List<String>,
    private val x500Name: String,
    private val groupId: String
) : InterOpIdentityInfo {
    override fun getApplicationName(): String {
        return applicationName
    }


    override fun getFacadeIds(): List<String> {
        return facadeIds
    }

    override fun getGroupId(): String {
        return groupId
    }

    override fun getX500Name(): String {
        return x500Name
    }
}
