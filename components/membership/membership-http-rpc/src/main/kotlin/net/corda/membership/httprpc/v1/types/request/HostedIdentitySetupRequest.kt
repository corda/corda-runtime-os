package net.corda.membership.httprpc.v1.types.request

/**
 * Request for setting up a hosted identity for a particular vNode.
 *
 * @param p2pTlsCertificateChainAlias The certificates chain alias of the TLS certificate.
 * @param p2pTlsTenantId The TLS tenant ID (either 'p2p' or the holdingIdentityShortHash, defaults to the holdingIdentityShortHash).
 * @param sessionKeyTenantId The tenant ID under which the session initiation key is stored (defaults to holdingIdentityShortHash).
 * @param sessionKeyId The session key identifier (will use the first one if null).
 * @param sessionCertificateChainAlias The certificate chain alias of the Session Key. Should be null if no PKI is used for sessions.
 */
data class HostedIdentitySetupRequest(
    val p2pTlsCertificateChainAlias: String,
    val p2pTlsTenantId: String?,
    val sessionKeyTenantId: String?,
    val sessionKeyId: String?,
    val sessionCertificateChainAlias: String? = null
)