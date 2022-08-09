package net.corda.applications.workers.rpc

import net.corda.applications.workers.rpc.http.TestToolkitProperty
import net.corda.applications.workers.rpc.utils.ALICE_CLUSTER_NS
import net.corda.applications.workers.rpc.utils.BOB_CLUSTER_NS
import net.corda.applications.workers.rpc.utils.ClusterTestData
import net.corda.applications.workers.rpc.utils.HSM_CAT_LEDGER
import net.corda.applications.workers.rpc.utils.HSM_CAT_SESSION
import net.corda.applications.workers.rpc.utils.HSM_CAT_TLS
import net.corda.applications.workers.rpc.utils.MGM_CLUSTER_NS
import net.corda.applications.workers.rpc.utils.MemberTestData
import net.corda.applications.workers.rpc.utils.P2P_GATEWAY
import net.corda.applications.workers.rpc.utils.P2P_TENANT_ID
import net.corda.applications.workers.rpc.utils.RPC_PORT
import net.corda.applications.workers.rpc.utils.RPC_WORKER
import net.corda.applications.workers.rpc.utils.assertMemberInMemberList
import net.corda.applications.workers.rpc.utils.assertOnlyMgmIsInMemberList
import net.corda.applications.workers.rpc.utils.assignSoftHsm
import net.corda.applications.workers.rpc.utils.createMGMGroupPolicyJson
import net.corda.applications.workers.rpc.utils.createVirtualNode
import net.corda.applications.workers.rpc.utils.disableCLRChecks
import net.corda.applications.workers.rpc.utils.genGroupPolicy
import net.corda.applications.workers.rpc.utils.genKeyPair
import net.corda.applications.workers.rpc.utils.generateCert
import net.corda.applications.workers.rpc.utils.generateCsr
import net.corda.applications.workers.rpc.utils.getCa
import net.corda.applications.workers.rpc.utils.getMemberRegistrationContext
import net.corda.applications.workers.rpc.utils.getMgmRegistrationContext
import net.corda.applications.workers.rpc.utils.groupId
import net.corda.applications.workers.rpc.utils.keyExists
import net.corda.applications.workers.rpc.utils.lookupMembers
import net.corda.applications.workers.rpc.utils.name
import net.corda.applications.workers.rpc.utils.register
import net.corda.applications.workers.rpc.utils.setUpNetworkIdentity
import net.corda.applications.workers.rpc.utils.status
import net.corda.applications.workers.rpc.utils.tmpPath
import net.corda.applications.workers.rpc.utils.toByteArray
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
import java.io.File

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

    private val mgmRpcHost = "$RPC_WORKER.$MGM_CLUSTER_NS"
    private val mgmP2pHost = "$P2P_GATEWAY.$MGM_CLUSTER_NS"
    private val mgmTestToolkit by TestToolkitProperty(mgmRpcHost, RPC_PORT)

    private val aliceRpcHost = "$RPC_WORKER.$ALICE_CLUSTER_NS"
    private val aliceP2pHost = "$P2P_GATEWAY.$ALICE_CLUSTER_NS"
    private val aliceTestToolkit by TestToolkitProperty(aliceRpcHost, RPC_PORT)

    private val bobRpcHost = "$RPC_WORKER.$BOB_CLUSTER_NS"
    private val bobP2pHost = "$P2P_GATEWAY.$BOB_CLUSTER_NS"
    private val bobTestToolkit by TestToolkitProperty(bobRpcHost, RPC_PORT)

    private val mgmCluster = ClusterTestData(
        mgmTestToolkit,
        mgmP2pHost,
        listOf(MemberTestData("O=Mgm, L=London, C=GB, OU=${mgmTestToolkit.uniqueName}"))
    )

    private val aliceCluster = ClusterTestData(
        aliceTestToolkit,
        aliceP2pHost,
        listOf(
            MemberTestData("O=Alice, L=London, C=GB, OU=${aliceTestToolkit.uniqueName}"),
        )
    )
    private val bobCluster = ClusterTestData(
        bobTestToolkit,
        bobP2pHost,
        listOf(
            MemberTestData("O=Bob, L=London, C=GB, OU=${bobTestToolkit.uniqueName}"),
        )
    )

    private val clusters = listOf(aliceCluster, bobCluster)

    private val ca = getCa()

    @Test
    fun `Create mgm and allow members to join the group`() {
        // For the purposes of this test, the MGM cluster is
        // expected to have only one MGM (in reality there can be more on a cluster).
        assertThat(mgmCluster.members).hasSize(1)

        val holdingIds = mutableMapOf<String, String>()
        val mgm = mgmCluster.members[0]
        mgmCluster.disableCLRChecks()
        val cpiChecksum = mgmCluster.uploadCpi(createMGMGroupPolicyJson(), true)
        val mgmHoldingId = mgmCluster.createVirtualNode(mgm, cpiChecksum)
        holdingIds[mgm.name] = mgmHoldingId
        println("MGM HoldingIdentity: $mgmHoldingId")

        mgmCluster.assignSoftHsm(mgmHoldingId, HSM_CAT_SESSION)

        val mgmSessionKeyId = mgmCluster.genKeyPair(mgmHoldingId, HSM_CAT_SESSION)

        mgmCluster.register(
            mgmHoldingId,
            getMgmRegistrationContext(
                tlsTrustRoot = ca.caCertificate.toPem(),
                sessionKeyId = mgmSessionKeyId,
                p2pUrl = mgmCluster.p2pUrl
            )
        )

        mgmCluster.assertOnlyMgmIsInMemberList(mgmHoldingId, mgm.name)

        if (!mgmCluster.keyExists(P2P_TENANT_ID, HSM_CAT_TLS)) {
            val mgmTlsKeyId = mgmCluster.genKeyPair(P2P_TENANT_ID, HSM_CAT_TLS)
            val mgmTlsCsr = mgmCluster.generateCsr(mgm, mgmTlsKeyId)
            val mgmTlsCert = ca.generateCert(mgmTlsCsr)
            mgmCluster.uploadTlsCertificate(mgmTlsCert)
        }

        mgmCluster.setUpNetworkIdentity(
            mgmHoldingId,
            mgmSessionKeyId
        )

        val memberGroupPolicy = mgmCluster.genGroupPolicy(mgmHoldingId)

        clusters.forEach { cordaCluster ->
            cordaCluster.disableCLRChecks()
            val memberCpiChecksum = cordaCluster.uploadCpi(toByteArray(memberGroupPolicy))

            cordaCluster.members.forEach { member ->
                val memberHoldingId = cordaCluster.createVirtualNode(member, memberCpiChecksum)
                holdingIds[member.name] = memberHoldingId
                println("${member.name} holding ID: $memberHoldingId")

                cordaCluster.assignSoftHsm(memberHoldingId, HSM_CAT_SESSION)
                cordaCluster.assignSoftHsm(memberHoldingId, HSM_CAT_LEDGER)

                if (!cordaCluster.keyExists(P2P_TENANT_ID, HSM_CAT_TLS)) {
                    val memberTlsKeyId = cordaCluster.genKeyPair(P2P_TENANT_ID, HSM_CAT_TLS)
                    val memberTlsCsr = cordaCluster.generateCsr(member, memberTlsKeyId)
                    val memberTlsCert = ca.generateCert(memberTlsCsr)
                    cordaCluster.uploadTlsCertificate(memberTlsCert)
                }

                val memberSessionKeyId = cordaCluster.genKeyPair(memberHoldingId, HSM_CAT_SESSION)
                val memberLedgerKeyId = cordaCluster.genKeyPair(memberHoldingId, HSM_CAT_LEDGER)

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
        }

        val allMembers = clusters.flatMap { it.members } + mgm
        val clusterMembers = mutableMapOf<ClusterTestData, MutableList<RpcMemberInfo>>()
        (clusters + mgmCluster).forEach { cordaCluster ->
            cordaCluster.members.forEach {
                val holdingId = holdingIds[it.name]
                assertNotNull(holdingId)

                eventually(
                    waitBetween = 2.seconds,
                    duration = 10.seconds
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

        /**
         * This is a temporary addition to the test. This function prints some commands to the console to assist in
         * manually confirming that the P2P messages can be sent between members which registered.
         * This is temporary until it is possible to access kafka directly to confirm.
         */
        printAppSimulatorConfig(
            clusterMembers[clusters[0]]!!.first(),
            clusterMembers[clusters[1]]!!.first()
        )
    }

    private fun printAppSimulatorConfig(
        source: RpcMemberInfo,
        destination: RpcMemberInfo
    ) {
        val senderConfig = """{
                parallelClients: 1,
                simulatorMode: "SENDER",
                loadGenerationParams: {
                    peerX500Name: "${destination.name}",
                    peerGroupId: "${destination.groupId}",
                    ourX500Name: "${source.name}",
                    ourGroupId: "${source.groupId}",
                    loadGenerationType: "ONE_OFF",
                    batchSize: 1,
                    totalNumberOfMessages: 1,
                    interBatchDelay: 0ms,
                    messageSizeBytes: 10000
                }
            }""".trimIndent()
        val configFileName = "simulator_sender.conf"
        val buildDir = File(tmpPath)
        val simulatorSenderConfig = File(buildDir, configFileName).apply {
            writeText(senderConfig)
        }

        val caLocation = "${buildDir.absolutePath}${File.separator}ca.crt"

        println(
            """
                The following steps allow for manual verification of connectivity between clusters. View the kafka topic to observe messages being sent across clusters.
                
                To test connection get the kafka trust store by running:
                
                kubectl get secret prereqs-kafka-0-tls -o go-template='{{ index .data "ca.crt" }}' -n $ALICE_CLUSTER_NS | base64 -D > $caLocation
                
                Then run the following with that truststore (Ensure running where corda-app-simulator-5.0.0.0-SNAPSHOT.jar is reachable):
                
                java -jar corda-app-simulator-5.0.0.0-SNAPSHOT.jar -mbootstrap.servers=prereqs-kafka.$ALICE_CLUSTER_NS:9092 -msecurity.protocol=SSL -mssl.truststore.location=$caLocation -mssl.truststore.type=PEM  --simulator-config ${simulatorSenderConfig.absolutePath}

        """.trimIndent()
        )
    }
}
