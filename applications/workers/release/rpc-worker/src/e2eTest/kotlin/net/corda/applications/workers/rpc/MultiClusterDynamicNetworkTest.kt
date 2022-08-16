package net.corda.applications.workers.rpc

import net.corda.applications.workers.rpc.utils.E2eCluster
import net.corda.applications.workers.rpc.utils.E2eClusterAConfig
import net.corda.applications.workers.rpc.utils.E2eClusterBConfig
import net.corda.applications.workers.rpc.utils.E2eClusterCConfig
import net.corda.applications.workers.rpc.utils.E2eClusterFactory
import net.corda.applications.workers.rpc.utils.HSM_CAT_LEDGER
import net.corda.applications.workers.rpc.utils.HSM_CAT_SESSION
import net.corda.applications.workers.rpc.utils.HSM_CAT_TLS
import net.corda.applications.workers.rpc.utils.MemberTestData
import net.corda.applications.workers.rpc.utils.P2P_TENANT_ID
import net.corda.applications.workers.rpc.utils.assertMemberInMemberList
import net.corda.applications.workers.rpc.utils.assertOnlyMgmIsInMemberList
import net.corda.applications.workers.rpc.utils.assignSoftHsm
import net.corda.applications.workers.rpc.utils.createMGMGroupPolicyJson
import net.corda.applications.workers.rpc.utils.createMemberRegistrationContext
import net.corda.applications.workers.rpc.utils.createMgmRegistrationContext
import net.corda.applications.workers.rpc.utils.createVirtualNode
import net.corda.applications.workers.rpc.utils.disableCLRChecks
import net.corda.applications.workers.rpc.utils.generateCert
import net.corda.applications.workers.rpc.utils.generateCsr
import net.corda.applications.workers.rpc.utils.generateGroupPolicy
import net.corda.applications.workers.rpc.utils.generateKeyPair
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
import net.corda.membership.httprpc.v1.types.response.RpcMemberInfo
import net.corda.test.util.eventually
import net.corda.v5.base.util.seconds
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

/**
 * Three clusters are required for running this test. See `resources/RunNetworkTests.md` for more details.
 */
@Disabled(
    "CORE-6036. " +
            "No multi cluster environment is available to run this test against. " +
            "Remove this to run locally or when CORE-6036 is resolved."
)
class MultiClusterDynamicNetworkTest {
    private val aliceCluster = E2eClusterFactory.getE2eCluster(E2eClusterAConfig).also { cluster ->
        cluster.addMembers(
            listOf(MemberTestData("O=Alice, L=London, C=GB, OU=${cluster.testToolkit.uniqueName}"))
        )
    }

    private val bobCluster = E2eClusterFactory.getE2eCluster(E2eClusterBConfig).also { cluster ->
        cluster.addMembers(
            listOf(MemberTestData("O=Bob, L=London, C=GB, OU=${cluster.testToolkit.uniqueName}"))
        )
    }

    private val mgmCluster = E2eClusterFactory.getE2eCluster(E2eClusterCConfig).also { cluster ->
        cluster.addMembers(
            listOf(MemberTestData("O=Mgm, L=London, C=GB, OU=${cluster.testToolkit.uniqueName}"))
        )
    }

    private val memberClusters = listOf(aliceCluster, bobCluster)

    private val ca = getCa()

    @Test
    fun `Create mgm and allow members to join the group`() {
        onboardMultiClusterGroup()
    }

