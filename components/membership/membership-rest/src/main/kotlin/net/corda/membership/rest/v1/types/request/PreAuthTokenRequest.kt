package net.corda.membership.rest.v1.types.request

import java.time.Duration

/**
 * Request generation of a preAuthToken.
 *
 * @param ownerX500Name The X500 name of the member to preauthorize.
 * @param ttl A (time-to-live) duration after which this token will become invalid. If unset then the token if valid forever.
 * The duration must be specified using the ISO-8601 duration format PnDTnHnMn.nS, where n is an integer, D is days, H is hours and M is
 * minutes. Some examples: PT15M (15 minutes), P4D (4 days), P1DT2H2M (1 day 2 hours and 2 minutes).
 * @param remarks Some optional remarks.
 */
data class PreAuthTokenRequest (
    val ownerX500Name: String,
    val ttl: Duration? = null,
    val remarks: String? = null
)