package net.corda.membership.identity

import net.corda.v5.base.util.NetworkHostAndPort
import net.corda.v5.base.util.parse
import net.corda.v5.base.util.parseList
import net.corda.v5.crypto.PublicKeyHash
import net.corda.v5.membership.identity.EndpointInfo
import net.corda.v5.membership.identity.MemberInfo
import java.net.URL
import java.time.Instant

class MemberInfoExtension {
    companion object {
        /** Key name for identity keys property. */
        const val IDENTITY_KEYS = "corda.identityKeys"
        const val IDENTITY_KEYS_KEY = "corda.identityKeys.%s"

        /** Key name for identity key hashes property. */
        const val IDENTITY_KEY_HASHES = "corda.identityKeyHashes"
        const val IDENTITY_KEY_HASHES_KEY = "corda.identityKeyHashes.%s"

        /** Key name for platform version property. */
        const val PLATFORM_VERSION = "corda.platformVersion"

        /** Key name for party property. */
        const val PARTY_NAME = "corda.party.name"
        const val PARTY_OWNING_KEY = "corda.party.owningKey"

        /** Key name for notary service property. */
        const val NOTARY_SERVICE_PARTY_NAME = "corda.notaryServiceParty.name"
        const val NOTARY_SERVICE_PARTY_KEY = "corda.notaryServiceParty.owningKey"

        /** Key name for serial property. */
        const val SERIAL = "corda.serial"

        /** Key name for status property. */
        const val STATUS = "corda.status"

        /** Key name for endpoints property. */
        const val ENDPOINTS = "corda.endpoints"

        const val URL_KEY = "corda.endpoints.%s.connectionURL"
        const val PROTOCOL_VERSION = "corda.endpoints.%s.protocolVersion"

        /** Key name for softwareVersion property. */
        const val SOFTWARE_VERSION = "corda.softwareVersion"

        /** Key name for group identifier property. */
        const val GROUP_ID = "corda.groupId"

        /** Key name for certificate property. */
        const val CERTIFICATE = "corda.certificate"

        /** Key name for modified time property. */
        const val MODIFIED_TIME = "corda.modifiedTime"

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

        /** Identity certificate or null for non-PKI option. Certificate subject and key should match party */
        // TODO we will need a CertPath converter somewhere
        /*@JvmStatic
        val MemberInfo.certificate: CertPath?
            get() = memberProvidedContext.readAs(CERTIFICATE)*/

        /** Group identifier. UUID as a String. */
        @JvmStatic
        val MemberInfo.groupId: String
            get() = memberProvidedContext.parse(GROUP_ID)

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

        /** List of identity key hashes for member's node. */
        @JvmStatic
        val MemberInfo.identityKeyHashes: List<PublicKeyHash>
            get() = memberProvidedContext.parseList(IDENTITY_KEY_HASHES)
    }
}