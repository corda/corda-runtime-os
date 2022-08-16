package net.corda.applications.workers.rpc

import net.corda.applications.workers.rpc.utils.E2eClusterFactory
import net.corda.applications.workers.rpc.utils.KEY_SCHEME
import net.corda.applications.workers.rpc.utils.MemberTestData
import net.corda.applications.workers.rpc.utils.assertMemberInMemberList
import net.corda.applications.workers.rpc.utils.assertP2pConnectivity
import net.corda.applications.workers.rpc.utils.createStaticMemberGroupPolicyJson
import net.corda.applications.workers.rpc.utils.createVirtualNode
import net.corda.applications.workers.rpc.utils.getCa
import net.corda.applications.workers.rpc.utils.groupId
import net.corda.applications.workers.rpc.utils.lookupMembers
import net.corda.applications.workers.rpc.utils.name
import net.corda.applications.workers.rpc.utils.register
import net.corda.applications.workers.rpc.utils.status
import net.corda.applications.workers.rpc.utils.uploadCpi
import net.corda.data.identity.HoldingIdentity
import net.corda.test.util.eventually
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.*
import java.util.concurrent.TimeUnit

class StaticNetworkTest {
    private val ca = getCa()

    private val cordaCluster = E2eClusterFactory.getE2eCluster().also { cluster ->
        cluster.addMembers(
            (1..5).map {
                MemberTestData("C=GB, L=London, O=Member-${cluster.testToolkit.uniqueName}")
            }
        )
    }


    @Test
    fun `register members`() {
        onboardStaticGroup()
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
        val groupId = onboardStaticGroup()

        assertP2pConnectivity(
            HoldingIdentity(cordaCluster.members[0].name, groupId),
            HoldingIdentity(cordaCluster.members[1].name, groupId),
            cordaCluster.kafkaTestToolkit
        )
    }

    private fun onboardStaticGroup(): String {
        val groupId = UUID.randomUUID().toString()
        val cpiCheckSum = cordaCluster.uploadCpi(
            createStaticMemberGroupPolicyJson(ca, groupId, cordaCluster)
        )

        val holdingIds = cordaCluster.members.associate { member ->
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

            member.name to holdingId
        }

        cordaCluster.members.forEach {
            val holdingId = holdingIds[it.name]
            Assertions.assertNotNull(holdingId)
            eventually {
                cordaCluster.lookupMembers(holdingId!!).also { result ->
                    assertThat(result)
                        .hasSize(cordaCluster.members.size)
                        .allSatisfy { memberInfo ->
                            assertThat(memberInfo.status).isEqualTo("ACTIVE")
                            assertThat(memberInfo.groupId).isEqualTo(groupId)
                        }
                    assertThat(result.map { memberInfo -> memberInfo.name })
                        .hasSize(cordaCluster.members.size)
                        .containsExactlyInAnyOrderElementsOf(
                            cordaCluster.members.map { member ->
                                member.name
                            }
                        )
                }
            }
        }
        return groupId
    }
}
