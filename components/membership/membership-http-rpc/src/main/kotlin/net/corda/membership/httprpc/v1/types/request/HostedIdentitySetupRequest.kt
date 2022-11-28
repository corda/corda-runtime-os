package net.corda.membership.httprpc.v1.types.request

/**
 * Request for setting up a hosted identity for a particular vNode (for one way TLS network).
 *
 * @param p2pServerTlsCertificateChainAlias The server certificates chain alias.
 * @param p2pClientTlsCertificateChainAlias The client certificates chain alias (leave null for mutual TLS network).
 * @param useClusterLevelTlsCertificateAndKey Should the cluster-level P2P TLS certificate type and key be used or
 *     the virtual node certificate and key.
 * @param useClusterLevelSessionCertificateAndKey Should the cluster-level P2P SESSION certificate type and key be used or
 *     the virtual node certificate and key.
 * @param sessionKeyId The session key identifier (will use the first one if null).
 * @param sessionCertificateChainAlias The certificate chain alias of the Session Key. Should be null if no PKI is used for sessions.
 */
data class HostedIdentitySetupRequest(
    val p2pServerTlsCertificateChainAlias: String,
    val p2pClientTlsCertificateChainAlias: String? = null,
    val useClusterLevelTlsCertificateAndKey: Boolean?,
    val useClusterLevelSessionCertificateAndKey: Boolean?,
    val sessionKeyId: String?,
    val sessionCertificateChainAlias: String? = null,
)
