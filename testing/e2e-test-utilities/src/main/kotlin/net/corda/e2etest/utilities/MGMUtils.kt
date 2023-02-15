@file:Suppress("TooManyFunctions")

package net.corda.e2etest.utilities

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.crypto.test.certificates.generation.toPem
import net.corda.httprpc.ResponseCode
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.util.seconds
import java.io.File
import java.net.URLEncoder.encode
import java.nio.charset.Charset.defaultCharset
import java.time.Duration

/**
 * Calls the necessary endpoints to create a vnode, and onboard the MGM to that vnode.
 */
fun onboardMgm(
    clusterConfig: ClusterConfig,
    resourceName: String,
    mgmName: MemberX500Name = MemberX500Name.parse("O=Mgm, L=London, C=GB, OU=$testRunUniqueId")
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
        clusterConfig.p2pUri.toString()
    )

    if (!keyExists(TENANT_P2P, CAT_TLS)) {
        val tlsKeyId = createKeyFor(TENANT_P2P, CERT_ALIAS_P2P, CAT_TLS, DEFAULT_KEY_SCHEME)
        val mgmTlsCsr = generateCsr(clusterConfig, mgmName, tlsKeyId)
        val mgmTlsCert = File.createTempFile("${clusterConfig.hashCode()}$CAT_TLS", ".pem").also {
            it.deleteOnExit()
            it.writeBytes(getCa().generateCert(mgmTlsCsr).toByteArray())
        }
        importCertificate(clusterConfig, mgmTlsCert, CERT_USAGE_P2P, CERT_ALIAS_P2P)
    }
    val registrationId = register(clusterConfig, mgmHoldingId, registrationContext, waitForApproval = true)
    configureNetworkParticipant(clusterConfig, mgmHoldingId, sessionKeyId)

    return NetworkOnboardingMetadata(mgmHoldingId, mgmName, registrationId, registrationContext)
}

/**
 * Returns the MGM's group policy file.
 */
fun exportGroupPolicy(
    clusterConfig: ClusterConfig,
    mgmHoldingId: String
) = cluster(clusterConfig) {
    assertWithRetry {
        timeout(10.seconds)
        command { get("/api/v1/mgm/$mgmHoldingId/info") }
        condition { it.code == ResponseCode.OK.statusCode }
    }.body
}

/**
 * Attempt to create a standard approval rule.
 */
fun createApprovalRule(
    clusterConfig: ClusterConfig,
    mgmHoldingId: String,
    regex: String,
    label: String
) = createApprovalRuleCommon(clusterConfig, "/api/v1/mgm/$mgmHoldingId/approval/rules", regex, label)

/**
 * Attempt to create a pre-auth approval rule.
 */
fun createPreAuthApprovalRule(
    clusterConfig: ClusterConfig,
    mgmHoldingId: String,
    regex: String,
    label: String
) = createApprovalRuleCommon(clusterConfig, "/api/v1/mgm/$mgmHoldingId/approval/rules/preauth", regex, label)

/**
 * Attempt to create an approval rule at a given resource URL.
 */
private fun createApprovalRuleCommon(
    clusterConfig: ClusterConfig,
    url: String,
    regex: String,
    label: String
) = cluster(clusterConfig) {
    val payload = mapOf(
        "ruleLabel" to label,
        "ruleRegex" to regex
    )

    assertWithRetry {
        timeout(10.seconds)
        command { post(url, ObjectMapper().writeValueAsString(payload)) }
        condition { it.code == ResponseCode.OK.statusCode }
    }.toJson()["ruleId"].textValue()
}

/**
 * Attempt to delete a standard approval rule.
 */
fun deleteApprovalRule(
    clusterConfig: ClusterConfig,
    mgmHoldingId: String,
    ruleId: String
) = delete(clusterConfig, "/api/v1/mgm/$mgmHoldingId/approval/rules/$ruleId")

/**
 * Attempt to delete a pre-auth approval rule.
 */
fun deletePreAuthApprovalRule(
    clusterConfig: ClusterConfig,
    mgmHoldingId: String,
    ruleId: String
) = delete(clusterConfig, "/api/v1/mgm/$mgmHoldingId/approval/rules/preauth/$ruleId")

/**
 * Attempt to delete a resource at a given URL with retries.
 */
private fun delete(
    clusterConfig: ClusterConfig,
    url: String
) = cluster(clusterConfig) {
    assertWithRetry {
        timeout(10.seconds)
        command { delete(url) }
        condition { it.code == ResponseCode.NO_CONTENT.statusCode }
    }
}

/**
 * Attempt to create a pre-auth token.
 */
