package net.corda.membership.rest.v1

import net.corda.membership.lib.MemberInfoExtension
import net.corda.membership.rest.v1.types.RestGroupParameters
import net.corda.rest.RestResource
import net.corda.rest.annotations.HttpGET
import net.corda.rest.annotations.RestPathParameter
import net.corda.rest.annotations.RestQueryParameter
import net.corda.rest.annotations.HttpRestResource
import net.corda.membership.rest.v1.types.response.RestMemberInfoList
import net.corda.rest.annotations.RestApiVersion

/**
 * The Member Lookup API consists of endpoints used to look up information related to membership groups. The API allows
 * you to retrieve a list of active and pending members in the membership group.
 */
@HttpRestResource(
    name = "Member Lookup API",
    description = "The Member Lookup API consists of endpoints used to look up information related to membership groups.",
    path = "members"
)
interface MemberLookupRestResource : RestResource {
    /**
     * The [lookup] method enables you to retrieve a list of all members in the membership group that
     * are visible to the member represented by [holdingIdentityShortHash]. The list can be optionally filtered by
     * X.500 name attributes or member statuses. This method returns an empty list if no members matching the criteria
     * are found.
     *
     * Example usage:
     * ```
     * memberLookupOps.lookup(holdingIdentityShortHash = "58B6030FABDD")
     *
     * memberLookupOps.lookup(holdingIdentityShortHash = "58B6030FABDD", commonName = "Alice", country = "GB")
     * ```
     *
     * @param holdingIdentityShortHash Holding identity ID of the requesting member, which uniquely identifies the member
     * and its group. The result only contains members that are visible to this member.
     * @param commonName Optional. Common Name (CN) attribute of the X.500 name to filter members by.
     * @param organization Optional. Organization (O) attribute of the X.500 name to filter members by.
     * @param organizationUnit Optional. Organization Unit (OU) attribute of the X.500 name to filter members by.
     * @param locality Optional. Locality (L) attribute of the X.500 name to filter members by.
     * @param state Optional. State (ST) attribute of the X.500 name to filter members by.
     * @param country Optional. Country (C) attribute of the X.500 name to filter members by.
     * @param statuses Optional. List of statuses ("ACTIVE", "SUSPENDED") to filter members by.
     * By default, only ACTIVE members are filtered. Only an MGM can view suspended members.
     * The v5_1 version of the API allows members to view themselves regardless of their status (e.g. even if they are suspended).
     *
     * @return List of active and pending members matching the criteria as [RestMemberInfoList].
     */
    @HttpGET(
        path = "{holdingIdentityShortHash}",
        description = "This method retrieves a list of all active and pending members in the membership group.",
        maxVersion = RestApiVersion.C5_0
    )
    @Suppress("LongParameterList")
    @Deprecated("Deprecated in favour of lookupV51")
    fun lookup(
        @RestPathParameter(description = "Holding identity ID of the requesting member. The result only contains" +
                " members that are visible to this member")
        holdingIdentityShortHash: String,
        @RestQueryParameter(
            name = "cn",
            description = "Common Name (CN) attribute of the X.500 name to filter members by",
            required = false
        )
        commonName: String? = null,
        @RestQueryParameter(
            name = "o",
            description = "Organization (O) attribute of the X.500 name to filter members by",
            required = false
        )
        organization: String? = null,
        @RestQueryParameter(
            name = "ou",
            description = "Organization Unit (OU) attribute of the X.500 name to filter members by",
            required = false
        )
        organizationUnit: String? = null,
        @RestQueryParameter(
            name = "l",
            description = "Locality (L) attribute of the X.500 name to filter members by",
            required = false
        )
        locality: String? = null,
        @RestQueryParameter(
            name = "st",
            description = "State (ST) attribute of the X.500 name to filter members by",
            required = false
        )
        state: String? = null,
        @RestQueryParameter(
            name = "c",
            description = "Country (C) attribute of the X.500 name to filter members by",
            required = false
        )
        country: String? = null,
        @RestQueryParameter(
            description = "List of statuses (\"ACTIVE\", \"SUSPENDED\") to filter members by. " +
                    "By default, only ACTIVE members are filtered. Only an MGM can view suspended members. " +
                    "The v5_1 version of the API allows members to view themselves regardless of their status " +
                    "(e.g. even if they are suspended).",
            required = false,
        )
        statuses: List<String> = listOf(MemberInfoExtension.MEMBER_STATUS_ACTIVE),
    ): RestMemberInfoList

