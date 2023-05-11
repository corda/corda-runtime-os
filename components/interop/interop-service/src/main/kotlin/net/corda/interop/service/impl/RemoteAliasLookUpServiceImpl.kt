package net.corda.interop.service.impl

import net.corda.sandbox.type.UsedByFlow
import net.corda.v5.application.interop.RemoteAliasLookUpService
import net.corda.v5.interop.AliasMemberInfo
import net.corda.v5.interop.InteropGroupInfo
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE

@Component(service = [RemoteAliasLookUpService::class, UsedByFlow::class], scope = PROTOTYPE)
class RemoteAliasLookUpServiceImpl @Activate constructor() : RemoteAliasLookUpService, UsedByFlow,
    SingletonSerializeAsToken {

    override fun get(identifier: String): AliasMemberInfo? {
        val groupName = getGroupName(identifier)
        return getAliasMemberData().firstOrNull { info ->
            info.groupName == groupName
        }?.members?.firstOrNull { memberInfo ->
            memberInfo.identifier.equals(
                identifier
            )
        }
    }

    private fun getAliasMemberData(): List<InteropGroupInfo> {
        return HardcodedAliasIdentityDataServiceImpl().getAliasIdentityData()
    }

    private fun getGroupName(identifier: String): String {
        return identifier.split("@")[1]
    }
}
