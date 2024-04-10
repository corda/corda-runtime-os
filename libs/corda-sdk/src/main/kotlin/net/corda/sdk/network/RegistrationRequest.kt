package net.corda.sdk.network

import net.corda.membership.lib.MemberInfoExtension
import net.corda.membership.rest.v1.types.request.MemberRegistrationRequest
import net.corda.membership.rest.v1.types.response.KeyPairIdentifier

class RegistrationRequest {

    /**
     * Create an object containing the necessary registration context for an MGM
     * @param mtls if true will use Mutual TLS otherwise will use OneWay
     * @param p2pGatewayUrls collection of URLs to be used for P2p communication
     * @param sessionKey key ID for the generated session
     * @param ecdhKey key ID for the generated ecdh key
     * @param tlsTrustRoot value of certificate from Certificate Authority
     * @return [MemberRegistrationRequest]
     */
    fun createMgmRegistrationRequest(
        mtls: Boolean,
        p2pGatewayUrls: Collection<String>,
        sessionKey: KeyPairIdentifier,
        ecdhKey: KeyPairIdentifier,
        tlsTrustRoot: String
    ): MemberRegistrationRequest {
        val tlsType = if (mtls) {
            "Mutual"
        } else {
            "OneWay"
        }

        val endpoints = p2pGatewayUrls.flatMapIndexed { index, url ->
            listOf(
                MemberInfoExtension.URL_KEY.format(index) to url,
                MemberInfoExtension.PROTOCOL_VERSION.format(index) to "1",
            )
        }

        return MemberRegistrationRequest(
            context = mapOf(
                MemberInfoExtension.PARTY_SESSION_KEYS_ID.format(0) to sessionKey.id,
                "corda.ecdh.key.id" to ecdhKey.id,
                "corda.group.protocol.registration"
                    to "net.corda.membership.impl.registration.dynamic.member.DynamicMemberRegistrationService",
                "corda.group.protocol.synchronisation"
                    to "net.corda.membership.impl.synchronisation.MemberSynchronisationServiceImpl",
                "corda.group.protocol.p2p.mode" to "Authenticated_Encryption",
                "corda.group.key.session.policy" to "Distinct",
                "corda.group.tls.type" to tlsType,
                "corda.group.pki.session" to "NoPKI",
                "corda.group.pki.tls" to "Standard",
                "corda.group.tls.version" to "1.3",
                "corda.group.trustroot.tls.0" to tlsTrustRoot,
            ) + endpoints
        )
    }

    /**
     * Create an object containing the necessary registration context for a member
     * @param preAuthToken optional pre-auth token
     * @param roles optional member roles
     * @param customProperties Map of custom properties
     * @param sessionKey key ID for the generated session
     * @param ledgerKey ID for the generated ledger key
     * @return [MemberRegistrationRequest]
     */
    @Suppress("LongParameterList")
    fun createMemberRegistrationRequest(
        preAuthToken: String?,
        roles: Set<MemberRole>?,
        customProperties: Map<String, String>?,
        p2pGatewayUrls: Collection<String>,
        sessionKey: KeyPairIdentifier,
        ledgerKey: KeyPairIdentifier,
    ): MemberRegistrationRequest {
        val endpoints = p2pGatewayUrls.flatMapIndexed { index, url ->
            listOf(
                MemberInfoExtension.URL_KEY.format(index) to url,
                MemberInfoExtension.PROTOCOL_VERSION.format(index) to "1",
            )
        }

        val context = mapOf(
            MemberInfoExtension.PARTY_SESSION_KEYS_ID.format(0) to sessionKey.id,
            MemberInfoExtension.SESSION_KEYS_SIGNATURE_SPEC.format(0) to "SHA256withECDSA",
            MemberInfoExtension.LEDGER_KEYS_ID.format(0) to ledgerKey.id,
            MemberInfoExtension.LEDGER_KEY_SIGNATURE_SPEC.format(0) to "SHA256withECDSA",
        )

        val preAuth = preAuthToken?.let { listOf("corda.auth.token" to it) } ?: emptyList()
        val roleProperty = roles?.mapIndexed { index: Int, memberRole: MemberRole ->
            "${MemberInfoExtension.ROLES_PREFIX}.$index" to memberRole.value
        } ?: emptyList()

        val extProperties =
            customProperties?.filterKeys { it.startsWith("${MemberInfoExtension.CUSTOM_KEY_PREFIX}.") } ?: emptyMap()

        return MemberRegistrationRequest(context = context + preAuth + roleProperty + extProperties + endpoints)
    }

