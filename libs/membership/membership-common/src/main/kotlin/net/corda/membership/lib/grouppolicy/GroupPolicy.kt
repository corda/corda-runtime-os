package net.corda.membership.lib.grouppolicy

import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters.ProtocolMode
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters.SessionPkiMode
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters.TlsPkiMode
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters.TlsVersion
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.ProtocolParameters.SessionKeyPolicy

/**
 * Object representation of the group policy file which is packaged within a CPI and provides
 * group configurations.
 */
interface GroupPolicy {
    /**
     * Integer representing the version of the group policy file schema used for this group policy.
     */
    val fileFormatVersion: Int

    /**
     * Group Identifier.
     */
    val groupId: String

    /**
     * Fully qualified name of the registration protocol implementation required for the group.
     */
    val registrationProtocol: String

    /**
     * Fully qualified name of the synchronisation protocol implementation required for the group.
     */
    val synchronisationProtocol: String

    /**
     * Parameters required for the registration and synchronisation protocols.
     */
    val protocolParameters: ProtocolParameters

    /**
     * Set of P2P configuration parameters for the group.
     */
    val p2pParameters: P2PParameters

    /**
     * The MGM information packaged in the CPI.
     * Could be null if running a static network.
     */
    val mgmInfo: MGMInfo?

    /**
     * Properties for the cipher suite configuration.
     */
    val cipherSuite: CipherSuite

    interface ProtocolParameters {
        /**
         * The policy for session key handling.
         * [SessionKeyPolicy.COMBINED] means the same key is used for session initiation and ledger signing.
         * [SessionKeyPolicy.DISTINCT] means separate keys are used for sessions and ledger signing.
         */
        val sessionKeyPolicy: SessionKeyPolicy

        /**
         * Static network member configurations. Only present for static networks.
         * Extensible map of properties which represent a template to build a static network member.
         */
        val staticNetworkMembers: List<Map<String, Any>>?
    }

    interface P2PParameters {
        /**
         * A collection of trust root certificates for session initiation as PEM strigns.
         * This is optional. If `sessionPki` mode is [SessionPkiMode.NO_PKI] then this will return null.
         */
        val sessionTrustRoots: Collection<String>?

        /**
         * A collection of TLS trust root certificates as PEM strings.
         * Parsing validates for at least one certificate.
         */
        val tlsTrustRoots: Collection<String>

        /**
         * The session PKI mode.
         */
        val sessionPki: SessionPkiMode

        /**
         * The TLS PKI mode.
         */
        val tlsPki: TlsPkiMode

        /**
         * The TLS version to be used.
         */
        val tlsVersion: TlsVersion

        /**
         * The P2P protocol mode.
         */
        val protocolMode: ProtocolMode
    }

    /**
     * Key-Value representation of the MGM's member context. Used for initial connections to the MGM during registration.
     */
    interface MGMInfo : Map<String, String>

    /**
     * Map of key-value pairs defining the cipher suite configuration.
     */
    interface CipherSuite : Map<String, String>
}