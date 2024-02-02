@file:Suppress("TooManyFunctions")

package net.corda.e2etest.utilities

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.crypto.test.certificates.generation.toPem
import net.corda.e2etest.utilities.types.NetworkOnboardingMetadata
import net.corda.rest.ResponseCode
import net.corda.utilities.minutes
import net.corda.utilities.seconds
import java.io.File
import java.net.URLEncoder.encode
import java.nio.charset.Charset.defaultCharset
import java.time.Duration
import net.corda.rest.annotations.RestApiVersion
import java.nio.charset.StandardCharsets
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

/**
 * Calls the necessary endpoints to create a vnode, and onboard the MGM to that vnode.
 */
fun ClusterInfo.onboardMgm(
    mgmName: String = "O=Mgm, L=London, C=GB, OU=$testRunUniqueId",
    groupPolicyConfig: GroupPolicyConfig = GroupPolicyConfig()
): NetworkOnboardingMetadata {
    val mgmCpiName = "mgm.cpi"
    conditionallyUploadCpiSigningCertificate()
    conditionallyUploadCordaPackage(mgmCpiName, null, getMgmGroupPolicy())
    val mgmHoldingId = getOrCreateVirtualNodeFor(mgmName, mgmCpiName)

    addSoftHsmFor(mgmHoldingId, CAT_SESSION_INIT)
    val sessionKeyId = createKeyFor(
        mgmHoldingId, "$mgmHoldingId$CAT_SESSION_INIT", CAT_SESSION_INIT, DEFAULT_KEY_SCHEME
    )
    var mgmSessionCert: String? = null
    val mgmSessionCertAlias = "$CERT_ALIAS_SESSION-$mgmHoldingId"
    if (groupPolicyConfig.sessionPkiMode == "Standard") {
        val mgmSessionCsr = generateCsr(mgmName, sessionKeyId, mgmHoldingId)
        mgmSessionCert = getCa().generateCert(mgmSessionCsr)
        val mgmSessionCertFile = File.createTempFile("${this.hashCode()}$CAT_SESSION_INIT", ".pem").also {
            it.deleteOnExit()
            it.writeBytes(mgmSessionCert.toByteArray())
        }
        importCertificate(mgmSessionCertFile, CERT_USAGE_SESSION, mgmSessionCertAlias, mgmHoldingId)
    }

    addSoftHsmFor(mgmHoldingId, CAT_PRE_AUTH)
    val ecdhKeyId = createKeyFor(
        mgmHoldingId, "$mgmHoldingId$CAT_PRE_AUTH", CAT_PRE_AUTH, DEFAULT_KEY_SCHEME
    )

    val registrationContext = createMgmRegistrationContext(
        getCa().caCertificate.toPem(),
        sessionKeyId,
        ecdhKeyId,
        groupPolicyConfig
    )

    whenNoKeyExists(TENANT_P2P, alias = "$TENANT_P2P$CAT_TLS", category = CAT_TLS) {
        disableCertificateRevocationChecks()
        val tlsKeyId = createKeyFor(TENANT_P2P, "$TENANT_P2P$CAT_TLS", CAT_TLS, DEFAULT_KEY_SCHEME)
        val mgmTlsCsr = generateCsr(mgmName, tlsKeyId)
        val mgmTlsCert = File.createTempFile("${this.hashCode()}$CAT_TLS", ".pem").also {
            it.deleteOnExit()
            it.writeBytes(getCa().generateCert(mgmTlsCsr).toByteArray())
        }
        importCertificate(mgmTlsCert, CERT_USAGE_P2P, CERT_ALIAS_P2P)
    }
    val registrationId = register(mgmHoldingId, registrationContext, waitForApproval = true)
    if (mgmSessionCert != null) {
        configureNetworkParticipant(mgmHoldingId, sessionKeyId, mgmSessionCertAlias)
    } else {
        configureNetworkParticipant(mgmHoldingId, sessionKeyId)
    }

    return NetworkOnboardingMetadata(mgmHoldingId, mgmName, registrationId, registrationContext, this)
}

/**
 * Returns the MGM's group policy file.
 */
fun ClusterInfo.exportGroupPolicy(
    mgmHoldingId: String
) = cluster {
    assertWithRetryIgnoringExceptions {
        interval(2.seconds)
        timeout(30.seconds)
        command { get("/api/$REST_API_VERSION_PATH/mgm/$mgmHoldingId/info") }
        condition { it.code == ResponseCode.OK.statusCode }
    }.body
}

