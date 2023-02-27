@file:Suppress("TooManyFunctions")

package net.corda.e2etest.utilities

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.crypto.test.certificates.generation.toPem
import net.corda.httprpc.ResponseCode
import net.corda.utilities.seconds
import net.corda.v5.base.types.MemberX500Name
import java.io.File
import java.net.URLEncoder.encode
import java.nio.charset.Charset.defaultCharset
import java.time.Duration

/**
 * Calls the necessary endpoints to create a vnode, and onboard the MGM to that vnode.
 */
fun onboardMgm(
    clusterInfo: ClusterInfo,
    resourceName: String,
    mgmName: MemberX500Name = MemberX500Name.parse("O=Mgm, L=London, C=GB, OU=$testRunUniqueId"),
    groupPolicyConfig: GroupPolicyConfig = GroupPolicyConfig()
): NetworkOnboardingMetadata {
    val mgmCpiName = "mgm_$testRunUniqueId.cpi"
    conditionallyUploadCordaPackage(mgmCpiName, resourceName, getMgmGroupPolicy())
    val mgmHoldingId = getOrCreateVirtualNodeFor(mgmName.toString(), mgmCpiName)

    addSoftHsmFor(mgmHoldingId, CAT_SESSION_INIT)
    val sessionKeyId = createKeyFor(
        mgmHoldingId, "$mgmHoldingId$CAT_SESSION_INIT", CAT_SESSION_INIT, DEFAULT_KEY_SCHEME
    )

    addSoftHsmFor(mgmHoldingId, CAT_PRE_AUTH)
    val ecdhKeyId = createKeyFor(
        mgmHoldingId, "$mgmHoldingId$CAT_PRE_AUTH", CAT_PRE_AUTH, DEFAULT_KEY_SCHEME
    )

    val registrationContext = createMgmRegistrationContext(
        getCa().caCertificate.toPem(),
        sessionKeyId,
        ecdhKeyId,
        clusterInfo,
        groupPolicyConfig
    )

    if (!keyExists(TENANT_P2P, "$TENANT_P2P$CAT_TLS", CAT_TLS)) {
        val tlsKeyId = createKeyFor(TENANT_P2P, "$TENANT_P2P$CAT_TLS", CAT_TLS, DEFAULT_KEY_SCHEME)
        val mgmTlsCsr = generateCsr(clusterInfo, mgmName, tlsKeyId)
        val mgmTlsCert = File.createTempFile("${clusterInfo.hashCode()}$CAT_TLS", ".pem").also {
            it.deleteOnExit()
            it.writeBytes(getCa().generateCert(mgmTlsCsr).toByteArray())
        }
        importCertificate(clusterInfo, mgmTlsCert, CERT_USAGE_P2P, CERT_ALIAS_P2P)
    }
    val registrationId = register(clusterInfo, mgmHoldingId, registrationContext, waitForApproval = true)
    configureNetworkParticipant(clusterInfo, mgmHoldingId, sessionKeyId)

    return NetworkOnboardingMetadata(mgmHoldingId, mgmName, registrationId, registrationContext)
}

/**
 * Returns the MGM's group policy file.
 */
fun exportGroupPolicy(
    clusterInfo: ClusterInfo,
    mgmHoldingId: String
) = cluster(clusterInfo) {
    assertWithRetry {
        command { get("/api/v1/mgm/$mgmHoldingId/info") }
        condition { it.code == ResponseCode.OK.statusCode }
    }.body
}

/**
 * Attempt to create a standard approval rule.
 */
fun createApprovalRule(
    clusterInfo: ClusterInfo,
    mgmHoldingId: String,
    regex: String,
    label: String
) = createApprovalRuleCommon(clusterInfo, "/api/v1/mgm/$mgmHoldingId/approval/rules", regex, label)

/**
 * Attempt to create a pre-auth approval rule.
 */
