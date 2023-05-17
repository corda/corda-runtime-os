package net.corda.interop.service.impl

import net.corda.sandbox.type.UsedByFlow
import net.corda.v5.application.interop.RemoteAliasLookUp
import net.corda.v5.interop.AliasMemberInfo
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE

@Component(service = [RemoteAliasLookUp::class, UsedByFlow::class], scope = PROTOTYPE)
class RemoteAliasLookUpImpl @Activate constructor() :
    RemoteAliasLookUp, UsedByFlow, SingletonSerializeAsToken {

    override fun lookup(x500Name : String, cpiName: String): AliasMemberInfo? {
        return getAliasMemberData().firstOrNull {
            it.identifier == "$x500Name@$cpiName"
        }
    }

    override fun lookup(facadeId: String?): List<AliasMemberInfo> {
        return getAliasMemberData().filter { it.facadeIds.contains(facadeId) }
    }

    private fun getAliasMemberData(): List<AliasMemberInfo> {
        return HardcodedAliasIdentityDataServiceImpl().getAliasIdentityData().flatMap { it.members }.toList()
    }
}