@Suppress("unused")
/**
 * Attempt to create a standard approval rule.
 */
fun ClusterInfo.createApprovalRule(
    mgmHoldingId: String,
    regex: String,
    label: String
) = createApprovalRuleCommon({ "/api/$REST_API_VERSION_PATH/mgm/$mgmHoldingId/approval/rules" }, regex, label)

@Suppress("unused")
/**
 * Attempt to create a pre-auth approval rule.
 */
fun ClusterInfo.createPreAuthApprovalRule(
    mgmHoldingId: String,
    regex: String,
    label: String
) = createApprovalRuleCommon({ "/api/$REST_API_VERSION_PATH/mgm/$mgmHoldingId/approval/rules/preauth" }, regex, label)

/**
 * Attempt to create an approval rule at a given resource URL.
 */
private fun ClusterInfo.createApprovalRuleCommon(
    urlFn: ClusterBuilder.() -> String,
    regex: String,
    label: String
) = cluster {
    val payload = mapOf(
        "ruleLabel" to label,
        "ruleRegex" to regex
    )

    assertWithRetry {
        interval(1.seconds)
        command { post(urlFn(), ObjectMapper().writeValueAsString(payload)) }
        condition { it.code == ResponseCode.OK.statusCode }
    }.toJson()["ruleId"].textValue()
}

@Suppress("unused")
/**
 * Attempt to delete a standard approval rule.
 */
fun ClusterInfo.deleteApprovalRule(
    mgmHoldingId: String,
    ruleId: String
) = delete { "/api/$REST_API_VERSION_PATH/mgm/$mgmHoldingId/approval/rules/$ruleId" }

@Suppress("unused")
/**
 * Attempt to delete a pre-auth approval rule.
 */
fun ClusterInfo.deletePreAuthApprovalRule(
    mgmHoldingId: String,
    ruleId: String
) = delete {"/api/$REST_API_VERSION_PATH/mgm/$mgmHoldingId/approval/rules/preauth/$ruleId" }

/**
 * Attempt to delete a resource at a given URL with retries.
 */
private fun ClusterInfo.delete(
    urlFn: ClusterBuilder.() -> String
) = cluster {
    assertWithRetry {
        interval(1.seconds)
        command { delete(urlFn()) }
        condition { it.code == ResponseCode.NO_CONTENT.statusCode }
    }
}

@Suppress("unused")
/**
 * Attempt to create a pre-auth token.
 */
fun ClusterInfo.createPreAuthToken(
    mgmHoldingId: String,
    ownerX500Name: String,
    remark: String? = "Token created for automated test run $testRunUniqueId with default remark.",
    ttl: Duration? = null
) = cluster {
    val payload = mutableMapOf(
        "ownerX500Name" to ownerX500Name
    ).apply {
        remark?.let { put("remarks", it) }
        ttl?.let { put("ttl", it.toString()) }
    }

    assertWithRetryIgnoringExceptions {
        interval(1.seconds)
        command {
            post(
                "/api/$REST_API_VERSION_PATH/mgm/$mgmHoldingId/preauthtoken",
                ObjectMapper().writeValueAsString(payload)
            )
        }
        condition { it.code == ResponseCode.OK.statusCode }
    }.toJson()["id"].textValue()
}

@Suppress("unused")
/**
 * Attempt to revoke a pre-auth token.
 */
fun ClusterInfo.revokePreAuthToken(
    mgmHoldingId: String,
    tokenId: String,
    remark: String = "Token revoked for automated test run $testRunUniqueId with default remark."
) {
    cluster {
        assertWithRetry {
            interval(1.seconds)
            command {
                put(
                "/api/$REST_API_VERSION_PATH/mgm/$mgmHoldingId/preauthtoken/revoke/$tokenId",
                "{\"remarks\": \"$remark\"}"
                )
            }
            condition { it.code == ResponseCode.OK.statusCode }
        }
    }
}

@Suppress("unused")
/**
 * Get the visible pre-auth tokens for an MGM and return the whole response payload.
 */
