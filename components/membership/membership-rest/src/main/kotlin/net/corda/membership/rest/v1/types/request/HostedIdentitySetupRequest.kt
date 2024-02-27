package net.corda.membership.rest.v1.types.request

/**
 * Request for setting up a hosted identity for a particular vNode.
 *
 * @param p2pTlsCertificateChainAlias The certificates chain alias.
 * @param useClusterLevelTlsCertificateAndKey Should the cluster-level P2P TLS certificate type and key be used or
 *     the virtual node certificate and key.
 * @param sessionKeysAndCertificates The list of session keys and certificates.
 */
data class HostedIdentitySetupRequest(
    val p2pTlsCertificateChainAlias: String,
    val useClusterLevelTlsCertificateAndKey: Boolean?,
    val sessionKeysAndCertificates: List<HostedIdentitySessionKeyAndCertificate>,
)

/**
 * @param sessionKeyId The session key identifier.
 * @param sessionCertificateChainAlias The certificate chain alias of the Session Key.
 *   Should be null if no PKI is used for sessions.
 * @param preferred true if this key is the preferred key.
 */
data class HostedIdentitySessionKeyAndCertificate(
    val sessionKeyId: String,
    val sessionCertificateChainAlias: String? = null,
    val preferred: Boolean = false,
)
