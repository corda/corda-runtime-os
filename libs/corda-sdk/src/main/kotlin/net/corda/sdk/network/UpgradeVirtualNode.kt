package net.corda.sdk.network

import net.corda.crypto.core.ShortHash
import net.corda.membership.lib.MemberInfoExtension.Companion.PARTY_NAME
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_CPI_NAME
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_CPI_VERSION
import net.corda.membership.lib.MemberInfoExtension.Companion.GROUP_ID
import net.corda.membership.lib.MemberInfoExtension.Companion.IS_MGM
import net.corda.membership.lib.MemberInfoExtension.Companion.NOTARY_SERVICE_NAME
import net.corda.restclient.CordaRestClient
import net.corda.sdk.network.config.NetworkConfig
import net.corda.sdk.network.config.VNode
import net.corda.sdk.packaging.CpiAttributes
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.HoldingIdentity

class UpgradeVirtualNode(val restClient: CordaRestClient) {

    private class MemberContext(private val memberContextMap: Map<String, String>) {
        val partyName get() = MemberX500Name.parse(
            memberContextMap[PARTY_NAME] ?: error("Member context does not contain party name.")
        )
        val groupId get() = memberContextMap[GROUP_ID] ?: error("Member context does not contain group ID.")
        val cpiName get() = memberContextMap[MEMBER_CPI_NAME] ?: error("Member context does not contain CPI name.")
        val cpiVersion get() = memberContextMap[MEMBER_CPI_VERSION] ?: error("Member context does not contain CPI version.")
        val holdingId get() = HoldingIdentity(partyName, groupId).shortHash
    }

    private val virtualNode = VirtualNode(restClient)
    private val memberLookup = MemberLookup(restClient)

    private fun MemberLookup.lookupPartyMembers(holdingIdentityShortHash: ShortHash): List<MemberContext> {
        return lookupMember(holdingIdentityShortHash, status = emptyList()).members
            // Filter out MGM nodes and notaries
            .filterNot { it.mgmContext[IS_MGM] == "true" || it.memberContext[NOTARY_SERVICE_NAME] != null }
            .map { MemberContext(it.memberContext) }
    }

    private val NetworkConfig.memberNodes: List<VNode>
        get() = vNodes.filter { it.serviceX500Name == null && !it.mgmNode.toBoolean() }

    private val VNode.memberX500Name get() = MemberX500Name.parse(x500Name!!)
    private val NetworkConfig.memberX500Names: List<MemberX500Name>
        get() = memberNodes.map { it.memberX500Name }

    private fun NetworkConfig.validateAndGetMgmNode(): VNode {
        return this.getMgmNode() ?: error("Network configuration file does not contain MGM node.")
    }

    private fun getMgmHoldingId(mgmNode: VNode): ShortHash {
        val mgmCpiName = mgmNode.cpi
            ?: error("MGM node in the network configuration file does not have a CPI name defined.")
        val mgmX500Name = mgmNode.x500Name
            ?: error("MGM node in the network configuration file does not have an X.500 name defined.")

        val existingVNodes = virtualNode.getAllVirtualNodes().virtualNodes
        val shortHashString = existingVNodes.firstOrNull {
            it.holdingIdentity.x500Name == mgmX500Name && it.cpiIdentifier.cpiName == mgmCpiName
        }?.holdingIdentity?.shortHash ?: error(
            "MGM virtual node with X.500 name '$mgmX500Name' and CPI name '$mgmCpiName' not found among existing virtual nodes."
        )

        return ShortHash.of(shortHashString)
    }

    private fun validateNetworkConfig(networkConfig: NetworkConfig, upgradeCpiAttributes: CpiAttributes) {
        require(networkConfig.memberNodes.isNotEmpty()) { "Network configuration file does not contain any members to upgrade." }
        require(networkConfig.memberNodes.all { it.x500Name != null && it.cpi != null }) {
            "Network configuration file contains members without X.500 name or CPI name defined."
        }
        require(networkConfig.memberNodes.all { it.cpi!! == upgradeCpiAttributes.cpiName }) {
            "All target members must have the same CPI name as the one being upgraded (${upgradeCpiAttributes.cpiName})."
        }
    }

    fun getUpgradeHoldingIds(networkConfig: NetworkConfig, upgradeCpiAttributes: CpiAttributes): List<ShortHash> {
        validateNetworkConfig(networkConfig, upgradeCpiAttributes)

        val mgmHoldingId = getMgmHoldingId(networkConfig.validateAndGetMgmNode())
        val existingMembers = memberLookup.lookupPartyMembers(mgmHoldingId)

        validateAllConfigMembersExist(networkConfig, existingMembers, upgradeCpiAttributes.cpiName)

        val targetExistingMembers = existingMembers.filter {
            it.cpiName == upgradeCpiAttributes.cpiName && it.partyName in networkConfig.memberX500Names
        }
        validateMembersCpiVersion(targetExistingMembers, upgradeCpiAttributes)

        return inferAndCheckTargetHoldingIds(targetExistingMembers)
    }

    private fun inferAndCheckTargetHoldingIds(targetExistingMembers: List<MemberContext>): List<ShortHash> {
        val allVirtualNodes = virtualNode.getAllVirtualNodes().virtualNodes
        val existingVNodesHoldingIds = allVirtualNodes.map { ShortHash.of(it.holdingIdentity.shortHash) }

        val notFoundHoldingIds = targetExistingMembers.filterNot {
            it.holdingId in existingVNodesHoldingIds
        }

        // Double-checking, this should not be the case after the prior validations
        require(notFoundHoldingIds.isEmpty()) {
            "The following inferred holding identity short hashes are not present among existing virtual nodes:\n" +
             notFoundHoldingIds.joinToString("\n") {
                 """Name: "${it.partyName}", short hash: ${it.holdingId}"""
             }
        }

        return targetExistingMembers.map { it.holdingId }
    }

    private fun validateAllConfigMembersExist(networkConfig: NetworkConfig, existingMemberContexts: List<MemberContext>, upgradeCpiName: String) {
        val missingConfigMembers = networkConfig.memberNodes.filter { memberNode ->
            existingMemberContexts.none { it.partyName == memberNode.memberX500Name && it.cpiName == upgradeCpiName }
        }

        require(missingConfigMembers.isEmpty()) {
            val missingMembersString = missingConfigMembers.joinToString("\n") {
                """Name: "${it.x500Name}", CPI: ${it.cpi}"""
            }
            "The following members from the network configuration file are not present in the network:\n$missingMembersString"
        }
    }

    private fun validateMembersCpiVersion(memberContexts: List<MemberContext>, upgradeCpiAttributes: CpiAttributes) {
        val sameCpiVersionMembers = memberContexts.filter {
            it.cpiVersion == upgradeCpiAttributes.cpiVersion
        }
        require(sameCpiVersionMembers.isEmpty()) {
            val invalidMembersString = sameCpiVersionMembers.joinToString("\n") {
                """Name: "${it.partyName}", CPI name: ${it.cpiName}, CPI version: ${it.cpiVersion}"""
            }
            "The following members have the same CPI version as the one being upgraded:\n$invalidMembersString"
        }
    }
}