    /**
     * The [lookup] method enables you to retrieve a list of all members in the membership group that
     * are visible to the member represented by [holdingIdentityShortHash]. The list can be optionally filtered by
     * X.500 name attributes or member statuses. This method returns an empty list if no members matching the criteria
     * are found.
     *
     * Example usage:
     * ```
     * memberLookupOps.lookup(holdingIdentityShortHash = "58B6030FABDD")
     *
     * memberLookupOps.lookup(holdingIdentityShortHash = "58B6030FABDD", commonName = "Alice", country = "GB")
     * ```
     *
     * @param holdingIdentityShortHash Holding identity ID of the requesting member, which uniquely identifies the member
     * and its group. The result only contains members that are visible to this member.
     * @param commonName Optional. Common Name (CN) attribute of the X.500 name to filter members by.
     * @param organization Optional. Organization (O) attribute of the X.500 name to filter members by.
     * @param organizationUnit Optional. Organization Unit (OU) attribute of the X.500 name to filter members by.
     * @param locality Optional. Locality (L) attribute of the X.500 name to filter members by.
     * @param state Optional. State (ST) attribute of the X.500 name to filter members by.
     * @param country Optional. Country (C) attribute of the X.500 name to filter members by.
     * @param statuses Optional. List of statuses ("ACTIVE", "SUSPENDED") to filter members by.
     * By default, only ACTIVE members are filtered. An MGM can view all suspended members.
     * A regular member cannot view other suspended members, but can view itself in any status (e.g. even if it's suspended).
     *
     * @return List of active and pending members matching the criteria as [RestMemberInfoList].
     */
    @HttpGET(
        path = "{holdingIdentityShortHash}",
        description = "This method retrieves a list of all active and pending members in the membership group.",
        minVersion = RestApiVersion.C5_1
    )
    @Suppress("LongParameterList")
    fun lookupV51(
        @RestPathParameter(description = "Holding identity ID of the requesting member. The result only contains" +
                " members that are visible to this member")
        holdingIdentityShortHash: String,
        @RestQueryParameter(
            name = "cn",
            description = "Common Name (CN) attribute of the X.500 name to filter members by",
            required = false
        )
        commonName: String? = null,
        @RestQueryParameter(
            name = "o",
            description = "Organization (O) attribute of the X.500 name to filter members by",
            required = false
        )
        organization: String? = null,
        @RestQueryParameter(
            name = "ou",
            description = "Organization Unit (OU) attribute of the X.500 name to filter members by",
            required = false
        )
        organizationUnit: String? = null,
        @RestQueryParameter(
            name = "l",
            description = "Locality (L) attribute of the X.500 name to filter members by",
            required = false
        )
        locality: String? = null,
        @RestQueryParameter(
            name = "st",
            description = "State (ST) attribute of the X.500 name to filter members by",
            required = false
        )
        state: String? = null,
        @RestQueryParameter(
            name = "c",
            description = "Country (C) attribute of the X.500 name to filter members by",
            required = false
        )
        country: String? = null,
        @RestQueryParameter(
            description = "List of statuses (\"ACTIVE\", \"SUSPENDED\") to filter members by. " +
                    "By default, only ACTIVE members are filtered. An MGM can view all suspended members. " +
                    "A regular member cannot view other suspended members, but can view itself in any status " +
                    "(e.g. even if it's suspended).",
            required = false,
        )
        statuses: List<String> = listOf(MemberInfoExtension.MEMBER_STATUS_ACTIVE),
    ): RestMemberInfoList

    /**
     * The [viewGroupParameters] method allows you to inspect the group parameters of the membership group, as
     * visible to the member represented by [holdingIdentityShortHash].
     *
     * Example usage:
     * ```
     * memberLookupOps.viewGroupParameters(holdingIdentityShortHash = "58B6030FABDD")
     * ```
     *
     * @param holdingIdentityShortHash Holding identity ID of the requesting member, which uniquely identifies the member
     * and its group.
     *
     * @return The group parameters of the membership group.
     */
    @HttpGET(
        path = "{holdingIdentityShortHash}/group-parameters",
        description = "This method retrieves the group parameters of the membership group.",
        responseDescription = "The group parameters of the membership group as a map"
    )
    fun viewGroupParameters(
        @RestPathParameter(description = "Holding identity ID of the requesting member. The result contains group " +
                "parameters visible to this member.")
        holdingIdentityShortHash: String
    ): RestGroupParameters
}
