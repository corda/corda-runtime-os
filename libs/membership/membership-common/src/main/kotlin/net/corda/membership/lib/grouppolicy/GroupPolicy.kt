package net.corda.membership.lib.grouppolicy

import net.corda.membership.lib.exceptions.BadGroupPolicyException
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters.ProtocolMode
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters.SessionPkiMode
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters.TlsPkiMode
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters.TlsVersion
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters.TlsType
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.ProtocolParameters.SessionKeyPolicy
import net.corda.v5.base.types.MemberX500Name
import kotlin.jvm.Throws

interface MGMGroupPolicy : GroupPolicy
interface MemberGroupPolicy : GroupPolicy
interface InteropGroupPolicy : GroupPolicy

/**
 * Object representation of the group policy file which is packaged within a CPI and provides
 * group configurations.
 */
interface GroupPolicy {
    /**
     * Integer representing the version of the group policy file schema used for this group policy.
     *
     * @throws [BadGroupPolicyException] if the data is unavailable or cannot be parsed.
     */
    @get:Throws(BadGroupPolicyException::class)
    val fileFormatVersion: Int

    /**
     * Group Identifier.
     *
     * @throws [BadGroupPolicyException] if the data is unavailable or cannot be parsed.
     */
    @get:Throws(BadGroupPolicyException::class)
    val groupId: String

    /**
     * Fully qualified name of the registration protocol implementation required for the group.
     *
     * @throws [BadGroupPolicyException] if the data is unavailable or cannot be parsed.
     */
    @get:Throws(BadGroupPolicyException::class)
    val registrationProtocol: String

    /**
     * Fully qualified name of the synchronisation protocol implementation required for the group.
     *
     * @throws [BadGroupPolicyException] if the data is unavailable or cannot be parsed.
     */
    @get:Throws(BadGroupPolicyException::class)
    val synchronisationProtocol: String

    /**
     * Parameters required for the registration and synchronisation protocols.
     *
     * @throws [BadGroupPolicyException] if the data is unavailable or cannot be parsed.
     */
    @get:Throws(BadGroupPolicyException::class)
    val protocolParameters: ProtocolParameters

    /**
     * Set of P2P configuration parameters for the group.
     *
     * @throws [BadGroupPolicyException] if the data is unavailable or cannot be parsed.
     */
    @get:Throws(BadGroupPolicyException::class)
    val p2pParameters: P2PParameters

    /**
     * The MGM information packaged in the CPI.
     * Could be null if running a static network.
     *
     * @throws [BadGroupPolicyException] if the data is unavailable or cannot be parsed.
     */
    @get:Throws(BadGroupPolicyException::class)
    val mgmInfo: MGMInfo?

    /**
     * Properties for the cipher suite configuration.
     *
     * @throws [BadGroupPolicyException] if the data is unavailable or cannot be parsed.
     */
    @get:Throws(BadGroupPolicyException::class)
    val cipherSuite: CipherSuite

    interface ProtocolParameters {
        /**
         * The policy for session key handling.
         * [SessionKeyPolicy.COMBINED] means the same key is used for session initiation and ledger signing.
         * [SessionKeyPolicy.DISTINCT] means separate keys are used for sessions and ledger signing.
         *
         * @throws [BadGroupPolicyException] if the data is unavailable or cannot be parsed.
         */
        @get:Throws(BadGroupPolicyException::class)
        val sessionKeyPolicy: SessionKeyPolicy

        /**
         * Static network member configurations. Only present for static networks.
         * Extensible map of properties which represent a template to build a static network member.
         *
         * @throws [BadGroupPolicyException] if the data is unavailable or cannot be parsed.
         */
        @get:Throws(BadGroupPolicyException::class)
        val staticNetworkMembers: List<Map<String, Any>>?
    }

    interface P2PParameters {
        /**
         * A collection of trust root certificates for session initiation as PEM strigns.
         * This is optional. If `sessionPki` mode is [SessionPkiMode.NO_PKI] then this will return null.
         *
         * @throws [BadGroupPolicyException] if the data is unavailable or cannot be parsed.
         */
        @get:Throws(BadGroupPolicyException::class)
        val sessionTrustRoots: Collection<String>?

        /**
         * A collection of TLS trust root certificates as PEM strings.
         * Parsing validates for at least one certificate.
         *
         * @throws [BadGroupPolicyException] if the data is unavailable or cannot be parsed.
         */
        @get:Throws(BadGroupPolicyException::class)
        val tlsTrustRoots: Collection<String>

        /**
         * The session PKI mode.
         *
         * @throws [BadGroupPolicyException] if the data is unavailable or cannot be parsed.
         */
        @get:Throws(BadGroupPolicyException::class)
        val sessionPki: SessionPkiMode

        /**
         * The TLS PKI mode.
         *
         * @throws [BadGroupPolicyException] if the data is unavailable or cannot be parsed.
         */
        @get:Throws(BadGroupPolicyException::class)
        val tlsPki: TlsPkiMode

        /**
         * The TLS version to be used.
         *
         * @throws [BadGroupPolicyException] if the data is unavailable or cannot be parsed.
         */
        val tlsVersion: TlsVersion

        /**
         * The P2P protocol mode.
         *
         * @throws [BadGroupPolicyException] if the data is unavailable or cannot be parsed.
         */
        @get:Throws(BadGroupPolicyException::class)
        val protocolMode: ProtocolMode

        /**
         * The P2P TLS type.
         *
         * @throws [BadGroupPolicyException] if the data is unavailable or cannot be parsed.
         */
        @get:Throws(BadGroupPolicyException::class)
        val tlsType: TlsType

        /**
         * The MGM client certificate subject (mutual TLS only).
         */
        val mgmClientCertificateSubject: MemberX500Name?
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
