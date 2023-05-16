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
                "Interop-Gold-Silver-Bronze-Group", listOf(
                    NetworkFacadeData(
                        "Gold", "Gold Trading", listOf(
                            "org.corda.interop/platform/hello-interop/v1.0", "org.corda.interop/platform/tokens/v1.0"
                        )
                    ), NetworkFacadeData(
                        "Silver", "Silver Trading", listOf(
                            "org.corda.interop/platform/hello-interop/v1.0"
                        )
                    ), NetworkFacadeData(
                        "Bronze", "Bronze Trading", listOf(
                            "org.corda.interop/platform/tokens/v1.0"
                        )
                    )
                )
            ), GroupData(
                "abc0aae-be7c-44c2-aa4f-4d0d7145cabc", "Interop-UKBank-EUBank-Group", listOf(
                    NetworkFacadeData(
                        "UKBank", "UK Bank Payment", listOf(
                            "org.corda.interop/platform/hello-interop/v1.0"
                        )
                    ), NetworkFacadeData(
                        "EUBank", "EU Bank Payment", listOf(
                            "org.corda.interop/platform/tokens/v1.0"
                        )
                    )
                )
            )
        )
        return HoldingIdAliasGroupInfoImpl("Bob", aliceGroups).groups
    }
}

data class GroupData(
    val groupId: String,
    val groupName: String,
    val networks: List<NetworkFacadeData>
)

data class NetworkFacadeData(
    val network: String,
    val cpiName: String,
    val facadeIds: List<String>
)

data class HoldingIdAliasGroupInfoImpl(
    private val member: String,
    val groupData: List<GroupData>
) : HoldingIdAliasGroupInfo {
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
        return networks.map { DummyAliasMemberInfo(member, it.network, groupName, it.cpiName, it.facadeIds) }.toMutableList()
    }
}

data class DummyAliasMemberInfo(
    private val member: String,
    private val network: String,
    private val groupName: String,
    private val cpiName: String,
    private val facadeIds: List<String>
) : AliasMemberInfo {
    override fun getX500Name(): String {
        return "O=$member Alias, L=London, C=GB"
    }

    override fun getCpiName(): String {
        return cpiName
    }

    override fun getIdentifier(): String {
        return "$x500Name@$cpiName";
    }

    override fun getFacadeIds(): List<String> {
        return facadeIds
    }
}