    /**
     * Onboard group and return group ID.
     */
    private fun onboardMultiClusterGroup(): String {
        // For the purposes of this test, the MGM cluster is
        // expected to have only one MGM (in reality there can be more on a cluster).
        assertThat(mgmCluster.members).hasSize(1)

        val holdingIds = mutableMapOf<String, String>()
        val mgm = mgmCluster.members[0]
        mgmCluster.disableCLRChecks()
        val cpiChecksum = mgmCluster.uploadCpi(createMGMGroupPolicyJson(), true)
        val mgmHoldingId = mgmCluster.createVirtualNode(mgm, cpiChecksum)
        holdingIds[mgm.name] = mgmHoldingId

        mgmCluster.assignSoftHsm(mgmHoldingId, HSM_CAT_SESSION)

        val mgmSessionKeyId = mgmCluster.generateKeyPair(mgmHoldingId, HSM_CAT_SESSION)

        mgmCluster.register(
            mgmHoldingId,
            createMgmRegistrationContext(
                tlsTrustRoot = ca.caCertificate.toPem(),
                sessionKeyId = mgmSessionKeyId,
                p2pUrl = mgmCluster.p2pUrl
            )
        )

        mgmCluster.assertOnlyMgmIsInMemberList(mgmHoldingId, mgm.name)

        if (!mgmCluster.keyExists(P2P_TENANT_ID, HSM_CAT_TLS)) {
            val mgmTlsKeyId = mgmCluster.generateKeyPair(P2P_TENANT_ID, HSM_CAT_TLS)
            val mgmTlsCsr = mgmCluster.generateCsr(mgm, mgmTlsKeyId)
            val mgmTlsCert = ca.generateCert(mgmTlsCsr)
            mgmCluster.uploadTlsCertificate(mgmTlsCert)
        }

        mgmCluster.setUpNetworkIdentity(
            mgmHoldingId,
            mgmSessionKeyId
        )

        val memberGroupPolicy = mgmCluster.generateGroupPolicy(mgmHoldingId)

        memberClusters.forEach { cordaCluster ->
            cordaCluster.disableCLRChecks()
            val memberCpiChecksum = cordaCluster.uploadCpi(memberGroupPolicy.toByteArray())

            cordaCluster.members.forEach { member ->
                val memberHoldingId = cordaCluster.createVirtualNode(member, memberCpiChecksum)
                holdingIds[member.name] = memberHoldingId

                cordaCluster.assignSoftHsm(memberHoldingId, HSM_CAT_SESSION)
                cordaCluster.assignSoftHsm(memberHoldingId, HSM_CAT_LEDGER)

                if (!cordaCluster.keyExists(P2P_TENANT_ID, HSM_CAT_TLS)) {
                    val memberTlsKeyId = cordaCluster.generateKeyPair(P2P_TENANT_ID, HSM_CAT_TLS)
                    val memberTlsCsr = cordaCluster.generateCsr(member, memberTlsKeyId)
                    val memberTlsCert = ca.generateCert(memberTlsCsr)
                    cordaCluster.uploadTlsCertificate(memberTlsCert)
                }

                val memberSessionKeyId = cordaCluster.generateKeyPair(memberHoldingId, HSM_CAT_SESSION)
                val memberLedgerKeyId = cordaCluster.generateKeyPair(memberHoldingId, HSM_CAT_LEDGER)

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
        }

        val allMembers = memberClusters.flatMap { it.members } + mgm
        val clusterMembers = mutableMapOf<E2eCluster, MutableList<RpcMemberInfo>>()
        (memberClusters + mgmCluster).forEach { cordaCluster ->
            cordaCluster.members.forEach {
                val holdingId = holdingIds[it.name]
                assertNotNull(holdingId)

                eventually(
                    waitBetween = 3.seconds,
                    duration = 60.seconds
                ) {
                    val memberList = cordaCluster.lookupMembers(holdingId!!)

                    val expectedList = allMembers.map { member -> member.name }

                    assertThat(memberList)
                        .hasSize(allMembers.size)
                        .allSatisfy { memberInfo ->
                            assertThat(memberInfo.status).isEqualTo("ACTIVE")
                        }
                    assertThat(memberList.map { memberInfo -> memberInfo.name })
                        .hasSize(allMembers.size)
                        .containsExactlyInAnyOrderElementsOf(expectedList)

                    memberList
                        .firstOrNull { memberInfo ->
                            memberInfo.name == it.name
                        }?.let { memberInfo ->
                            clusterMembers
                                .computeIfAbsent(cordaCluster) {
                                    mutableListOf()
                                }.add(memberInfo)
                        }

                }
            }
        }
        return mgmCluster.getGroupId(mgmHoldingId)
    }
}
