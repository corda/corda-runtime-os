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
import net.corda.applications.workers.rpc.utils.disableCLRChecks
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
import net.corda.membership.httprpc.v1.types.response.RpcMemberInfo
import net.corda.test.util.eventually
import net.corda.v5.base.util.seconds
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

/**
 * When running this test locally, three clusters will most likely be too much for your laptop.
 * It is preferable to test this using a deployment on EKS.
 *
 * One script to assist with deploying this is:
 * https://gist.github.com/yift-r3/8993f48af8e576ef102c92465dc99e17
 *
 * Or alternatively:
 *
 * declare -a namespaces=("$USER-cluster-a" "$USER-cluster-b" "$USER-cluster-mgm")
 * for namespace in ${namespaces[@]}; do
 *   kubectl delete ns $namespace
 *   kubectl create ns $namespace
 *
 *   helm upgrade --install prereqs -n $namespace \
 *    oci://corda-os-docker.software.r3.com/helm-charts/corda-dev \
 *    --set kafka.replicaCount=1,kafka.zookeeper.replicaCount=1 \
 *    --render-subchart-notes \
 *    --timeout 10m \
 *    --wait
 *
 *   helm upgrade --install corda -n $namespace \
 *    oci://corda-os-docker.software.r3.com/helm-charts/corda \
 *    --version "^0.1.0-beta" \
 *    --values values.yaml --values debug.yaml \
 *    --wait
 * done
 */
@Disabled(
    "CORE-6036. " +
            "No multi cluster environment is available to run this test against. " +
            "Remove this to run locally or when CORE-6036 is resolved."
)
class MultiClusterDynamicNetworkTest {

    private val mgmRpcHost = System.getProperty("e2eClusterCRpcHost")
    private val mgmRpcPort = System.getProperty("e2eClusterCRpcPort").toInt()
    private val mgmP2pHost = System.getProperty("e2eClusterCP2pHost")
    private val mgmP2pPort = System.getProperty("e2eClusterCP2pPort").toInt()
    private val mgmTestToolkit by TestToolkitProperty(mgmRpcHost, mgmRpcPort)

    private val aliceRpcHost = System.getProperty("e2eClusterARpcHost")
    private val aliceRpcPort = System.getProperty("e2eClusterARpcPort").toInt()
    private val aliceP2pHost = System.getProperty("e2eClusterAP2pHost")
    private val aliceP2pPort = System.getProperty("e2eClusterAP2pPort").toInt()
    private val aliceTestToolkit by TestToolkitProperty(aliceRpcHost, aliceRpcPort)

    private val bobRpcHost = System.getProperty("e2eClusterBRpcHost")
    private val bobRpcPort = System.getProperty("e2eClusterBRpcPort").toInt()
    private val bobP2pHost = System.getProperty("e2eClusterBP2pHost")
    private val bobP2pPort = System.getProperty("e2eClusterBP2pPort").toInt()
    private val bobTestToolkit by TestToolkitProperty(bobRpcHost, bobRpcPort)

    private val mgmCluster = ClusterTestData(
        mgmTestToolkit,
        mgmP2pHost,
        mgmP2pPort,
        listOf(MemberTestData("O=Mgm, L=London, C=GB, OU=${mgmTestToolkit.uniqueName}"))
    )

    private val aliceCluster = ClusterTestData(
        aliceTestToolkit,
        aliceP2pHost,
        aliceP2pPort,
        listOf(
            MemberTestData("O=Alice, L=London, C=GB, OU=${aliceTestToolkit.uniqueName}"),
        )
    )
    private val bobCluster = ClusterTestData(
        bobTestToolkit,
        bobP2pHost,
        bobP2pPort,
        listOf(
            MemberTestData("O=Bob, L=London, C=GB, OU=${bobTestToolkit.uniqueName}"),
        )
    )

    private val clusters = listOf(aliceCluster, bobCluster)

    private val ca = getCa()

    @Test
    fun `Create mgm and allow members to join the group`() {
        onboardMultiClusterGroup()
    }

    @Disabled("Is disabled and can be run manually until CORE-6079 is complete. At that point this can be " +
        "merged into the above test.")
    @Test
    fun `Onboard group across clusters and check p2p connectivity`() {
        val groupId = onboardMultiClusterGroup()

        assertP2pConnectivity(
            HoldingIdentity(
                aliceCluster.members.first().name,
                groupId
            ),
            HoldingIdentity(
                bobCluster.members.first().name,
                groupId
            ),
            aliceCluster.kafkaTestToolkit,
            bobCluster.kafkaTestToolkit
        )
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

        clusters.forEach { cordaCluster ->
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

        val allMembers = clusters.flatMap { it.members } + mgm
        val clusterMembers = mutableMapOf<ClusterTestData, MutableList<RpcMemberInfo>>()
        (clusters + mgmCluster).forEach { cordaCluster ->
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
