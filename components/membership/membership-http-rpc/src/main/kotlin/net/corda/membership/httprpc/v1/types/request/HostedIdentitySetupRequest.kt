package net.corda.membership.httprpc.v1.types.request

/**
 * Request for setting up a hosted identity for a particular vNode.
 *
 * @param p2pTlsCertificateChainAlias The certificates chain alias.
 * @param useClusterLevelTlsCertificateAndKey Should the P2P cluster level TLS certificate type and  P2P key will be used or
 *     the virtual node cluster and key.
 * @param sessionKeyTenantId The tenant ID under which the session initiation key is stored (defaults to holdingIdentityShortHash).
 * @param sessionKeyId The session key identifier (will use the first one if null).
 */
data class HostedIdentitySetupRequest(
    val p2pTlsCertificateChainAlias: String,
    val useClusterLevelTlsCertificateAndKey: Boolean?,
    val sessionKeyTenantId: String?,
    val sessionKeyId: String?
)