fun ClusterInfo.getPreAuthTokens(
    mgmHoldingId: String,
    tokenId: String? = null,
    ownerX500name: String? = null,
    viewInactive: Boolean = false
) = cluster {
    val queries = mutableListOf(
        "viewinactive=$viewInactive"
    ).apply {
        tokenId?.let { add("preauthtokenid=$it") }
        ownerX500name?.let { add("ownerx500name=${encode(it, defaultCharset())}") }
    }
    val query = queries.joinToString(prefix = "?", separator = "&")
    assertWithRetryIgnoringExceptions {
        interval(1.seconds)
        command { get("/api/$REST_API_VERSION_PATH/mgm/$mgmHoldingId/preauthtoken$query") }
        condition { it.code == ResponseCode.OK.statusCode }
    }.toJson()
}

@Suppress("unused")
/**
 * Wait for a pending approval registration to be visible to the MGM in the list of registrations paused waiting for
 * review.
 */
fun ClusterInfo.waitForPendingRegistrationReviews(
    mgmHoldingId: String,
    memberX500Name: String? = null,
    registrationId: String?
) {
    cluster {
        val query = memberX500Name?.let {
            "?requestsubjectx500name=${encode(memberX500Name.toString(), defaultCharset())}"
        } ?: ""
        assertWithRetryIgnoringExceptions {
            timeout(2.minutes)
            interval(3.seconds)
            command { get("/api/$REST_API_VERSION_PATH/mgm/$mgmHoldingId/registrations$query") }
            condition {
                val json = it.toJson().firstOrNull()
                it.code == ResponseCode.OK.statusCode
                        && json?.get("registrationStatus")?.textValue() == REGISTRATION_PENDING_APPROVAL
                        && (registrationId == null || json.get("registrationId")?.textValue() == registrationId)
            }
        }
    }
}

@Suppress("unused")
/**
 * Attempt to approve a registration.
 */
fun ClusterInfo.approveRegistration(
    mgmHoldingId: String,
    registrationId: String
) {
    cluster {
        assertWithRetry {
            interval(1.seconds)
            command { post("/api/$REST_API_VERSION_PATH/mgm/$mgmHoldingId/approve/$registrationId", "") }
            condition { it.code == ResponseCode.NO_CONTENT.statusCode }
        }
    }
}

@Suppress("unused")
/**
 * Attempt to decline a registration.
 */
