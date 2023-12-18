package net.corda.membership.lib

import net.corda.crypto.core.fullIdHash
import net.corda.crypto.core.parseSecureHash
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.membership.lib.notary.MemberNotaryDetails
import net.corda.utilities.NetworkHostAndPort
import net.corda.utilities.parse
import net.corda.utilities.parseList
import net.corda.utilities.parseOrNull
import net.corda.utilities.parseSet
import net.corda.v5.crypto.SecureHash
import net.corda.v5.membership.EndpointInfo
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import org.slf4j.LoggerFactory
import java.net.URL
import java.security.PublicKey
import java.time.Instant

class MemberInfoExtension {
    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)

        /** Key name for ledger keys property. */
        const val LEDGER_KEYS = "corda.ledger.keys"
        const val LEDGER_KEYS_ID = "$LEDGER_KEYS.%s.id"
        const val LEDGER_KEYS_KEY = "$LEDGER_KEYS.%s.pem"

        /** Key name for ledger key hashes property. */
        const val LEDGER_KEY_HASHES_KEY = "$LEDGER_KEYS.%s.hash"

        /** Key name for ledger key signature spec property. */
        const val LEDGER_KEY_SIGNATURE_SPEC = "$LEDGER_KEYS.%s.signature.spec"

        /** Key name for platform version property. */
        const val PLATFORM_VERSION = "corda.platformVersion"

        /** Key name for party property. */
        const val PARTY_NAME = "corda.name"
        const val SESSION_KEYS = "corda.session.keys"
        const val PARTY_SESSION_KEYS_ID = "$SESSION_KEYS.%s.id"
        const val PARTY_SESSION_KEYS = "$SESSION_KEYS.%s"
        const val KEYS_PEM_SUFFIX = "pem"
        const val PARTY_SESSION_KEYS_PEM = "$SESSION_KEYS.%s.$KEYS_PEM_SUFFIX"

        /** Key name for the session key hash **/
        const val SESSION_KEYS_HASH = "$SESSION_KEYS.%s.hash"

        /** Key name for the session key signature spec **/
        const val SESSION_KEYS_SIGNATURE_SPEC = "$SESSION_KEYS.%s.signature.spec"

        const val HISTORIC_SESSION_KEYS = "corda.historic.session.keys"
        const val HISTORIC_SESSION_KEYS_PEM = "$HISTORIC_SESSION_KEYS.%s.pem"
        const val HISTORIC_SESSION_KEYS_SIGNATURE_SPEC = "$HISTORIC_SESSION_KEYS.%s.signature.spec"

        /** Key name for notary service property. */
        const val NOTARY_SERVICE_PARTY_NAME = "corda.notaryService.name"
        const val NOTARY_SERVICE_SESSION_KEY = "corda.notaryService.session.key"

        /** Key name for serial property. */
        const val SERIAL = "corda.serial"

        /** Key name for status property. */
        const val STATUS = "corda.status"

        /** Key name for creation time **/
        const val CREATION_TIME = "corda.creationTime"

        /** Key name for endpoints property. */
        const val ENDPOINTS = "corda.endpoints"

        const val URL_KEY = "corda.endpoints.%s.connectionURL"
        const val PROTOCOL_VERSION = "corda.endpoints.%s.protocolVersion"

        /** Key name for softwareVersion property. */
        const val SOFTWARE_VERSION = "corda.softwareVersion"

        /** Key name for group identifier property. */
        const val GROUP_ID = "corda.groupId"

        /** Key name for ECDH key property. */
        const val ECDH_KEY = "corda.ecdh.key"

        /** Key name for modified time property. */
        const val MODIFIED_TIME = "corda.modifiedTime"

        /** Key name for MGM property. */
        const val IS_MGM = "corda.mgm"

        /**
         * Key name for identifying static network MGM property.
         * A static network MGM is not backed by a virtual node so may need different handling.
         */
        const val IS_STATIC_MGM = "corda.mgm.static"

        /** Key name for the ID of the registration in which the current member info was approved. */
        const val REGISTRATION_ID = "corda.registration.id"

        /** Key name for the CPI name **/
        const val MEMBER_CPI_NAME = "corda.cpi.name"

        /** Key name for the CPI version **/
        const val MEMBER_CPI_VERSION = "corda.cpi.version"

        /** Key name for the CPI signer summary hash **/
        const val MEMBER_CPI_SIGNER_HASH = "corda.cpi.signer.summary.hash"

        /** Active nodes can transact in the Membership Group with the other nodes. **/
        const val MEMBER_STATUS_ACTIVE = "ACTIVE"

        /**
         * Membership request has been submitted but Group Manager still hasn't responded to it. Nodes with this status can't
         * communicate with the other nodes in the Membership Group.
         */
        const val MEMBER_STATUS_PENDING = "PENDING"

        /**
         * Suspended nodes can't communicate with the other nodes in the Membership Group. They are still members of the Membership Group
         * meaning they can be re-activated.
         */
        const val MEMBER_STATUS_SUSPENDED = "SUSPENDED"

        /**
         * PREFIX for notary roles name
         */
        const val ROLES_PREFIX = "corda.roles"

        /**
         * Prefix for custom properties
         */
        const val CUSTOM_KEY_PREFIX = "ext"

        /**
         * Notary role name
         */
        const val NOTARY_ROLE = "notary"

        /**
         * Notary role properties
         */
        const val NOTARY_KEYS = "corda.notary.keys"
        const val NOTARY_SERVICE_NAME = "corda.notary.service.name"
        const val NOTARY_SERVICE_BACKCHAIN_REQUIRED = "corda.notary.service.backchain.required"
        const val NOTARY_SERVICE_PROTOCOL = "corda.notary.service.flow.protocol.name"
        const val NOTARY_SERVICE_PROTOCOL_VERSIONS = "corda.notary.service.flow.protocol.version.%s"
        const val NOTARY_KEYS_ID = "corda.notary.keys.%s.id"
        const val NOTARY_KEY_PEM = "corda.notary.keys.%s.pem"
        const val NOTARY_KEY_HASH = "corda.notary.keys.%s.hash"
        const val NOTARY_KEY_SPEC = "corda.notary.keys.%s.signature.spec"

        /** Key name for TLS certificate subject. */
        const val TLS_CERTIFICATE_SUBJECT = "corda.tls.certificate.subject"

        /** Group identifier. UUID as a String. */
        @JvmStatic
        val MemberInfo.groupId: String
            get() = memberProvidedContext.parse(GROUP_ID)

        /** Member holding identity. */
        @JvmStatic
        val MemberInfo.holdingIdentity: HoldingIdentity
            get() = HoldingIdentity(groupId = groupId, x500Name = name)

        /** Member ID. */
        @JvmStatic
        val MemberInfo.id: String
            get() = holdingIdentity.shortHash.value

        /** List of P2P endpoints for member's node. */
        @JvmStatic
        val MemberInfo.addresses: List<NetworkHostAndPort>
            get() = endpoints.map {
                with(URL(it.url)) { NetworkHostAndPort(host, port) }
            }

        /**  List of P2P endpoints for member's node. */
        @JvmStatic
        val MemberInfo.endpoints: List<EndpointInfo>
            get() = memberProvidedContext.parseList(ENDPOINTS)

        /** Corda-Release-Version. */
        @JvmStatic
        val MemberInfo.softwareVersion: String
            get() = memberProvidedContext.parse(SOFTWARE_VERSION)

        /** Status of Membership. */
        @JvmStatic
        val MemberInfo.status: String
            get() = mgmProvidedContext.parse(STATUS)

        /**
         * The last time Membership was modified. Can be null.
         * MGM won't have modifiedTime,
         * also Members won't have it at the beginning when
         * they create their MemberInfo proposals
         * this is because only MGM can populate this information.
         * */
        @JvmStatic
        val MemberInfo.modifiedTime: Instant?
            get() = mgmProvidedContext.parse(MODIFIED_TIME)

        /** TLS certificate subject for member. */
        @JvmStatic
        val MemberInfo.tlsCertificateSubject: String?
            get() = memberProvidedContext.parseOrNull(TLS_CERTIFICATE_SUBJECT)

        /** Collection of ledger key hashes for member's node. */
        @JvmStatic
        val MemberInfo.ledgerKeyHashes: Collection<SecureHash>
            get() = getOrCalculateKeyHashes(LEDGER_KEYS) { ledgerKeys }

        /**
         * The member session initiation keys
         */
        @JvmStatic
        val MemberInfo.sessionInitiationKeys: Collection<PublicKey>
            get() = memberProvidedContext.parseList(SESSION_KEYS)

        /**
         * [SecureHash] for the session initiation key.
         * The hash value should be stored in the member context, but as a fallback it is calculated if not available.
         * It is preferable to always store this in the member context to avoid the repeated calculation.
         */
        @JvmStatic
        val MemberInfo.sessionKeyHashes: Collection<SecureHash>
            get() = getOrCalculateKeyHashes(SESSION_KEYS) { sessionInitiationKeys }

        /**
         * The now historic member session initiation keys.
         */
        @JvmStatic
        val MemberInfo.historicSessionInitiationKeys: Collection<PublicKey>
            get() = memberProvidedContext.parseList(HISTORIC_SESSION_KEYS)

        /**
         * [SecureHash] for the historic session initiation keys.
         * The hash value should be stored in the member context, but as a fallback it is calculated if not available.
         * It is preferable to always store this in the member context to avoid the repeated calculation.
         */
        @JvmStatic
        val MemberInfo.historicSessionKeyHashes: Collection<SecureHash>
            get() = getOrCalculateKeyHashes(HISTORIC_SESSION_KEYS) { historicSessionInitiationKeys }

        /** Denotes whether this [MemberInfo] represents an MGM node. */
        @JvmStatic
        val MemberInfo.isMgm: Boolean
            get() = mgmProvidedContext.parseOrNull(IS_MGM) ?: false

        /** Denotes whether this [MemberInfo] represents a static network MGM. */
        @JvmStatic
        val MemberInfo.isStaticMgm: Boolean
            get() = mgmProvidedContext.parseOrNull(IS_STATIC_MGM) ?: false

        /**
         * Return the notary details if the member is a notary.
         */
        @JvmStatic
        val MemberInfo.notaryDetails: MemberNotaryDetails?
            get() = if (isNotary()) {
                memberProvidedContext.parse("corda.notary")
            } else {
                null
            }

        @JvmStatic
        fun MemberInfo.isNotary(): Boolean = memberProvidedContext
            .entries
            .filter {
                it.key.startsWith(ROLES_PREFIX)
            }.any {
                it.value == NOTARY_ROLE
            }

        /** Return the key used for hybrid encryption. Only MGMs should have a value set for ecdh key. */
        @JvmStatic
        val MemberInfo.ecdhKey: PublicKey?
            get() = memberProvidedContext.parseOrNull(ECDH_KEY)

        /**
         * Return the [CpiIdentifier] from the [MemberInfo]
         */
        @JvmStatic
        val MemberInfo.cpiInfo: CpiIdentifier
            get() = CpiIdentifier(
                memberProvidedContext.parse(MEMBER_CPI_NAME),
                memberProvidedContext.parse(MEMBER_CPI_VERSION),
                memberProvidedContext.parse<String>(MEMBER_CPI_SIGNER_HASH).let {
                    parseSecureHash(it)
                }
            )

        /**
         * Return a list of current and previous (rotated) notary keys. Key at index 0 is always the latest
         * added notary key. Might be an empty list if the member is not a notary VNode.
         */
        @JvmStatic
        val MemberInfo.notaryKeys: List<PublicKey> get() = memberProvidedContext.parseList(NOTARY_KEYS)

        private fun MemberInfo.getOrCalculateKeyHashes(
            key: String,
            keyListGetter: () -> Collection<PublicKey>
        ) = memberProvidedContext.parseSet<SecureHash>(key).ifEmpty {
            logger.warn(
                "Calculating the key hash for $name in group $groupId for property $key. " +
                        "It is preferable to store this hash in the member context to avoid calculating on each access."
            )
            keyListGetter().map { it.fullIdHash() }
        }
    }
}