fun createPreAuthApprovalRule(
    clusterInfo: ClusterInfo,
    mgmHoldingId: String,
    regex: String,
    label: String
) = createApprovalRuleCommon(clusterInfo, "/api/v1/mgm/$mgmHoldingId/approval/rules/preauth", regex, label)

/**
 * Attempt to create an approval rule at a given resource URL.
 */
private fun createApprovalRuleCommon(
    clusterInfo: ClusterInfo,
    url: String,
    regex: String,
    label: String
) = cluster(clusterInfo) {
    val payload = mapOf(
        "ruleLabel" to label,
        "ruleRegex" to regex
    )

    assertWithRetry {
        command { post(url, ObjectMapper().writeValueAsString(payload)) }
        condition { it.code == ResponseCode.OK.statusCode }
    }.toJson()["ruleId"].textValue()
}

/**
 * Attempt to delete a standard approval rule.
 */
fun deleteApprovalRule(
    clusterInfo: ClusterInfo,
    mgmHoldingId: String,
    ruleId: String
) = delete(clusterInfo, "/api/v1/mgm/$mgmHoldingId/approval/rules/$ruleId")

/**
 * Attempt to delete a pre-auth approval rule.
 */
fun deletePreAuthApprovalRule(
    clusterInfo: ClusterInfo,
    mgmHoldingId: String,
    ruleId: String
) = delete(clusterInfo, "/api/v1/mgm/$mgmHoldingId/approval/rules/preauth/$ruleId")

/**
 * Attempt to delete a resource at a given URL with retries.
 */
private fun delete(
    clusterInfo: ClusterInfo,
    url: String
) = cluster(clusterInfo) {
    assertWithRetry {
        command { delete(url) }
        condition { it.code == ResponseCode.NO_CONTENT.statusCode }
    }
}

/**
 * Attempt to create a pre-auth token.
 */
fun createPreAuthToken(
    clusterInfo: ClusterInfo,
    mgmHoldingId: String,
    ownerX500Name: MemberX500Name,
    remark: String? = "Token created for automated test run $testRunUniqueId with default remark.",
    ttl: Duration? = null
) = cluster(clusterInfo) {
    val payload = mutableMapOf(
        "ownerX500Name" to ownerX500Name.toString()
    ).apply {
        remark?.let { put("remarks", it) }
        ttl?.let { put("ttl", it.toString()) }
    }

    assertWithRetry {
        command { post("/api/v1/mgm/$mgmHoldingId/preauthtoken", ObjectMapper().writeValueAsString(payload)) }
        condition { it.code == ResponseCode.OK.statusCode }
    }.toJson()["id"].textValue()
}

/**
 * Attempt to revoke a pre-auth token.
 */
fun revokePreAuthToken(
    clusterInfo: ClusterInfo,
    mgmHoldingId: String,
    tokenId: String,
    remark: String = "Token revoked for automated test run $testRunUniqueId with default remark."
) {
    cluster(clusterInfo) {
        assertWithRetry {
            command { put("/api/v1/mgm/$mgmHoldingId/preauthtoken/revoke/$tokenId", "{\"remarks\": \"$remark\"}") }
            condition { it.code == ResponseCode.OK.statusCode }
        }
    }
}

/**
 * Get the visible pre-auth tokens for an MGM and return the whole response payload.
 */
fun getPreAuthTokens(
    clusterInfo: ClusterInfo,
    mgmHoldingId: String,
    tokenId: String? = null,
    ownerX500name: String? = null,
    viewInactive: Boolean = false
) = cluster(clusterInfo) {
    val queries = mutableListOf(
        "viewinactive=$viewInactive"
    ).apply {
        tokenId?.let { add("preauthtokenid=$it") }
        ownerX500name?.let { add("ownerx500name=${encode(it, defaultCharset())}") }
    }
    val query = queries.joinToString(prefix = "?", separator = "&")
    assertWithRetry {
        command { get("/api/v1/mgm/$mgmHoldingId/preauthtoken$query") }
        condition { it.code == ResponseCode.OK.statusCode }
    }.toJson()
}

