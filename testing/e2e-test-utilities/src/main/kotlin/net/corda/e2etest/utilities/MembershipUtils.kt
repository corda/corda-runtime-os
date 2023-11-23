package net.corda.e2etest.utilities

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.e2etest.utilities.types.NetworkOnboardingMetadata
import net.corda.e2etest.utilities.types.jsonToMemberList
import net.corda.rest.ResponseCode
import net.corda.test.util.eventually
import net.corda.utilities.minutes
import net.corda.utilities.seconds
import net.corda.v5.base.types.MemberX500Name
import org.assertj.core.api.Assertions
import java.io.File
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private val mapper = ObjectMapper()

const val MEMBER_STATUS_ACTIVE = "ACTIVE"
const val REGISTRATION_KEY_PRE_AUTH = "corda.auth.token"
const val REGISTRATION_DECLINED = "DECLINED"
const val REGISTRATION_INVALID = "INVALID"
const val REGISTRATION_APPROVED = "APPROVED"
const val REGISTRATION_SUBMITTED = "SUBMITTED"
const val REGISTRATION_SENT_TO_MGM = "SENT_TO_MGM"
const val REGISTRATION_PENDING_APPROVAL = "PENDING_MANUAL_APPROVAL"
const val CAT_SESSION_INIT = "SESSION_INIT"
const val CAT_PRE_AUTH = "PRE_AUTH"
const val CAT_LEDGER = "LEDGER"
const val CAT_TLS = "TLS"
const val CAT_NOTARY = "NOTARY"
const val TENANT_P2P = "p2p"
const val CERT_USAGE_P2P = "p2p-tls"
const val CERT_USAGE_SESSION = "p2p-session"
const val CERT_ALIAS_P2P = "p2p-tls-cert"
const val CERT_ALIAS_SESSION = "p2p-session-cert"
const val DEFAULT_KEY_SCHEME = "CORDA.ECDSA.SECP256R1"
const val DEFAULT_SIGNATURE_SPEC = "SHA256withECDSA"
const val DEFAULT_NOTARY_SERVICE = "O=NotaryService, L=London, C=GB"

/**
 * Onboard a member by uploading a CPI if it doesn't exist, creating a vnode if it doesn't exist, configuring the
 * member's keys, certificates and registration context, and starting registration.
 * By default, this function will wait until the registration is approved, but this can be disabled so that after
 * registration is submitted, the status is not verified.
 *
 * @param cpb The path to the CPB to use when creating the CPI.
 * @param cpiName The name to be used for the CPI.
 * @param groupPolicy The group policy file to be bundled with the CPB in the CPI.
 * @param x500Name The X500 name of the onboarding member.
 * @param waitForApproval Boolean flag to indicate whether the function should wait and assert for approved status.
 *  Defaults to true.
 * @param getAdditionalContext Optional function which can be passed in to add additional properties on top of the
 *  default to the registration context during registration. The function accepts the members holding ID which might be
 *  required if making API calls.
 * @param useLedgerKey whether the member should be onboarded with a ledger key or not.
 */