fun createPreAuthToken(
    clusterConfig: ClusterConfig,
    mgmHoldingId: String,
    ownerX500Name: MemberX500Name,
    remark: String? = "Token created for automated test run $testRunUniqueId with default remark.",
    ttl: Duration? = null
) = cluster(clusterConfig) {
    val payload = mutableMapOf(
        "ownerX500Name" to ownerX500Name.toString()
    ).apply {
        remark?.let { put("remarks", it) }
        ttl?.let { put("ttl", it.toString()) }
    }

    assertWithRetry {
        timeout(10.seconds)
        command { post("/api/v1/mgm/$mgmHoldingId/preauthtoken", ObjectMapper().writeValueAsString(payload)) }
        condition { it.code == ResponseCode.OK.statusCode }
    }.toJson()["id"].textValue()
}

/**
 * Attempt to revoke a pre-auth token.
 */
fun revokePreAuthToken(
    clusterConfig: ClusterConfig,
    mgmHoldingId: String,
    tokenId: String,
    remark: String = "Token revoked for automated test run $testRunUniqueId with default remark."
) {
    cluster(clusterConfig) {
        assertWithRetry {
            timeout(10.seconds)
            command { put("/api/v1/mgm/$mgmHoldingId/preauthtoken/revoke/$tokenId", "{\"remarks\": \"$remark\"}") }
            condition { it.code == ResponseCode.OK.statusCode }
        }
    }
}

/**
 * Get the visible pre-auth tokens for an MGM and return the whole response payload.
 */
fun getPreAuthTokens(
    clusterConfig: ClusterConfig,
    mgmHoldingId: String,
    tokenId: String? = null,
    ownerX500name: String? = null,
    viewinactive: Boolean? = null
) = cluster(clusterConfig) {
    val queries = mutableListOf<String>().apply {
        tokenId?.let { add("preauthtokenid=$it") }
        ownerX500name?.let { add("ownerx500name=${encode(it, defaultCharset())}") }
        viewinactive?.let { add("viewinactive=$it") }
    }
    val query = if (queries.isNotEmpty()) {
        queries.joinToString(prefix = "?", separator = "&")
    } else {
        ""
    }
    assertWithRetry {
        timeout(10.seconds)
        command { get("/api/v1/mgm/$mgmHoldingId/preauthtoken$query") }
        condition { it.code == ResponseCode.OK.statusCode }
    }.toJson()
}

/**
 * Wait for a pending approval registration to be visible to the MGM in the list of registrations paused waiting for
 * review.
 */
fun waitForPendingRegistrationReviews(
    clusterConfig: ClusterConfig,
    mgmHoldingId: String,
    memberX500Name: MemberX500Name? = null,
    registrationId: String?
) {
    cluster(clusterConfig) {
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
    clusterConfig: ClusterConfig,
    mgmHoldingId: String,
    registrationId: String
) {
    cluster(clusterConfig) {
        assertWithRetry {
            timeout(10.seconds)
            command { post("/api/v1/mgm/$mgmHoldingId/approve/$registrationId", "") }
            condition { it.code == ResponseCode.OK.statusCode }
        }
    }
}

/**
 * Attempt to decline a registration.
 */
fun declineRegistration(
    clusterConfig: ClusterConfig,
    mgmHoldingId: String,
    registrationId: String
) {
    cluster(clusterConfig) {
        assertWithRetry {
            timeout(10.seconds)
            command { post("/api/v1/mgm/$mgmHoldingId/decline/$registrationId", "") }
            condition { it.code == ResponseCode.OK.statusCode }
        }
    }
}

/**
 * Create a default registration context for registering the MGM
 */
private fun createMgmRegistrationContext(
    caTrustRoot: String,
    sessionKeyId: String,
    ecdhKeyId: String,
    p2pUrl: String,
    sessionPkiMode: String = "NoPKI"
) = mapOf(
    "corda.session.key.id" to sessionKeyId,
    "corda.ecdh.key.id" to ecdhKeyId,
    "corda.group.protocol.registration"
            to "net.corda.membership.impl.registration.dynamic.member.DynamicMemberRegistrationService",
    "corda.group.protocol.synchronisation"
            to "net.corda.membership.impl.synchronisation.MemberSynchronisationServiceImpl",
    "corda.group.protocol.p2p.mode" to "Authenticated_Encryption",
    "corda.group.key.session.policy" to "Distinct",
    "corda.group.tls.type" to "OneWay",
    "corda.group.pki.session" to sessionPkiMode,
    "corda.group.pki.tls" to "Standard",
    "corda.group.tls.version" to "1.3",
    "corda.endpoints.0.connectionURL" to p2pUrl,
    "corda.endpoints.0.protocolVersion" to "1",
    "corda.group.truststore.tls.0" to caTrustRoot,
    "corda.group.truststore.session.0" to caTrustRoot,
)