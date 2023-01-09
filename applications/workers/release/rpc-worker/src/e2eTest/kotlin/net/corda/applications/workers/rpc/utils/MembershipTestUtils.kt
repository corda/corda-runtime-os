package net.corda.applications.workers.rpc.utils

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.crypto.test.certificates.generation.CertificateAuthority
import net.corda.crypto.test.certificates.generation.toPem
import net.corda.membership.httprpc.v1.MemberLookupRpcOps
import net.corda.membership.httprpc.v1.types.response.RpcMemberInfo
import net.corda.test.util.eventually
import net.corda.v5.base.util.minutes
import net.corda.v5.base.util.seconds
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
    tlsTrustRoot: String,
    sessionKeyId: String,
    ecdhKeyId: String,
    p2pUrl: String,
) = mapOf(
    "corda.session.key.id" to sessionKeyId,
    "corda.ecdh.key.id" to ecdhKeyId,
    "corda.group.protocol.registration"
            to "net.corda.membership.impl.registration.dynamic.member.DynamicMemberRegistrationService",
    "corda.group.protocol.synchronisation"
            to "net.corda.membership.impl.synchronisation.MemberSynchronisationServiceImpl",
    "corda.group.protocol.p2p.mode" to "Authenticated_Encryption",
    "corda.group.key.session.policy" to "Distinct",
    "corda.group.pki.session" to "NoPKI",
    "corda.group.pki.tls" to "Standard",
    "corda.group.tls.version" to "1.3",
    "corda.endpoints.0.connectionURL" to p2pUrl,
    "corda.endpoints.0.protocolVersion" to "1",
    "corda.group.truststore.tls.0" to tlsTrustRoot,
)

fun createMemberRegistrationContext(
    memberE2eCluster: E2eCluster,
    sessionKeyId: String,
    ledgerKeyId: String
) = mapOf(
    "corda.session.key.id" to sessionKeyId,
    "corda.session.key.signature.spec" to SIGNATURE_SPEC,
    "corda.ledger.keys.0.id" to ledgerKeyId,
    "corda.ledger.keys.0.signature.spec" to SIGNATURE_SPEC,
    "corda.endpoints.0.connectionURL" to memberE2eCluster.p2pUrl,
    "corda.endpoints.0.protocolVersion" to "1"
)

val RpcMemberInfo.status get() = mgmContext["corda.status"] ?: fail("Could not find member status")
val RpcMemberInfo.groupId get() = memberContext["corda.groupId"] ?: fail("Could not find member group ID")
val RpcMemberInfo.name get() = memberContext["corda.name"] ?: fail("Could not find member name")

fun E2eCluster.assertOnlyMgmIsInMemberList(
    holdingId: String,
    mgmName: String
) = lookupMembers(holdingId).also { result ->
    assertThat(result)
        .hasSize(1)
        .allSatisfy {
            assertThat(it.status).isEqualTo("ACTIVE")
            assertThat(it.name).isEqualTo(mgmName)
        }
}

fun E2eCluster.getGroupId(
    holdingId: String
): String = eventually {
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
        waitBetween = 3.seconds
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
): List<RpcMemberInfo> {
    return clusterHttpClientFor(MemberLookupRpcOps::class.java)
        .use { client ->
            client.start().proxy.lookup(holdingId).members
        }
}