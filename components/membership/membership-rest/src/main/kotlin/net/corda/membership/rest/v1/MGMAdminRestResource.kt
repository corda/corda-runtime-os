package net.corda.membership.rest.v1

import net.corda.rest.RestResource
import net.corda.rest.annotations.ClientRequestBodyParameter
import net.corda.rest.annotations.HttpPOST
import net.corda.rest.annotations.HttpRestResource
import net.corda.rest.annotations.RestPathParameter

/**
 * TODO
 */
@HttpRestResource(
    name = "MGM Admin API",
    description = "",
    path = "admin"
)
interface MGMAdminRestResource : RestResource {
    /**
     * TODO
     */
    @HttpPOST(
        path = "{holdingIdentityShortHash}/decline",
        description = ""
    )
    fun forceDeclineRegistrationRequest(
        @RestPathParameter(
            description = "The holding identity ID of the MGM of the membership group"
        )
        holdingIdentityShortHash: String,
        @ClientRequestBodyParameter(
            description = "ID of the registration request"
        )
        requestId: String
    )
}