/**
 * Wait for a pending approval registration to be visible to the MGM in the list of registrations paused waiting for
 * review.
 */
fun waitForPendingRegistrationReviews(
    clusterInfo: ClusterInfo,
    mgmHoldingId: String,
    memberX500Name: MemberX500Name? = null,
    registrationId: String?
) {
    cluster(clusterInfo) {
        val query = memberX500Name?.let {
            "?requestsubjectx500name=${encode(memberX500Name.toString(), defaultCharset())}"
        } ?: ""
        assertWithRetry {
            timeout(60.seconds)
            command { get("/api/v1/mgm/$mgmHoldingId/registrations$query") }
            condition {
                val json = it.toJson().firstOrNull()
                it.code == ResponseCode.OK.statusCode
                        && json?.get("registrationStatus")?.textValue() == REGISTRATION_PENDING_APPROVAL
                        && (registrationId == null || json.get("registrationId")?.textValue() == registrationId)
            }
        }
    }
}

/**
 * Attempt to approve a registration.
 */
fun approveRegistration(
    clusterInfo: ClusterInfo,
    mgmHoldingId: String,
    registrationId: String
) {
    cluster(clusterInfo) {
        assertWithRetry {
            command { post("/api/v1/mgm/$mgmHoldingId/approve/$registrationId", "") }
            condition { it.code == ResponseCode.NO_CONTENT.statusCode }
        }
    }
}

/**
 * Attempt to decline a registration.
 */
fun declineRegistration(
    clusterInfo: ClusterInfo,
    mgmHoldingId: String,
    registrationId: String
) {
    cluster(clusterInfo) {
        assertWithRetry {
            command { post(
                "/api/v1/mgm/$mgmHoldingId/decline/$registrationId",
                "{\"reason\": \"Declined by automated test with runId $testRunUniqueId.\"}")
            }
            condition { it.code == ResponseCode.NO_CONTENT.statusCode }
        }
    }
}

/**
 * Data class for customising the group policy file during MGM registration.
 */
data class GroupPolicyConfig(
    val sessionPkiMode: String = "NoPKI",
    val tlsType: String = "OneWay",
    val p2pMode: String = "Authenticated_Encryption",
    val sessionPolicy: String = "Distinct",
    val tlsPkiMode: String = "Standard",
    val tlsVersion: String = "1.3"
)

/**
 * Create a default registration context for registering the MGM
 */
private fun createMgmRegistrationContext(
    caTrustRoot: String,
    sessionKeyId: String,
    ecdhKeyId: String,
    clusterInfo: ClusterInfo,
    groupPolicyConfig: GroupPolicyConfig
) = mapOf(
    "corda.session.key.id" to sessionKeyId,
    "corda.ecdh.key.id" to ecdhKeyId,
    "corda.group.protocol.registration"
            to "net.corda.membership.impl.registration.dynamic.member.DynamicMemberRegistrationService",
    "corda.group.protocol.synchronisation"
            to "net.corda.membership.impl.synchronisation.MemberSynchronisationServiceImpl",
    "corda.group.protocol.p2p.mode" to groupPolicyConfig.p2pMode,
    "corda.group.key.session.policy" to groupPolicyConfig.sessionPolicy,
    "corda.group.tls.type" to groupPolicyConfig.tlsType,
    "corda.group.pki.session" to groupPolicyConfig.sessionPkiMode,
    "corda.group.pki.tls" to groupPolicyConfig.tlsPkiMode,
    "corda.group.tls.version" to groupPolicyConfig.tlsVersion,
    "corda.endpoints.0.connectionURL" to clusterInfo.p2p.uri.toString(),
    "corda.endpoints.0.protocolVersion" to clusterInfo.p2p.protocol,
    "corda.group.truststore.tls.0" to caTrustRoot,
    "corda.group.truststore.session.0" to caTrustRoot,
)