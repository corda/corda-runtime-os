package net.corda.membership.httprpc.v1.types.request

/**
 * Request for setting up a hosted identity for a particular vNode.
 *
 * @param p2pTlsCertificateChainAlias The certificates chain alias.
 * @param useClusterLevelTlsCertificateAndKey Should the cluster-level P2P TLS certificate type and key be used or
 *     the virtual node certificate and key.
 * @param useClusterLevelSessionCertificateAndKey Should the cluster-level P2P SESSION certificate type and key be used or
 *     the virtual node certificate and key.
 * @param sessionKeyId The session key identifier (will use the first one if null).
 */
data class HostedIdentitySetupRequest(
    val p2pTlsCertificateChainAlias: String,
    val useClusterLevelTlsCertificateAndKey: Boolean?,
    val useClusterLevelSessionCertificateAndKey: Boolean?,
    val sessionKeyId: String?
)
