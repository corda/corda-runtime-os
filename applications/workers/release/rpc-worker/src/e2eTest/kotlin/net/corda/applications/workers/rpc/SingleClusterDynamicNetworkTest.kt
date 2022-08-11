package net.corda.applications.workers.rpc

import net.corda.applications.workers.rpc.http.TestToolkitProperty
import net.corda.applications.workers.rpc.utils.ClusterTestData
import net.corda.applications.workers.rpc.utils.HSM_CAT_LEDGER
import net.corda.applications.workers.rpc.utils.HSM_CAT_SESSION
import net.corda.applications.workers.rpc.utils.HSM_CAT_TLS
import net.corda.applications.workers.rpc.utils.MemberTestData
import net.corda.applications.workers.rpc.utils.P2P_GATEWAY
import net.corda.applications.workers.rpc.utils.P2P_TENANT_ID
import net.corda.applications.workers.rpc.utils.RPC_PORT
import net.corda.applications.workers.rpc.utils.RPC_WORKER
import net.corda.applications.workers.rpc.utils.SINGLE_CLUSTER_NS
import net.corda.applications.workers.rpc.utils.assertMemberInMemberList
import net.corda.applications.workers.rpc.utils.assertOnlyMgmIsInMemberList
import net.corda.applications.workers.rpc.utils.assignSoftHsm
import net.corda.applications.workers.rpc.utils.createMGMGroupPolicyJson
import net.corda.applications.workers.rpc.utils.createVirtualNode
import net.corda.applications.workers.rpc.utils.genGroupPolicy
import net.corda.applications.workers.rpc.utils.genKeyPair
import net.corda.applications.workers.rpc.utils.generateCert
import net.corda.applications.workers.rpc.utils.generateCsr
import net.corda.applications.workers.rpc.utils.getCa
import net.corda.applications.workers.rpc.utils.getMemberRegistrationContext
import net.corda.applications.workers.rpc.utils.getMgmRegistrationContext
import net.corda.applications.workers.rpc.utils.keyExists
import net.corda.applications.workers.rpc.utils.lookupMembers
import net.corda.applications.workers.rpc.utils.name
import net.corda.applications.workers.rpc.utils.register
import net.corda.applications.workers.rpc.utils.setUpNetworkIdentity
import net.corda.applications.workers.rpc.utils.status
import net.corda.applications.workers.rpc.utils.toByteArray
import net.corda.applications.workers.rpc.utils.uploadCpi
import net.corda.applications.workers.rpc.utils.uploadTlsCertificate
import net.corda.crypto.test.certificates.generation.toPem
import net.corda.test.util.eventually
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class SingleClusterDynamicNetworkTest {
    companion object {
        // If running deployment in eks, ensure this is set to true to use correct endpoint information.
        // This should be false by default for automated builds.
        private const val IS_REMOTE_CLUSTER = false
    }

    private val remoteRpcHost = "$RPC_WORKER.$SINGLE_CLUSTER_NS"
    private val remoteP2pHost = "$P2P_GATEWAY.$SINGLE_CLUSTER_NS"
    private val remoteTestToolkit by TestToolkitProperty(remoteRpcHost, RPC_PORT)
    private val localTestToolkit by TestToolkitProperty()

    private val p2pHost = if (IS_REMOTE_CLUSTER) remoteP2pHost else "https://localhost:8080"
    private val testToolkit = if (IS_REMOTE_CLUSTER) remoteTestToolkit else localTestToolkit

    private val mgm = MemberTestData(
        "O=Mgm, L=London, C=GB, OU=${testToolkit.uniqueName}"
    )

    private val cordaCluster = ClusterTestData(
        testToolkit,
        p2pHost,
        listOf(
            MemberTestData("O=Alice, L=London, C=GB, OU=${testToolkit.uniqueName}"),
            MemberTestData("O=Bob, L=London, C=GB, OU=${testToolkit.uniqueName}"),
            MemberTestData("O=Charlie, L=London, C=GB, OU=${testToolkit.uniqueName}")
        )
    )

    private val ca = getCa()

    @Test
    fun `Create mgm and allow members to join the group`() {
        val holdingIds = mutableMapOf<String, String>()
        val cpiChecksum = cordaCluster.uploadCpi(createMGMGroupPolicyJson(), true)
        val mgmHoldingId = cordaCluster.createVirtualNode(mgm, cpiChecksum)
        holdingIds[mgm.name] = mgmHoldingId
        println("MGM HoldingIdentity: $mgmHoldingId")

        cordaCluster.assignSoftHsm(mgmHoldingId, HSM_CAT_SESSION)

        val mgmSessionKeyId = cordaCluster.genKeyPair(mgmHoldingId, HSM_CAT_SESSION)

        cordaCluster.register(
            mgmHoldingId,
            getMgmRegistrationContext(
                tlsTrustRoot = ca.caCertificate.toPem(),
                sessionKeyId = mgmSessionKeyId,
                p2pUrl = cordaCluster.p2pUrl
            )
        )
        cordaCluster.assertOnlyMgmIsInMemberList(mgmHoldingId, mgm.name)

        if (!cordaCluster.keyExists(P2P_TENANT_ID, HSM_CAT_TLS)) {
            val mgmTlsKeyId = cordaCluster.genKeyPair(P2P_TENANT_ID, HSM_CAT_TLS)
            val mgmTlsCsr = cordaCluster.generateCsr(mgm, mgmTlsKeyId)
            val mgmTlsCert = ca.generateCert(mgmTlsCsr)
            cordaCluster.uploadTlsCertificate(mgmTlsCert)
        }

        cordaCluster.setUpNetworkIdentity(
            mgmHoldingId,
            mgmSessionKeyId
        )

        val memberGroupPolicy = cordaCluster.genGroupPolicy(mgmHoldingId)

        val memberCpiChecksum = cordaCluster.uploadCpi(toByteArray(memberGroupPolicy))
        cordaCluster.members.forEach { member ->
            val memberHoldingId = cordaCluster.createVirtualNode(member, memberCpiChecksum)
            holdingIds[member.name] = memberHoldingId
            println("${member.name} holding ID: $memberHoldingId")

            cordaCluster.assignSoftHsm(memberHoldingId, HSM_CAT_SESSION)
            cordaCluster.assignSoftHsm(memberHoldingId, HSM_CAT_LEDGER)

            val memberSessionKeyId = cordaCluster.genKeyPair(memberHoldingId, HSM_CAT_SESSION)
            val memberLedgerKeyId = cordaCluster.genKeyPair(memberHoldingId, HSM_CAT_LEDGER)

            if (!cordaCluster.keyExists(P2P_TENANT_ID, HSM_CAT_TLS)) {
                val memberTlsKeyId = cordaCluster.genKeyPair(P2P_TENANT_ID, HSM_CAT_TLS)
                val memberTlsCsr = cordaCluster.generateCsr(member, memberTlsKeyId)
                val memberTlsCert = ca.generateCert(memberTlsCsr)
                cordaCluster.uploadTlsCertificate(memberTlsCert)
            }

            cordaCluster.setUpNetworkIdentity(
                memberHoldingId,
                memberSessionKeyId
            )
            cordaCluster.assertOnlyMgmIsInMemberList(memberHoldingId, mgm.name)

            cordaCluster.register(
                memberHoldingId,
                getMemberRegistrationContext(
                    cordaCluster,
                    memberSessionKeyId,
                    memberLedgerKeyId
                )
            )

            // Check registration complete.
            // Eventually we can use the registration status endpoint.
            // For now just assert we have received our own member data.
            cordaCluster.assertMemberInMemberList(
                memberHoldingId,
                member
            )
        }

        (cordaCluster.members + mgm).forEach {
            val holdingId = holdingIds[it.name]
            assertNotNull(holdingId)
            eventually {
                cordaCluster.lookupMembers(holdingId!!).also { result ->
                    assertThat(result)
                        .hasSize(1 + cordaCluster.members.size)
                        .allSatisfy { memberInfo ->
                            assertThat(memberInfo.status).isEqualTo("ACTIVE")
                        }
                    val expectedList = cordaCluster.members.map { member ->
                        member.name
                    } + mgm.name
                    assertThat(result.map { memberInfo -> memberInfo.name })
                        .hasSize(1 + cordaCluster.members.size)
                        .containsExactlyInAnyOrderElementsOf(expectedList)
                }
            }
        }
    }
}