@Suppress("LongParameterList")
fun ClusterInfo.onboardMember(
    cpb: String?,
    cpiName: String,
    groupPolicy: String,
    x500Name: String,
    waitForApproval: Boolean = true,
    getAdditionalContext: ((holdingId: String) -> Map<String, String>)? = null,
    tlsCertificateUploadedCallback: (String) -> Unit = {},
    useSessionCertificate: Boolean = false,
    useLedgerKey: Boolean = true,
): NetworkOnboardingMetadata {
    conditionallyUploadCpiSigningCertificate()
    conditionallyUploadCordaPackage(cpiName, cpb, groupPolicy)
    val holdingId = getOrCreateVirtualNodeFor(x500Name, cpiName)

    addSoftHsmFor(holdingId, CAT_SESSION_INIT)
    val sessionKeyId = createKeyFor(holdingId, "$holdingId$CAT_SESSION_INIT", CAT_SESSION_INIT, DEFAULT_KEY_SCHEME)
    var memberSessionCert: String? = null
    val mgmSessionCertAlias = "$CERT_ALIAS_SESSION-$holdingId"
    if (useSessionCertificate) {
        val memberSessionCsr = generateCsr(x500Name, sessionKeyId, holdingId)
        memberSessionCert = getCa().generateCert(memberSessionCsr)
        val mgmSessionCertFile = File.createTempFile("${this.hashCode()}$CAT_SESSION_INIT", ".pem").also {
            it.deleteOnExit()
            it.writeBytes(memberSessionCert.toByteArray())
        }
        importCertificate(mgmSessionCertFile, CERT_USAGE_SESSION, mgmSessionCertAlias, holdingId)
    }

    addSoftHsmFor(holdingId, CAT_LEDGER)
    val ledgerKeyId = if (useLedgerKey) {
        createKeyFor(holdingId, "$holdingId$CAT_LEDGER", CAT_LEDGER, DEFAULT_KEY_SCHEME)
    } else {
        null
    }

    if (!keyExists(TENANT_P2P, "$TENANT_P2P$CAT_TLS", CAT_TLS)) {
        disableCertificateRevocationChecks()
        val tlsKeyId = createKeyFor(TENANT_P2P, "$TENANT_P2P$CAT_TLS", CAT_TLS, DEFAULT_KEY_SCHEME)
        val tlsCsr = generateCsr(x500Name, tlsKeyId)
        val tlsCert = getCa().generateCert(tlsCsr)
        val tlsCertFile = File.createTempFile("${this.hashCode()}$CAT_TLS", ".pem").also {
            it.deleteOnExit()
            it.writeBytes(tlsCert.toByteArray())
        }
        importCertificate(tlsCertFile, CERT_USAGE_P2P, CERT_ALIAS_P2P)
        tlsCertificateUploadedCallback(tlsCert)
    }

    val registrationContext = createRegistrationContext(
        sessionKeyId,
        ledgerKeyId
    ) + (getAdditionalContext?.let { it(holdingId) } ?: emptyMap())

    if (memberSessionCert != null) {
        configureNetworkParticipant(holdingId, sessionKeyId, mgmSessionCertAlias)
    } else {
        configureNetworkParticipant(holdingId, sessionKeyId)
    }

    val registrationId = register(holdingId, registrationContext, waitForApproval)

    return NetworkOnboardingMetadata(holdingId, x500Name, registrationId, registrationContext, this)
}

/**
 * Register a member who has registered previously using the [NetworkOnboardingMetadata] from the previous registration
 * for the cluster connection details and for the member identifier.
 */
fun NetworkOnboardingMetadata.reregisterMember(
    contextToMerge: Map<String, String?> = emptyMap(),
    waitForApproval: Boolean = true
): NetworkOnboardingMetadata {
    val newContext = registrationContext.toMutableMap()
    contextToMerge.forEach {
        if (it.value == null) {
            newContext.remove(it.key)
        } else {
            newContext[it.key] = it.value!!
        }
    }
    return copy(
        registrationContext = newContext,
        registrationId = clusterInfo.register(holdingId, newContext, waitForApproval)
    )
}

/**
 * Onboard a member to be a notary. This performs the same logic as when onboarding a standard member, but also creates
 * the additional notary specific context.
 */
@Suppress("LongParameterList")
fun ClusterInfo.onboardNotaryMember(
    resourceName: String,
    cpiName: String,
    groupPolicy: String,
    x500Name: String,
    wait: Boolean = true,
    getAdditionalContext: ((holdingId: String) -> Map<String, String>)? = null,
    tlsCertificateUploadedCallback: (String) -> Unit = {},
    notaryServiceName: String = DEFAULT_NOTARY_SERVICE,
    backchainRequired: Boolean = true
) = onboardMember(
    resourceName,
    cpiName,
    groupPolicy,
    x500Name,
    wait,
    getAdditionalContext = { holdingId ->
        addSoftHsmFor(holdingId, CAT_NOTARY)
        val notaryKeyId = createKeyFor(holdingId, "$holdingId$CAT_NOTARY", CAT_NOTARY, DEFAULT_KEY_SCHEME)

        mapOf(
            "corda.roles.0" to "notary",
            "corda.notary.service.name" to MemberX500Name.parse(notaryServiceName).toString(),
            "corda.notary.service.backchain.required" to "$backchainRequired",
            "corda.notary.service.flow.protocol.name" to "com.r3.corda.notary.plugin.nonvalidating",
            "corda.notary.service.flow.protocol.version.0" to "1",
            "corda.notary.keys.0.id" to notaryKeyId,
            "corda.notary.keys.0.signature.spec" to DEFAULT_SIGNATURE_SPEC
        ) + (getAdditionalContext?.let { it(holdingId) } ?: emptyMap())
    },
    tlsCertificateUploadedCallback = tlsCertificateUploadedCallback,
    useLedgerKey = false
)

