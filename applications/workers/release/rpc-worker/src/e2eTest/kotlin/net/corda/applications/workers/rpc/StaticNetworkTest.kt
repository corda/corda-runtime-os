package net.corda.applications.workers.rpc

import net.corda.applications.workers.rpc.http.TestToolkitProperty
import net.corda.applications.workers.rpc.kafka.KafkaTestToolKit
import net.corda.applications.workers.rpc.utils.ClusterTestData
import net.corda.applications.workers.rpc.utils.KEY_SCHEME
import net.corda.applications.workers.rpc.utils.MemberTestData
import net.corda.applications.workers.rpc.utils.RPC_PORT
import net.corda.applications.workers.rpc.utils.RPC_WORKER
import net.corda.applications.workers.rpc.utils.SINGLE_CLUSTER_NS
import net.corda.applications.workers.rpc.utils.assertMemberInMemberList
import net.corda.applications.workers.rpc.utils.assertP2pConnectivity
import net.corda.applications.workers.rpc.utils.createStaticMemberGroupPolicyJson
import net.corda.applications.workers.rpc.utils.createVirtualNode
import net.corda.applications.workers.rpc.utils.getCa
import net.corda.applications.workers.rpc.utils.groupId
import net.corda.applications.workers.rpc.utils.name
import net.corda.applications.workers.rpc.utils.register
import net.corda.applications.workers.rpc.utils.status
import net.corda.applications.workers.rpc.utils.uploadCpi
import net.corda.data.identity.HoldingIdentity
import net.corda.membership.httprpc.v1.MemberLookupRpcOps
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.*
import java.util.concurrent.TimeUnit

class StaticNetworkTest {
    companion object {
        // If running deployment in eks, ensure this is set to true to use correct endpoint information.
        // This should be false by default for automated builds.
        private const val IS_REMOTE_CLUSTER = false
    }

    private val remoteRpcHost = "$RPC_WORKER.$SINGLE_CLUSTER_NS"
    private val remoteTestToolkit by TestToolkitProperty(remoteRpcHost, RPC_PORT)
    private val localTestToolkit by TestToolkitProperty()

    private val testToolkit = if (IS_REMOTE_CLUSTER) remoteTestToolkit else localTestToolkit

    private val kafkaToolKit by lazy {
        KafkaTestToolKit(testToolkit)
    }

    private val ca = getCa()

    private val cordaCluster = ClusterTestData(
        testToolkit,
        "https://localhost:8080",
        (1..5).map {
            MemberTestData("C=GB, L=London, O=Member-${testToolkit.uniqueName}")
        }
    )

    @Test
    fun `register members`() {
        val groupId = UUID.randomUUID().toString()
        val cpiCheckSum = cordaCluster.uploadCpi(
            createStaticMemberGroupPolicyJson(ca, groupId, cordaCluster)
        )

        val holdingIds = cordaCluster.members.map { member ->
            val holdingId = cordaCluster.createVirtualNode(member, cpiCheckSum)

            cordaCluster.register(
                holdingId,
                mapOf(
                    "corda.key.scheme" to KEY_SCHEME
                )
            )

            // Check registration complete.
            // Eventually we can use the registration status endpoint.
            // For now just assert we have received our own member data.
            cordaCluster.assertMemberInMemberList(
                holdingId,
                member
            )

            holdingId
        }

        testToolkit.httpClientFor(MemberLookupRpcOps::class.java).use { client ->
            val proxy = client.start().proxy
            holdingIds.forEach { id ->
                val members = proxy.lookup(id).members

                assertThat(members)
                    .hasSize(holdingIds.size)
                    .allSatisfy {
                        assertThat(it.status).isEqualTo("ACTIVE")
                        assertThat(it.groupId).isEqualTo(groupId)
                    }
                val names = members.map { it.name }
                assertThat(names)
                    .containsExactlyInAnyOrderElementsOf(
                        cordaCluster.members.map {
                            it.name
                        }
                    )
            }
        }
    }

    /*
    This test is disabled until CORE-6079 is ready.
    When CORE-6079 is ready, please delete the `register members` test (as this one will cover that use case as well)
    To run it locally while disabled follow the instruction in resources/RunP2PTest.md:
     */
    @Test
    @Disabled("This test is disabled until CORE-6079 is ready")
    @Timeout(value = 10, unit = TimeUnit.MINUTES)
    fun `create a static network, register members and exchange messages between them via p2p`() {
        val groupId = UUID.randomUUID().toString()
        // Create two identities
        val sender = HoldingIdentity(
            cordaCluster.members[0].name,
            groupId,
        )

        val receiver = HoldingIdentity(
            cordaCluster.members[1].name,
            groupId,
        )
        val cpiCheckSum = cordaCluster.uploadCpi(
            createStaticMemberGroupPolicyJson(ca, groupId, cordaCluster)
        )
        cordaCluster.members.map { member ->
            val holdingId = cordaCluster.createVirtualNode(member, cpiCheckSum)

            cordaCluster.register(
                holdingId,
                mapOf(
                    "corda.key.scheme" to KEY_SCHEME
                )
            )

            // Check registration complete.
            // Eventually we can use the registration status endpoint.
            // For now just assert we have received our own member data.
            cordaCluster.assertMemberInMemberList(
                holdingId,
                member
            )
        }

        kafkaToolKit.assertP2pConnectivity(
            sender,
            receiver,
            groupId,
            testToolkit
        )
    }
}
