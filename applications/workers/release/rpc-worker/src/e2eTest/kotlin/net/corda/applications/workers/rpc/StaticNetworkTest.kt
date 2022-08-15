package net.corda.applications.workers.rpc

import net.corda.applications.workers.rpc.http.TestToolkitProperty
import net.corda.applications.workers.rpc.kafka.KafkaTestToolKit
import net.corda.applications.workers.rpc.utils.ClusterTestData
import net.corda.applications.workers.rpc.utils.KEY_SCHEME
import net.corda.applications.workers.rpc.utils.MemberTestData
import net.corda.applications.workers.rpc.utils.assertMemberInMemberList
import net.corda.applications.workers.rpc.utils.clearX500Name
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
import net.corda.messaging.api.records.Record
import net.corda.p2p.app.AppMessage
import net.corda.p2p.app.AuthenticatedMessage
import net.corda.p2p.app.AuthenticatedMessageHeader
import net.corda.p2p.app.UnauthenticatedMessage
import net.corda.p2p.app.UnauthenticatedMessageHeader
import net.corda.schema.Schemas.P2P.Companion.P2P_IN_TOPIC
import net.corda.schema.Schemas.P2P.Companion.P2P_OUT_TOPIC
import net.corda.test.util.eventually
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class StaticNetworkTest {
    private val rpcHost = System.getProperty("e2eClusterARpcHost")
    private val rpcPort = System.getProperty("e2eClusterARpcPort").toInt()
    private val testToolkit by TestToolkitProperty(rpcHost, rpcPort)
    private val kafkaToolKit by lazy {
        KafkaTestToolKit(testToolkit)
    }

    private val p2pHost = System.getProperty("e2eClusterAP2pHost")
    private val p2pPort = System.getProperty("e2eClusterAP2pPort").toInt()

    private val ca = getCa()

    private val cordaCluster = ClusterTestData(
        testToolkit,
        p2pHost,
        p2pPort,
        (1..5).map {
            MemberTestData("C=GB, L=London, O=Member-${testToolkit.uniqueName}")
        }
    )

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
        // Create two identities
        val sender = HoldingIdentity(
            cordaCluster.members[0].name,
            groupId,
        )

        val receiver = HoldingIdentity(
            cordaCluster.members[1].name,
            groupId,
        )

        val traceId = "e2e-test-$groupId"
        val subSystem = "e2e-test"

        // Create authenticated messages
        val numberOfAuthenticatedMessages = 5
        val authenticatedMessagesIdToContent = (1..numberOfAuthenticatedMessages).associate {
            testToolkit.uniqueName to testToolkit.uniqueName
        }
        val authenticatedRecords = authenticatedMessagesIdToContent.map { (id, content) ->
            val messageHeader = AuthenticatedMessageHeader.newBuilder()
                .setDestination(receiver)
                .setSource(sender)
                .setTtl(null)
                .setMessageId(id)
                .setTraceId(traceId)
                .setSubsystem(subSystem)
                .build()
            val message = AuthenticatedMessage.newBuilder()
                .setHeader(messageHeader)
                .setPayload(ByteBuffer.wrap(content.toByteArray()))
                .build()
            Record(P2P_OUT_TOPIC, testToolkit.uniqueName, AppMessage(message))
        }

        // Create unauthenticated messages
        val numberOfUnauthenticatedMessages = 3
        val unauthenticatedMessagesContent = (1..numberOfUnauthenticatedMessages).map {
            testToolkit.uniqueName
        }
        val unauthenticatedRecords = unauthenticatedMessagesContent.map { content ->
            val messageHeader = UnauthenticatedMessageHeader.newBuilder()
                .setDestination(receiver)
                .setSource(sender)
                .setSubsystem(subSystem)
                .build()
            val message = UnauthenticatedMessage.newBuilder()
                .setHeader(messageHeader)
                .setPayload(ByteBuffer.wrap(content.toByteArray()))
                .build()
            Record(P2P_OUT_TOPIC, testToolkit.uniqueName, AppMessage(message))
        }

        // Accept messages
        val receivedAuthenticatedMessages = ConcurrentHashMap<String, String>()
        val receivedUnauthenticatedMessages = ConcurrentHashMap.newKeySet<String>()
        val countDown = CountDownLatch(numberOfUnauthenticatedMessages + numberOfAuthenticatedMessages)
        kafkaToolKit.acceptRecordsFromKafka<String, AppMessage>(P2P_IN_TOPIC) { record ->
            val message = record.value?.message
            if (message is AuthenticatedMessage) {
                if (message.header.destination.x500Name.clearX500Name() != receiver.x500Name.clearX500Name()) {
                    return@acceptRecordsFromKafka
                }
                if (message.header.destination.groupId != groupId) {
                    return@acceptRecordsFromKafka
                }
                if (message.header.source.x500Name.clearX500Name() != sender.x500Name.clearX500Name()) {
                    return@acceptRecordsFromKafka
                }
                if (message.header.source.groupId != groupId) {
                    return@acceptRecordsFromKafka
                }
                if (message.header.traceId != traceId) {
                    return@acceptRecordsFromKafka
                }
                if (message.header.subsystem != subSystem) {
                    return@acceptRecordsFromKafka
                }
                receivedAuthenticatedMessages[message.header.messageId] = String(message.payload.array())
                countDown.countDown()
            } else if (message is UnauthenticatedMessage) {
                if (message.header.destination.x500Name.clearX500Name() != receiver.x500Name.clearX500Name()) {
                    return@acceptRecordsFromKafka
                }
                if (message.header.destination.groupId != groupId) {
                    return@acceptRecordsFromKafka
                }
                if (message.header.source.x500Name.clearX500Name() != sender.x500Name.clearX500Name()) {
                    return@acceptRecordsFromKafka
                }
                if (message.header.source.groupId != groupId) {
                    return@acceptRecordsFromKafka
                }
                if (message.header.subsystem != subSystem) {
                    return@acceptRecordsFromKafka
                }
                receivedUnauthenticatedMessages.add(String(message.payload.array()))
                countDown.countDown()
            }
        }.use {
            // Send messages
            kafkaToolKit.publishRecordsToKafka(unauthenticatedRecords + authenticatedRecords)
            countDown.await(5, TimeUnit.MINUTES)
        }

        assertThat(receivedAuthenticatedMessages).containsAllEntriesOf(authenticatedMessagesIdToContent)
        assertThat(receivedUnauthenticatedMessages).containsAll(unauthenticatedMessagesContent)

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
