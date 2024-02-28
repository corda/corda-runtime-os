package net.corda.sdk.network

import net.corda.membership.lib.MemberInfoExtension

class RegistrationContext {

    fun getMgm(
        mtls: Boolean,
        p2pGatewayUrls: List<String>,
        sessionKeyId: String,
        ecdhKeyId: String,
        tlsTrustRoot: String
    ): Map<String, Any?> {
        val tlsType = if (mtls) {
            "Mutual"
        } else {
            "OneWay"
        }

        val endpoints = mutableMapOf<String, String>()

        p2pGatewayUrls.mapIndexed { index, url ->
            endpoints[MemberInfoExtension.URL_KEY.format(index)] = url
            endpoints[MemberInfoExtension.PROTOCOL_VERSION.format(index)] = "1"
        }

        return mapOf(
            MemberInfoExtension.PARTY_SESSION_KEYS_ID.format(0) to sessionKeyId,
            "corda.ecdh.key.id" to ecdhKeyId,
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
    }

    @Suppress("LongParameterList")
    fun getMember(
        preAuthToken: String?,
        roles: Set<MemberRole>?,
        customProperties: Map<String, String>?,
        p2pGatewayUrls: List<String>,
        sessionKeyId: String,
        ledgerKeyId: String,
    ): Map<String, Any?> {
        val endpoints = mutableMapOf<String, String>()

        p2pGatewayUrls.mapIndexed { index, url ->
            endpoints[MemberInfoExtension.URL_KEY.format(index)] = url
            endpoints[MemberInfoExtension.PROTOCOL_VERSION.format(index)] = "1"
        }

        val context = mapOf(
            MemberInfoExtension.PARTY_SESSION_KEYS_ID.format(0) to sessionKeyId,
            MemberInfoExtension.SESSION_KEYS_SIGNATURE_SPEC.format(0) to "SHA256withECDSA",
            MemberInfoExtension.LEDGER_KEYS_ID.format(0) to ledgerKeyId,
            MemberInfoExtension.LEDGER_KEY_SIGNATURE_SPEC.format(0) to "SHA256withECDSA",
        )

        val preAuth = preAuthToken?.let { mapOf("corda.auth.token" to it) } ?: emptyMap()
        val roleProperty: Map<String, String> = roles?.mapIndexed { index: Int, memberRole: MemberRole ->
            "${MemberInfoExtension.ROLES_PREFIX}.$index" to memberRole.value
        }?.toMap() ?: emptyMap()

        val extProperties = customProperties?.filterKeys { it.startsWith("${MemberInfoExtension.CUSTOM_KEY_PREFIX}.") } ?: emptyMap()

        return context + preAuth + roleProperty + extProperties + endpoints
    }

    @Suppress("LongParameterList")
    fun getNotary(
        preAuthToken: String?,
        roles: Set<MemberRole>?,
        customProperties: Map<String, String>?,
        p2pGatewayUrls: List<String>,
        sessionKeyId: String,
        notaryKeyId: String,
    ): Map<String, Any?> {
        require(!(roles.isNullOrEmpty() && !(roles!!.contains(MemberRole.NOTARY)))) { "Must specify the role as notary" }

        val endpoints = mutableMapOf<String, String>()

        p2pGatewayUrls.mapIndexed { index, url ->
            endpoints[MemberInfoExtension.URL_KEY.format(index)] = url
            endpoints[MemberInfoExtension.PROTOCOL_VERSION.format(index)] = "1"
        }

        val notaryServiceName = customProperties?.get(MemberInfoExtension.NOTARY_SERVICE_NAME)
            ?: throw IllegalArgumentException(
                "When specifying a NOTARY role, " +
                    "you also need to specify a custom property for its name under ${MemberInfoExtension.NOTARY_SERVICE_NAME}.",
            )
        val isBackchainRequired = customProperties[MemberInfoExtension.NOTARY_SERVICE_BACKCHAIN_REQUIRED] ?: true
        val notaryProtocol = customProperties[MemberInfoExtension.NOTARY_SERVICE_PROTOCOL] ?: "com.r3.corda.notary.plugin.nonvalidating"

        val context = mapOf(
            MemberInfoExtension.PARTY_SESSION_KEYS_ID.format(0) to sessionKeyId,
            MemberInfoExtension.SESSION_KEYS_SIGNATURE_SPEC.format(0) to "SHA256withECDSA",
            MemberInfoExtension.NOTARY_SERVICE_NAME to notaryServiceName,
            MemberInfoExtension.NOTARY_SERVICE_BACKCHAIN_REQUIRED to "$isBackchainRequired",
            MemberInfoExtension.NOTARY_SERVICE_PROTOCOL to notaryProtocol,
            MemberInfoExtension.NOTARY_SERVICE_PROTOCOL_VERSIONS.format("0") to "1",
            MemberInfoExtension.NOTARY_KEYS_ID.format("0") to notaryKeyId,
            MemberInfoExtension.NOTARY_KEY_SPEC.format("0") to "SHA256withECDSA",
        )

        val preAuth = preAuthToken?.let { mapOf("corda.auth.token" to it) } ?: emptyMap()

        val roleProperty: Map<String, String> = roles!!.mapIndexed { index: Int, memberRole: MemberRole ->
            "${MemberInfoExtension.ROLES_PREFIX}.$index" to memberRole.value
        }.toMap()

        val extProperties = customProperties.filterKeys { it.startsWith("${MemberInfoExtension.CUSTOM_KEY_PREFIX}.") }

        return context + preAuth + roleProperty + extProperties + endpoints
    }
}

enum class MemberRole(val value: String) {
    NOTARY(MemberInfoExtension.NOTARY_ROLE),
}