fun ClusterInfo.declineRegistration(
    mgmHoldingId: String,
    registrationId: String
) {
    cluster {
        assertWithRetry {
            interval(1.seconds)
            command { post(
                "/api/$REST_API_VERSION_PATH/mgm/$mgmHoldingId/decline/$registrationId",
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
    val p2pMode: String = "Authenticated_Encryption",
    val sessionPolicy: String = "Distinct",
    val tlsPkiMode: String = "Standard",
    val tlsVersion: String = "1.3"
)

/**
 * Create a default registration context for registering the MGM
 */
private fun ClusterInfo.createMgmRegistrationContext(
    caTrustRoot: String,
    sessionKeyId: String,
    ecdhKeyId: String,
    groupPolicyConfig: GroupPolicyConfig
) = mapOf(
    "corda.session.keys.0.id" to sessionKeyId,
    "corda.ecdh.key.id" to ecdhKeyId,
    "corda.group.protocol.registration"
            to "net.corda.membership.impl.registration.dynamic.member.DynamicMemberRegistrationService",
    "corda.group.protocol.synchronisation"
            to "net.corda.membership.impl.synchronisation.MemberSynchronisationServiceImpl",
    "corda.group.protocol.p2p.mode" to groupPolicyConfig.p2pMode,
    "corda.group.key.session.policy" to groupPolicyConfig.sessionPolicy,
    "corda.group.tls.type" to TlsType.type.groupPolicyMame,
    "corda.group.pki.session" to groupPolicyConfig.sessionPkiMode,
    "corda.group.pki.tls" to groupPolicyConfig.tlsPkiMode,
    "corda.group.tls.version" to groupPolicyConfig.tlsVersion,
    "corda.endpoints.0.connectionURL" to p2p.uri.toString(),
    "corda.endpoints.0.protocolVersion" to p2p.protocol,
    "corda.group.trustroot.tls.0" to caTrustRoot,
    "corda.group.trustroot.session.0" to caTrustRoot,
)

@Suppress("unused")
/**
 * Suspend a member identified by [x500Name].
 * Suspension is performed by the MGM identified by [mgmHoldingId].
 */
fun ClusterInfo.suspendMember(
    mgmHoldingId: String,
    x500Name: String,
    serialNumber: Int,
) = cluster {
    assertWithRetry {
        timeout(15.seconds)
        interval(1.seconds)
        command {
            post(
                "/api/$REST_API_VERSION_PATH/mgm/$mgmHoldingId/suspend",
                "{ \"x500Name\": \"$x500Name\", \"serialNumber\": $serialNumber }"
            )
        }
        condition { it.code == ResponseCode.NO_CONTENT.statusCode || it.code == ResponseCode.CONFLICT.statusCode }
    }
}

@Suppress("unused")
/**
 * Suspend a member identified by [x500Name].
 * Suspension is performed by the MGM identified by [mgmHoldingId].
 *
 * Used to test RestApiVersion.C5_0, this version allows the serial number to be Null.
 */
fun ClusterInfo.deprecatedSuspendMember(
    mgmHoldingId: String,
    x500Name: String,
    serialNumber: Int? = null,
) = cluster {
    assertWithRetry {
        timeout(15.seconds)
        interval(1.seconds)
        command {
            post(
                "/api/${RestApiVersion.C5_0.versionPath}/mgm/$mgmHoldingId/suspend",
                "{ \"x500Name\": \"$x500Name\", \"serialNumber\": $serialNumber }"
            )
        }
        condition { it.code == ResponseCode.NO_CONTENT.statusCode || it.code == ResponseCode.CONFLICT.statusCode }
    }
}

@Suppress("unused")
/**
 * Activate a member identified by [x500Name].
 * Activation is performed by the MGM identified by [mgmHoldingId].
 */
fun ClusterInfo.activateMember(
    mgmHoldingId: String,
    x500Name: String,
    serialNumber: Int,
) = cluster {
    assertWithRetry {
        timeout(15.seconds)
        interval(1.seconds)
        command {
            post(
                "/api/$REST_API_VERSION_PATH/mgm/$mgmHoldingId/activate",
                "{ \"x500Name\": \"$x500Name\", \"serialNumber\": $serialNumber }"
            )
        }
        condition { it.code == ResponseCode.NO_CONTENT.statusCode || it.code == ResponseCode.CONFLICT.statusCode }
    }
}

@Suppress("unused")
/**
 * Activate a member identified by [x500Name].
 * Activation is performed by the MGM identified by [mgmHoldingId].
 *
 * Used to test RestApiVersion.C5_0, this version allows the serial number to be Null.
 */
fun ClusterInfo.deprecatedActivateMember(
    mgmHoldingId: String,
    x500Name: String,
    serialNumber: Int? = null,
) = cluster {
    assertWithRetry {
        timeout(15.seconds)
        interval(1.seconds)
        command {
            post(
                "/api/${RestApiVersion.C5_0.versionPath}/mgm/$mgmHoldingId/activate",
                "{ \"x500Name\": \"$x500Name\", \"serialNumber\": $serialNumber }"
            )
        }
        condition { it.code == ResponseCode.NO_CONTENT.statusCode || it.code == ResponseCode.CONFLICT.statusCode }
    }
}

@Suppress("unused")
/**
 * Update the group parameters as the MGM identified by [mgmHoldingId], and return the newly updated group parameters.
 */
fun ClusterInfo.updateGroupParameters(
    mgmHoldingId: String,
    update: Map<String, String>
) = cluster {
    val payload = mapOf(
        "parameters" to update
    )
    assertWithRetryIgnoringExceptions {
        timeout(15.seconds)
        interval(1.seconds)
        command {
            post(
                "/api/$REST_API_VERSION_PATH/mgm/$mgmHoldingId/group-parameters",
                ObjectMapper().writeValueAsString(payload)
            )
        }
        condition { it.code == ResponseCode.OK.statusCode }
    }.toJson()
}

fun ClusterInfo.allowClientCertificates(certificatePem: String, mgmHoldingId: String) {
    val subject = CertificateFactory.getInstance("X.509")
        .generateCertificates(certificatePem.byteInputStream())
        .filterIsInstance<X509Certificate>()
        .first()
        .subjectX500Principal

    val encodedSubject = encode(subject.toString(), StandardCharsets.UTF_8)
    cluster {
        val endpoint = "/api/$REST_API_VERSION_PATH/mgm/$mgmHoldingId/mutual-tls/allowed-client-certificate-subjects/$encodedSubject"
        assertWithRetryIgnoringExceptions {
            timeout(15.seconds)
            interval(1.seconds)
            command {
                put(endpoint,"")
            }
            condition { it.code == ResponseCode.NO_CONTENT.statusCode }
        }
    }
}