/**
 * Configure a member to be a network participant.
 */
fun ClusterInfo.configureNetworkParticipant(
    holdingId: String,
    sessionKeyId: String,
    sessionCertAlias: String? = null
) {
    return cluster {
        assertWithRetryIgnoringExceptions {
            interval(1.seconds)
            command { configureNetworkParticipant(holdingId, sessionKeyId, sessionCertAlias) }
            condition { it.code == ResponseCode.NO_CONTENT.statusCode }
            failMessage("Failed to configure member '$holdingId' as a network participant")
        }
    }
}

/**
 * Start registration for a member.
 * This function can optionally wait for registration to be approved, or else skip that check and return after
 * submitting.
 */
fun ClusterInfo.register(
    holdingIdentityShortHash: String,
    registrationContext: Map<String, String>,
    waitForApproval: Boolean
) = cluster {

    val payload = mapOf(
        "context" to registrationContext
    )

    assertWithRetry {
        interval(3.seconds)
        command { register(holdingIdentityShortHash, mapper.writeValueAsString(payload)) }
        condition {
            it.code == ResponseCode.OK.statusCode
                    && it.toJson().get("registrationStatus")?.textValue() == REGISTRATION_SUBMITTED
        }
        failMessage("Failed to register to the network '$holdingIdentityShortHash'")
    }.toJson().get("registrationId")!!.textValue()
}.also {
    if (waitForApproval) {
        waitForRegistrationStatus(
            holdingIdentityShortHash,
            it,
            registrationStatus = REGISTRATION_APPROVED
        )
    }
}

private val finalRegistrationStates =  setOf(
    "DECLINED",
    "INVALID",
    "FAILED",
    "APPROVED",
)
/**
 * Check a given cluster for a registration visible by the virtual node represented by the holding identity short hash
 * provided which has status matching the provided status.
 * Optionally, this can look for a registration by ID.
 */
fun ClusterInfo.waitForRegistrationStatus(
    holdingIdentityShortHash: String,
    registrationId: String? = null,
    registrationStatus: String
) {
    cluster {
        assertWithRetryIgnoringExceptions {
            // Use a fairly long timeout here to give plenty of time for the other side to respond. Longer
            // term this should be changed to not use the RPC message pattern and have the information available in a
            // cache on the REST worker, but for now this will have to suffice.
            timeout(3.minutes)
            interval(5.seconds)
            command {
                if (registrationId != null) {
                    getRegistrationStatus(holdingIdentityShortHash, registrationId)
                } else {
                    getRegistrationStatus(holdingIdentityShortHash)
                }
            }
            condition {
                if (registrationId != null) {
                    it.toJson().get("registrationStatus")?.textValue() == registrationStatus
                } else {
                    it.toJson().firstOrNull()?.get("registrationStatus")?.textValue() == registrationStatus
                }
            }
            immediateFailCondition {
                val status = if (registrationId != null) {
                    it.toJson().get("registrationStatus")?.textValue()
                } else {
                    it.toJson().firstOrNull()?.get("registrationStatus")?.textValue() == registrationStatus
                }
                (status != registrationStatus) &&
                    (finalRegistrationStates.contains(status))
            }
            failMessage("Registration was not completed for $holdingIdentityShortHash")
        }
    }
}

/**
 * Register a member as part of a static network.
 */
