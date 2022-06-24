package net.corda.membership.lib.grouppolicy

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.membership.lib.exceptions.BadGroupPolicyException
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.Root.GROUP_ID
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.Root.MGM_DEFAULT_GROUP_ID
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import java.util.*

interface GroupPolicyParser {

    companion object {
        /**
         * Gets the group ID for the given JSON string representation of the group policy file.
         * If the group policy belongs to an MGM, a new ID is created.
         *
         * Note: this should only be use for VNode creation as it generates a new group ID if one does not already exist.
         *  i.e. if the group policy is for an MGM.
         *
         * @throws CordaRuntimeException if there is a failure parsing the GroupPolicy JSON.
         *
         * @return the groupId to use for the given GroupPolicy file.
         */
        fun getOrCreateGroupId(groupPolicyJson: String): String {
            try {
                val groupId = ObjectMapper().readTree(groupPolicyJson).get(GROUP_ID)?.asText()
                    ?: throw CordaRuntimeException("Failed to parse group policy file. " +
                            "Could not find `$GROUP_ID` in the JSON")
                return when (groupId) {
                    MGM_DEFAULT_GROUP_ID -> UUID.randomUUID().toString()
                    else -> groupId
                }
            } catch (e: JsonParseException) {
                throw CordaRuntimeException("Failed to parse group policy file", e)
            }
        }
    }
    /**
     * Parses a GroupPolicy from [String] to [GroupPolicy].
     *
     * @param holdingIdentity The holding identity which owns this group policy file. This is mostly important for when
     *  parsing on behalf of an MGM.
     * @param groupPolicy Group policy file as a Json String
     *
     * @throws [BadGroupPolicyException] if the input string is null, blank, or cannot be parsed.
     */
    fun parse(
        holdingIdentity: HoldingIdentity,
        groupPolicy: String?
    ): GroupPolicy

    /**
     * Constructs MGM [MemberInfo] from details specified in [GroupPolicy].
     *
     * @param holdingIdentity The holding identity which owns this group policy file. This is mostly important for when
     *  parsing on behalf of an MGM.
     * @param groupPolicy Group policy file as a Json String
     *
     * @throws [BadGroupPolicyException] if the input string is null, blank, or cannot be parsed.
     */
    fun getMgmInfo(
        holdingIdentity: HoldingIdentity,
        groupPolicy: String
    ): MemberInfo?
}
