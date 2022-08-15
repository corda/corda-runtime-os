package net.corda.applications.workers.rpc

import net.corda.applications.workers.rpc.http.TestToolkitProperty
import net.corda.applications.workers.rpc.utils.ClusterTestData
import net.corda.applications.workers.rpc.utils.HSM_CAT_LEDGER
import net.corda.applications.workers.rpc.utils.HSM_CAT_SESSION
import net.corda.applications.workers.rpc.utils.HSM_CAT_TLS
import net.corda.applications.workers.rpc.utils.MemberTestData
import net.corda.applications.workers.rpc.utils.P2P_TENANT_ID
import net.corda.applications.workers.rpc.utils.assertMemberInMemberList
import net.corda.applications.workers.rpc.utils.assertOnlyMgmIsInMemberList
import net.corda.applications.workers.rpc.utils.assertP2pConnectivity
import net.corda.applications.workers.rpc.utils.assignSoftHsm
import net.corda.applications.workers.rpc.utils.createMGMGroupPolicyJson
import net.corda.applications.workers.rpc.utils.createMemberRegistrationContext
import net.corda.applications.workers.rpc.utils.createMgmRegistrationContext
import net.corda.applications.workers.rpc.utils.createVirtualNode
import net.corda.applications.workers.rpc.utils.generateGroupPolicy
import net.corda.applications.workers.rpc.utils.generateKeyPair
import net.corda.applications.workers.rpc.utils.generateCert
import net.corda.applications.workers.rpc.utils.generateCsr
import net.corda.applications.workers.rpc.utils.getCa
import net.corda.applications.workers.rpc.utils.getGroupId
import net.corda.applications.workers.rpc.utils.keyExists
import net.corda.applications.workers.rpc.utils.lookupMembers
import net.corda.applications.workers.rpc.utils.name
import net.corda.applications.workers.rpc.utils.register
import net.corda.applications.workers.rpc.utils.setUpNetworkIdentity
import net.corda.applications.workers.rpc.utils.status
import net.corda.applications.workers.rpc.utils.uploadCpi
import net.corda.applications.workers.rpc.utils.uploadTlsCertificate
import net.corda.crypto.test.certificates.generation.toPem
import net.corda.data.identity.HoldingIdentity
import net.corda.test.util.eventually
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

class SingleClusterDynamicNetworkTest {
    private val rpcHost = System.getProperty("e2eClusterARpcHost")
    private val rpcPort = System.getProperty("e2eClusterARpcPort").toInt()
    private val testToolkit by TestToolkitProperty(rpcHost, rpcPort)

    private val p2pHost = System.getProperty("e2eClusterAP2pHost")
    private val p2pPort = System.getProperty("e2eClusterAP2pPort").toInt()

    private val mgm = MemberTestData(
        "O=Mgm, L=London, C=GB, OU=${testToolkit.uniqueName}"
    )

    private val cordaCluster = ClusterTestData(
        testToolkit,
        p2pHost,
        p2pPort,
        listOf(
            MemberTestData("O=Alice, L=London, C=GB, OU=${testToolkit.uniqueName}"),
            MemberTestData("O=Bob, L=London, C=GB, OU=${testToolkit.uniqueName}"),
            MemberTestData("O=Charlie, L=London, C=GB, OU=${testToolkit.uniqueName}")
        )
    )

    private val ca = getCa()

    @Test
    fun `Create mgm and allow members to join the group`() {
        onboardSingleClusterGroup()
    }

    @Disabled("Is disabled and can be run manually until CORE-6079 is complete. At that point this can be " +
        "merged into the above test.")
    @Test
    fun `Onboard group and check p2p connectivity`() {
        val groupId = onboardSingleClusterGroup()

        assertP2pConnectivity(
            HoldingIdentity(
                cordaCluster.members[0].name,
                groupId
            ),
            HoldingIdentity(
                cordaCluster.members[1].name,
                groupId
            ),
            cordaCluster.kafkaTestToolkit
        )
    }

    /**
     * Onboard group and return group ID
     */
    private fun onboardSingleClusterGroup(): String {
        val holdingIds = mutableMapOf<String, String>()
        val cpiChecksum = cordaCluster.uploadCpi(createMGMGroupPolicyJson(), true)
        val mgmHoldingId = cordaCluster.createVirtualNode(mgm, cpiChecksum)
        holdingIds[mgm.name] = mgmHoldingId

        cordaCluster.assignSoftHsm(mgmHoldingId, HSM_CAT_SESSION)

        val mgmSessionKeyId = cordaCluster.generateKeyPair(mgmHoldingId, HSM_CAT_SESSION)

        cordaCluster.register(
            mgmHoldingId,
            createMgmRegistrationContext(
                tlsTrustRoot = ca.caCertificate.toPem(),
                sessionKeyId = mgmSessionKeyId,
                p2pUrl = cordaCluster.p2pUrl
            )
        )
        cordaCluster.assertOnlyMgmIsInMemberList(mgmHoldingId, mgm.name)

        if (!cordaCluster.keyExists(P2P_TENANT_ID, HSM_CAT_TLS)) {
            val mgmTlsKeyId = cordaCluster.generateKeyPair(P2P_TENANT_ID, HSM_CAT_TLS)
            val mgmTlsCsr = cordaCluster.generateCsr(mgm, mgmTlsKeyId)
            val mgmTlsCert = ca.generateCert(mgmTlsCsr)
            cordaCluster.uploadTlsCertificate(mgmTlsCert)
        }

        cordaCluster.setUpNetworkIdentity(
            mgmHoldingId,
            mgmSessionKeyId
        )

        val memberGroupPolicy = cordaCluster.generateGroupPolicy(mgmHoldingId)

        val memberCpiChecksum = cordaCluster.uploadCpi(memberGroupPolicy.toByteArray())
        cordaCluster.members.forEach { member ->
            val memberHoldingId = cordaCluster.createVirtualNode(member, memberCpiChecksum)
            holdingIds[member.name] = memberHoldingId

            cordaCluster.assignSoftHsm(memberHoldingId, HSM_CAT_SESSION)
            cordaCluster.assignSoftHsm(memberHoldingId, HSM_CAT_LEDGER)

            val memberSessionKeyId = cordaCluster.generateKeyPair(memberHoldingId, HSM_CAT_SESSION)
            val memberLedgerKeyId = cordaCluster.generateKeyPair(memberHoldingId, HSM_CAT_LEDGER)

            if (!cordaCluster.keyExists(P2P_TENANT_ID, HSM_CAT_TLS)) {
                val memberTlsKeyId = cordaCluster.generateKeyPair(P2P_TENANT_ID, HSM_CAT_TLS)
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
                createMemberRegistrationContext(
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
        return cordaCluster.getGroupId(mgmHoldingId)
    }
}
