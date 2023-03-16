package net.corda.membership.rest.v1.types.request

/**
 * Request for setting up a hosted identity for a particular vNode.
 *
 * @param p2pTlsCertificateChainAlias The certificates chain alias.
 * @param useClusterLevelTlsCertificateAndKey Should the cluster-level P2P TLS certificate type and key be used or
 *     the virtual node certificate and key.
 * @param sessionKeyId The session key identifier (will use the first one if null).
 * @param sessionCertificateChainAlias The certificate chain alias of the Session Key. Should be null if no PKI is used for sessions.
 */
data class HostedIdentitySetupRequest(
    val p2pTlsCertificateChainAlias: String,
    val useClusterLevelTlsCertificateAndKey: Boolean?,
    val sessionKeyId: String?,
    val sessionCertificateChainAlias: String? = null,
)
