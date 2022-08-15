package net.corda.membership.lib.grouppolicy

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.membership.lib.exceptions.BadGroupPolicyException
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyKeys.Root.GROUP_ID
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.LayeredPropertyMap
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity

interface GroupPolicyParser {

    companion object {
        /**
         * Gets the group ID for the given JSON string representation of the group policy file.
         *
         * @throws CordaRuntimeException if there is a failure parsing the GroupPolicy JSON.
         *
         * @return the groupId to use for the given GroupPolicy file.
         */
        fun groupIdFromJson(groupPolicyJson: String): String {
            try {
                return ObjectMapper().readTree(groupPolicyJson).get(GROUP_ID)?.asText()
                    ?: throw CordaRuntimeException("Failed to parse group policy file. " +
                            "Could not find `$GROUP_ID` in the JSON")
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
     * @throws [BadGroupPolicyException] if the input string is null, blank, cannot be parsed, or if persisted
     * properties cannot be retrieved.
     */
    fun parse(
        holdingIdentity: HoldingIdentity,
        groupPolicy: String?,
        groupPolicyPropertiesQuery: () -> LayeredPropertyMap?
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
