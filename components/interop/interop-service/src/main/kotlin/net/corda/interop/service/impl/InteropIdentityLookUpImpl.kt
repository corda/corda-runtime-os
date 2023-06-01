package net.corda.interop.service.impl

import net.corda.interop.helper.HardcodedAliasIdentityHelper
import net.corda.sandbox.type.UsedByFlow
import net.corda.v5.application.interop.InteropIdentityLookUp
import net.corda.v5.application.interop.facade.FacadeId
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.interop.InterOpIdentityInfo
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE

@Component(service = [InteropIdentityLookUp::class, UsedByFlow::class], scope = PROTOTYPE)
class InteropIdentityLookUpImpl @Activate constructor() :
    InteropIdentityLookUp, UsedByFlow, SingletonSerializeAsToken {

    @Suspendable
    override fun lookup(applicationName: String?): InterOpIdentityInfo? {
        return getAliasMemberData().firstOrNull(){
            it.applicationName() == applicationName
        }
    }
    @Suspendable
    override fun lookup(facadeId: FacadeId?): List<InterOpIdentityInfo> {
        return getAliasMemberData().filter { it.facadeIds.contains(facadeId.toString()) }
    }
    @Suspendable
    private fun getAliasMemberData(): List<InterOpIdentityInfo> {
        return HardcodedAliasIdentityHelper.getAliasIdentityData()
    }
}
