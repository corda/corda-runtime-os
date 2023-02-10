package net.corda.membership.lib

import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.membership.lib.notary.MemberNotaryDetails
import net.corda.utilities.NetworkHostAndPort
import net.corda.v5.base.util.parse
import net.corda.v5.base.util.parseList
import net.corda.v5.base.util.parseOrNull
import net.corda.v5.base.util.parseSet
import net.corda.v5.crypto.PublicKeyHash
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.calculateHash
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
        const val LEDGER_KEYS_KEY = "corda.ledger.keys.%s.pem"

        /** Key name for ledger key hashes property. */
        const val LEDGER_KEY_HASHES = "corda.ledger.keys"
        const val LEDGER_KEY_HASHES_KEY = "corda.ledger.keys.%s.hash"

        /** Key name for ledger key signature spec property. */
        const val LEDGER_KEY_SIGNATURE_SPEC = "corda.ledger.keys.%s.signature.spec"

        /** Key name for platform version property. */
        const val PLATFORM_VERSION = "corda.platformVersion"

        /** Key name for party property. */
        const val PARTY_NAME = "corda.name"
        const val PARTY_SESSION_KEY = "corda.session.key"

        /** Key name for the session key hash **/
        const val SESSION_KEY_HASH = "corda.session.key.hash"

        /** Key name for the session key signature spec **/
        const val SESSION_KEY_SIGNATURE_SPEC = "corda.session.key.signature.spec"

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

        /** Key name for certificate property. */
        const val CERTIFICATE = "corda.session.certificate"

        /** Key name for modified time property. */
        const val MODIFIED_TIME = "corda.modifiedTime"

        /** Key name for MGM property. */
        const val IS_MGM = "corda.mgm"

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

        /** Membership request was declined by the Group Manager. **/
        const val MEMBER_STATUS_DECLINED = "DECLINED"

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
         * Notary role name
         */
        const val NOTARY_ROLE = "notary"

        /**
         * Notary role properties
         */
        const val NOTARY_KEYS = "corda.notary.keys"
        const val NOTARY_SERVICE_NAME = "corda.notary.service.name"
        const val NOTARY_SERVICE_PLUGIN = "corda.notary.service.plugin"
        const val NOTARY_KEY_PEM = "corda.notary.keys.%s.pem"
        const val NOTARY_KEY_HASH = "corda.notary.keys.%s.hash"
        const val NOTARY_KEY_SPEC = "corda.notary.keys.%s.signature.spec"

        /**
         * Interop role name
         */
        const val INTEROP_ROLE = "interop"

        /**
         * Interop role properties
         */
        const val INTEROP_SERVICE_NAME = "corda.notary.service.name"

        /** Key name for TLS certificate subject. */
        const val TLS_CERTIFICATE_SUBJECT = "corda.tls.certificate.subject"

        /** Identity certificate or null for non-PKI option. Certificate subject and key should match party */
        @JvmStatic
        val MemberInfo.certificate: List<String>
            get() = memberProvidedContext.parseList(CERTIFICATE)

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

        /** Collection of ledger key hashes for member's node. */
        @JvmStatic
        val MemberInfo.ledgerKeyHashes: Collection<PublicKeyHash>
            get() = memberProvidedContext.parseSet(LEDGER_KEY_HASHES)

        /**
         * [PublicKeyHash] for the session initiation key.
         * The hash value should be stored in the member context, but as a fallback it is calculated if not available.
         * It is preferable to always store this in the member context to avoid the repeated calculation.
         */
        @JvmStatic
        val MemberInfo.sessionKeyHash: PublicKeyHash
            get() = memberProvidedContext.parseOrNull(SESSION_KEY_HASH) ?: sessionInitiationKey.calculateHash().also {
                logger.warn("Calculating the session key hash for $name in group $groupId. " +
                        "It is preferable to store this hash in the member context to avoid calculating on each access.")
            }

        /** Denotes whether this [MemberInfo] represents an MGM node. */
        @JvmStatic
        val MemberInfo.isMgm: Boolean
            get() = mgmProvidedContext.parseOrNull(IS_MGM) ?: false

        /**
         * Return the notary details if the member is a notary.
         */
        @JvmStatic
        val MemberInfo.notaryDetails: MemberNotaryDetails?
            get() = if (
                memberProvidedContext
                    .entries
                    .filter {
                        it.key.startsWith(ROLES_PREFIX)
                    }.any {
                        it.value == NOTARY_ROLE
                    }
            ) {
                memberProvidedContext.parse("corda.notary")
            } else {
                null
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
                    SecureHash.parse(it)
                }
            )

        /**
         * Return a list of current and previous (rotated) notary keys. Key at index 0 is always the latest
         * added notary key. Might be an empty list if the member is not a notary VNode.
         */
        @JvmStatic
        val MemberInfo.notaryKeys: List<PublicKey> get() = memberProvidedContext.parseList(NOTARY_KEYS)
    }
}
