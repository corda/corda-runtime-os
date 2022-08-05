package net.corda.membership.httprpc.v1.types.request

/**
 * Request for setting up a hosted identity for a particular vNode.
 *
 * @param certificateChainAlias The certificates chain alias.
 * @param tlsTenantId The TLS tenant ID (either 'p2p' or the holdingIdentityShortHash, default to the holdingIdentityShortHash).
 * @param sessionKeyId The session key identifier (will use the first one if null).
 */
data class HostedIdentitySetupRequest(
    val certificateChainAlias: String,
    val tlsTenantId: String?,
    val sessionKeyId: String?
)