    /**
     * Create an object containing the necessary registration context for a member
     * @param preAuthToken optional pre-auth token
     * @param roles collection of member roles, expecting at least one roll to be a NOTARY
     * @param customProperties Map of custom properties
     * @param sessionKey key ID for the generated session
     * @param notaryKey ID for the generated notary key
     * @return registration context information as a Map
     */
    @Suppress("LongParameterList")
    fun createNotaryRegistrationRequest(
        preAuthToken: String?,
        roles: Set<MemberRole>,
        customProperties: Map<String, String>?,
        p2pGatewayUrls: Collection<String>,
        sessionKey: KeyPairIdentifier,
        notaryKey: KeyPairIdentifier,
    ): MemberRegistrationRequest {
        require(roles.contains(MemberRole.NOTARY)) { "Must specify the role as notary" }

        val endpoints = p2pGatewayUrls.flatMapIndexed { index, url ->
            listOf(
                MemberInfoExtension.URL_KEY.format(index) to url,
                MemberInfoExtension.PROTOCOL_VERSION.format(index) to "1",
            )
        }

        val notaryServiceName = customProperties?.get(MemberInfoExtension.NOTARY_SERVICE_NAME)
            ?: throw IllegalArgumentException(
                "When specifying a NOTARY role, " +
                    "you also need to specify a custom property for its name under ${MemberInfoExtension.NOTARY_SERVICE_NAME}.",
            )
        val isBackchainRequired = customProperties[MemberInfoExtension.NOTARY_SERVICE_BACKCHAIN_REQUIRED] ?: true
        val notaryProtocol =
            customProperties[MemberInfoExtension.NOTARY_SERVICE_PROTOCOL] ?: "com.r3.corda.notary.plugin.nonvalidating"

        val context = mapOf(
            MemberInfoExtension.PARTY_SESSION_KEYS_ID.format(0) to sessionKey.id,
            MemberInfoExtension.SESSION_KEYS_SIGNATURE_SPEC.format(0) to "SHA256withECDSA",
            MemberInfoExtension.NOTARY_SERVICE_NAME to notaryServiceName,
            MemberInfoExtension.NOTARY_SERVICE_BACKCHAIN_REQUIRED to "$isBackchainRequired",
            MemberInfoExtension.NOTARY_SERVICE_PROTOCOL to notaryProtocol,
            MemberInfoExtension.NOTARY_SERVICE_PROTOCOL_VERSIONS.format("0") to "1",
            MemberInfoExtension.NOTARY_KEYS_ID.format("0") to notaryKey.id,
            MemberInfoExtension.NOTARY_KEY_SPEC.format("0") to "SHA256withECDSA",
        )

        val preAuth = preAuthToken?.let { listOf("corda.auth.token" to it) } ?: emptyList()

        val roleProperty = roles.mapIndexed { index: Int, memberRole: MemberRole ->
            "${MemberInfoExtension.ROLES_PREFIX}.$index" to memberRole.value
        }

        val extProperties = customProperties.filterKeys { it.startsWith("${MemberInfoExtension.CUSTOM_KEY_PREFIX}.") }

        return MemberRegistrationRequest(context = context + preAuth + roleProperty + extProperties + endpoints)
    }
}

enum class MemberRole(val value: String) {
    NOTARY(MemberInfoExtension.NOTARY_ROLE),
}