fun registerStaticMember(
    holdingIdentityShortHash: String,
    notaryServiceName: String? = null,
    customMetadata: Map<String, String> = emptyMap(),
) = DEFAULT_CLUSTER.registerStaticMember(holdingIdentityShortHash, notaryServiceName, customMetadata)

val memberRegisterLock = ReentrantLock()
fun ClusterInfo.registerStaticMember(
    holdingIdentityShortHash: String,
    notaryServiceName: String? = null,
    customMetadata: Map<String, String> = emptyMap(),
) {
    cluster {
        memberRegisterLock.withLock {
            assertWithRetry {
                interval(1.seconds)
                timeout(10.seconds)
                command { registerStaticMember(holdingIdentityShortHash, notaryServiceName, customMetadata) }
                condition {
                    it.code == ResponseCode.OK.statusCode
                            && it.toJson()["registrationStatus"].textValue() == REGISTRATION_SUBMITTED
                }
                failMessage("Failed to register the member to the network '$holdingIdentityShortHash'")
            }

            assertWithRetry {
                // Use a fairly long timeout here to give plenty of time for the other side to respond. Longer
                // term this should be changed to not use the RPC message pattern and have the information available in a
                // cache on the REST worker, but for now this will have to suffice.
                timeout(60.seconds)
                interval(2.seconds)
                command { getRegistrationStatus(holdingIdentityShortHash) }
                condition {
                    it.toJson().firstOrNull()?.get("registrationStatus")?.textValue() == REGISTRATION_APPROVED
                }
                failMessage("Registration was not completed for $holdingIdentityShortHash")
            }
        }
    }
}

/**
 * Create the member context for a member's registration.
 */
fun ClusterInfo.createRegistrationContext(
    sessionKeyId: String,
    ledgerKeyId: String?
): Map<String, String> {
    val baseMap = mapOf(
        "corda.session.keys.0.id" to sessionKeyId,
        "corda.session.keys.0.signature.spec" to DEFAULT_SIGNATURE_SPEC,
        "corda.endpoints.0.connectionURL" to p2p.uri.toString(),
        "corda.endpoints.0.protocolVersion" to p2p.protocol
    )

    val ledgerKeysMap = if (ledgerKeyId == null) {
        emptyMap()
    } else {
        mapOf(
            "corda.ledger.keys.0.id" to ledgerKeyId,
            "corda.ledger.keys.0.signature.spec" to DEFAULT_SIGNATURE_SPEC
        )
    }

    return baseMap + ledgerKeysMap
}



/**
 * Look up the current member list as viewed on a specific cluster by a specific holding ID.
 * This can optionally be filtered by member status.
 */
fun ClusterInfo.lookup(
    holdingId: String,
    statuses: List<String> = emptyList()
) = cluster {
    assertWithRetryIgnoringExceptions {
        timeout(15.seconds)
        interval(1.seconds)
        command {
            val additionalQuery = statuses.joinToString(prefix = "?", separator = "&") { "statuses=$it" }
            get("/api/${ClusterBuilder.REST_API_VERSION_PATH}/members/$holdingId$additionalQuery")
        }
        condition { it.code == ResponseCode.OK.statusCode }
    }
}

/**
 * Look up the current group parameters as viewed on a specific cluster by a specific holding ID.
 */
fun ClusterInfo.lookupGroupParameters(
    holdingId: String
) = cluster {
    assertWithRetryIgnoringExceptions {
        timeout(15.seconds)
        interval(1.seconds)
        command {
            get("/api/${ClusterBuilder.REST_API_VERSION_PATH}/members/$holdingId/group-parameters")
        }
        condition { it.code == ResponseCode.OK.statusCode }
    }
}

fun ClusterInfo.containsExactlyInAnyOrderActiveMembers(
    holdingId: String,
    memberNames: List<String>,
) = eventually(
    duration = 90.seconds,
    waitBetween = 2.seconds
) {
    Assertions.assertThat(
        lookup(holdingId, listOf(MEMBER_STATUS_ACTIVE)).jsonToMemberList().map { it.name }
    ).containsExactlyInAnyOrderElementsOf(memberNames)
}