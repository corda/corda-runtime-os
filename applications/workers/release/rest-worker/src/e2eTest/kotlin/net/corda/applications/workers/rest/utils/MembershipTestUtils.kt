package net.corda.applications.workers.rest.utils

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.crypto.test.certificates.generation.CertificateAuthority
import net.corda.crypto.test.certificates.generation.toPem
import net.corda.membership.rest.v1.MemberLookupRestResource
import net.corda.membership.rest.v1.types.response.RestMemberInfo
import net.corda.test.util.eventually
import net.corda.utilities.minutes
import net.corda.utilities.seconds
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import java.io.ByteArrayOutputStream

const val SIGNATURE_SPEC = "SHA256withECDSA"

fun createMGMGroupPolicyJson(
    fileFormatVersion: Int = 1,
    registrationProtocol: String = "net.corda.membership.impl.registration.dynamic.mgm.MGMRegistrationService",
    syncProtocol: String = "net.corda.membership.impl.synchronisation.MgmSynchronisationServiceImpl"
): ByteArray {
    val groupPolicy = mapOf(
        "fileFormatVersion" to fileFormatVersion,
        "groupId" to "CREATE_ID",
        "registrationProtocol" to registrationProtocol,
        "synchronisationProtocol" to syncProtocol
    )

    return ByteArrayOutputStream().use { outputStream ->
        ObjectMapper().writeValue(outputStream, groupPolicy)
        outputStream.toByteArray()
    }
}

fun createStaticMemberGroupPolicyJson(
    ca: CertificateAuthority,
    groupId: String,
    e2eCluster: E2eCluster,
): ByteArray {
    val groupPolicy = mapOf(
        "fileFormatVersion" to 1,
        "groupId" to groupId,
        "registrationProtocol"
                to "net.corda.membership.impl.registration.staticnetwork.StaticMemberRegistrationService",
        "synchronisationProtocol"
                to "net.corda.membership.impl.sync.staticnetwork.StaticMemberSyncService",
        "protocolParameters" to mapOf(
            "sessionKeyPolicy" to "Combined",
            "staticNetwork" to mapOf(
                "members" to e2eCluster.members.map {
                    mapOf(
                        "name" to it.name,
                        "memberStatus" to "ACTIVE",
                        "endpointUrl-1" to e2eCluster.p2pUrl,
                        "endpointProtocol-1" to 1
                    )
                }
            )
        ),
        "p2pParameters" to mapOf(
            "tlsTrustRoots" to listOf(ca.caCertificate.toPem()),
            "sessionPki" to "NoPKI",
            "tlsPki" to "Standard",
            "tlsVersion" to "1.3",
            "protocolMode" to "Authenticated_Encryption",
            "tlsType" to "OneWay"
        ),
        "cipherSuite" to emptyMap<String, String>()
    )

    return ByteArrayOutputStream().use { outputStream ->
        ObjectMapper().writeValue(outputStream, groupPolicy)
        outputStream.toByteArray()
    }
}

fun createMgmRegistrationContext(
    caTrustRoot: String,
    sessionKeyId: String,
    ecdhKeyId: String,
    p2pUrl: String,
    sessionPkiMode: String = "NoPKI",
    tlsType: String = "OneWay",
) = mapOf(
    "corda.session.key.id" to sessionKeyId,
    "corda.ecdh.key.id" to ecdhKeyId,
    "corda.group.protocol.registration"
            to "net.corda.membership.impl.registration.dynamic.member.DynamicMemberRegistrationService",
    "corda.group.protocol.synchronisation"
            to "net.corda.membership.impl.synchronisation.MemberSynchronisationServiceImpl",
    "corda.group.protocol.p2p.mode" to "Authenticated_Encryption",
    "corda.group.key.session.policy" to "Distinct",
    "corda.group.tls.type" to tlsType,
    "corda.group.pki.session" to sessionPkiMode,
    "corda.group.pki.tls" to "Standard",
    "corda.group.tls.version" to "1.3",
    "corda.endpoints.0.connectionURL" to p2pUrl,
    "corda.endpoints.0.protocolVersion" to "1",
    "corda.group.truststore.tls.0" to caTrustRoot,
    "corda.group.truststore.session.0" to caTrustRoot,
)

fun createMemberRegistrationContext(
    member: E2eClusterMember,
    memberE2eCluster: E2eCluster,
    sessionKeyId: String,
    ledgerKeyId: String,
    notaryKeyId: String? = null
): Map<String, String> = mutableMapOf(
    "corda.session.key.id" to sessionKeyId,
    "corda.session.key.signature.spec" to SIGNATURE_SPEC,
    "corda.ledger.keys.0.id" to ledgerKeyId,
    "corda.ledger.keys.0.signature.spec" to SIGNATURE_SPEC,
    "corda.endpoints.0.connectionURL" to memberE2eCluster.p2pUrl,
    "corda.endpoints.0.protocolVersion" to "1"
).also {
    if(member.isNotary()) {
        assertThat(notaryKeyId)
            .withFailMessage {
                "Tried to create registration context for notary member without providing notary key info."
            }.isNotEmpty
        it["corda.roles.0"] = "notary"
        it["corda.notary.service.name"] = "C=GB,L=London,O=NotaryService, OU=${memberE2eCluster.uniqueName}"
        it["corda.notary.service.plugin"] = "net.corda.notary.NonValidatingNotary"
        it["corda.notary.keys.0.id"] = notaryKeyId!!
        it["corda.notary.keys.0.signature.spec"] = SIGNATURE_SPEC
    }
}

val RestMemberInfo.status get() = mgmContext["corda.status"] ?: fail("Could not find member status")
val RestMemberInfo.groupId get() = memberContext["corda.groupId"] ?: fail("Could not find member group ID")
val RestMemberInfo.name get() = memberContext["corda.name"] ?: fail("Could not find member name")

fun E2eCluster.assertOnlyMgmIsInMemberList(
    holdingId: String,
    mgmName: String
) = eventually(duration = 1.minutes, retryAllExceptions = true) {
    lookupMembers(holdingId).also { result ->
        assertThat(result)
            .hasSize(1)
            .allSatisfy {
                assertThat(it.status).isEqualTo("ACTIVE")
                assertThat(it.name).isEqualTo(mgmName)
            }
    }
}

fun E2eCluster.getGroupId(
    holdingId: String
): String = eventually(retryAllExceptions = true) {
    lookupMembers(holdingId).let { result ->
        assertThat(result)
            .isNotEmpty
            .anySatisfy {
                assertThat(it.status).isEqualTo("ACTIVE")
            }
        result.firstOrNull { it.status == "ACTIVE" }!!.groupId
    }
}

/**
 * Assert that a member represented by a holding ID can find the member represented by [E2eClusterMember] in it's
 * member list.
 */
fun E2eCluster.assertMemberInMemberList(
    holdingId: String,
    member: E2eClusterMember
) {
    eventually(
        duration = 2.minutes,
        waitBetween = 3.seconds,
        retryAllExceptions = true,
    ) {
        assertThat(
            lookupMembers(holdingId).map {
                it.name
            }
        ).contains(member.name)
    }
}

fun E2eCluster.lookupMembers(
    holdingId: String
): List<RestMemberInfo> {
    return clusterHttpClientFor(MemberLookupRestResource::class.java)
        .use { client ->
            client.start().proxy.lookup(holdingId).members
        }
}