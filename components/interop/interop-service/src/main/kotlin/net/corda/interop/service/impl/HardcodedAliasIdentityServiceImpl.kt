package net.corda.interop.service.impl

import net.corda.interop.service.AliasIdentityDataService
import net.corda.v5.interop.AliasMemberInfo
import net.corda.v5.interop.HoldingIdAliasGroupInfo
import net.corda.v5.interop.InteropGroupInfo
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import java.util.UUID

@Component(service = [AliasIdentityDataService::class])
class HardcodedAliasIdentityDataServiceImpl @Activate constructor() : AliasIdentityDataService {
    override fun getAliasIdentityData(): MutableList<InteropGroupInfo> {
        val aliceGroups = listOf(
            GroupData(
                "3dfc0aae-be7c-44c2-aa4f-4d0d7145cf08", //This is hardcoded groupId for existing texts.
                "Interop-Gold-Silver-Bronze-Group",
                listOf(
                    NetworkFacadeData(
                        "Gold",
                        listOf(
                            "org.corda.interop/platform/hello-interop/v1.0",
                            "org.corda.interop/platform/tokens/v1.0"
                        )
                    ),
                    NetworkFacadeData(
                        "Silver",
                        listOf(
                            "org.corda.interop/platform/tokens/v1.0"
                        )
                    ),
                    NetworkFacadeData(
                        "Bronze",
                        listOf(
                            "org.corda.interop/platform/tokens/v1.0"
                        )
                    )
                )
            ),
            GroupData(
                "abc0aae-be7c-44c2-aa4f-4d0d7145cabc",
                "Interop-UKBank-EUBank-Group",
                listOf(
                    NetworkFacadeData(
                        "UKBank",
                        listOf(
                            "org.corda.interop/platform/hello-interop/v1.0"
                        )
                    ),
                    NetworkFacadeData(
                        "EUBank",
                        listOf(
                            "org.corda.interop/platform/tokens/v1.0"
                        )
                    )
                )
            )
        )
        return HoldingIdAliasGroupInfo("Alice", aliceGroups).groups
    }
}

data class GroupData(val groupId: String, val groupName: String, val networks: List<NetworkFacadeData>)
data class NetworkFacadeData(val network: String, val facadeIds: List<String>)
data class HoldingIdAliasGroupInfo(private val member: String, val groupData: List<GroupData>) :
    HoldingIdAliasGroupInfo {
    override fun getShortHash(): String {
        return "ANYSHORTHASH"
    }

    override fun getGroups(): MutableList<InteropGroupInfo> {
        return groupData.map { DummyGroup(member, it.groupId, it.groupName, it.networks) }.toMutableList()
    }

}

data class DummyGroup(
    private val member: String,
    private val groupId: String,
    private val groupName: String,
    private val networks: List<NetworkFacadeData>
) : InteropGroupInfo {
    override fun getGroupId(): UUID {
        return UUID.fromString(groupId)
    }

    override fun getGroupName(): String {
        return groupName
    }

    override fun getMembers(): MutableList<AliasMemberInfo> {
        return networks.map { DummyAliasMemberInfo(member, it.network, groupName, it.facadeIds) }.toMutableList()
    }
}

data class DummyAliasMemberInfo(
    private val member: String,
    private val network: String,
    private val groupName: String,
    private val facadeIds: List<String>
) : AliasMemberInfo {
    override fun getX500Name(): String {
        return "CN=$member $network Alias, O=$member Corp, L=LDN, C=GB"
    }

    override fun getCpiName(): String {
        return "$network-CPI.cpi"
    }

    override fun getIdentifier(): String {
        return "$x500Name@$groupName";
    }

    override fun getFacadeIds(): List<String> {
        return facadeIds
    }
}
