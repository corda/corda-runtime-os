package net.corda.membership.rest.v1

import net.corda.rest.RestResource
import net.corda.rest.annotations.HttpPOST
import net.corda.rest.annotations.HttpRestResource
import net.corda.rest.annotations.RestApiVersion
import net.corda.rest.annotations.RestPathParameter

/**
 * The MGM Admin API consists of endpoints used to carry out administrative tasks on membership groups. A membership
 * group is a logical grouping of a number of Corda Identities to communicate and transact with one another with a
 * specific set of CorDapps. The API allows the MGM to perform actions such as force decline registration requests which
 * may be displaying unexpected behaviour. This API should only be used by the MGM and under exceptional circumstances.
 */
@HttpRestResource(
    name = "MGM Admin",
    description = "The MGM Admin API consists of endpoints used to carry out administrative tasks on membership " +
        "groups. A membership group is a logical grouping of a number of Corda Identities to communicate and " +
        "transact with one another with a specific set of CorDapps. The API allows the MGM to perform actions " +
        "such as force decline registration requests which may be displaying unexpected behaviour. This API " +
        "should only be used by the MGM under exceptional circumstances.",
    path = "mgmadmin"
)
interface MGMAdminRestResource : RestResource {
    /**
     * The [forceDeclineRegistrationRequest] method enables you to force decline an in-progress registration request
     * that may be stuck or displaying some other unexpected behaviour. This method should only be used under
     * exceptional circumstances.
     *
     * Example usage:
     * ```
     * mgmOps.forceDeclineRegistrationRequest("58B6030FABDD", "3B9A266F96E2")
     * ```
     *
     * @param holdingIdentityShortHash The holding identity ID of the MGM of the membership group.
     * @param requestId ID of the registration request.
     */
    @HttpPOST(
        path = "{holdingIdentityShortHash}/force-decline/{requestId}",
        minVersion = RestApiVersion.C5_1,
        description = "This method enables you to force decline an in-progress registration request that may be stuck" +
            " or displaying some other unexpected behaviour."
    )
    fun forceDeclineRegistrationRequest(
        @RestPathParameter(
            description = "The holding identity ID of the MGM of the membership group"
        )
        holdingIdentityShortHash: String,
        @RestPathParameter(
            description = "ID of the registration request"
        )
        requestId: String
    )
}
