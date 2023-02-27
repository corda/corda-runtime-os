package net.corda.applications.workers.rest.utils

import net.corda.applications.workers.rest.kafka.KafkaTestToolKit
import net.corda.data.identity.HoldingIdentity
import net.corda.messaging.api.records.Record
import net.corda.data.p2p.app.AppMessage
import net.corda.data.p2p.app.AuthenticatedMessage
import net.corda.data.p2p.app.AuthenticatedMessageHeader
import net.corda.data.p2p.app.UnauthenticatedMessage
import net.corda.data.p2p.app.UnauthenticatedMessageHeader
import net.corda.schema.Schemas.P2P.P2P_IN_TOPIC
import net.corda.schema.Schemas.P2P.P2P_OUT_TOPIC
import net.corda.v5.base.types.MemberX500Name
import org.assertj.core.api.Assertions.assertThat
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

fun assertP2pConnectivity(
    sender: HoldingIdentity,
    receiver: HoldingIdentity,
    senderKafkaTestToolKit: KafkaTestToolKit,
    receiverKafkaTestToolKit: KafkaTestToolKit = senderKafkaTestToolKit
) {
    assertThat(receiver.groupId)
        .isEqualTo(sender.groupId)
    val groupId = sender.groupId
    val traceId = "e2e-test-$groupId"
    val subSystem = "e2e-test"

    // Create authenticated messages
    val numberOfAuthenticatedMessages = 5
    val authenticatedMessagesIdToContent = (1..numberOfAuthenticatedMessages).associate {
        senderKafkaTestToolKit.uniqueName to senderKafkaTestToolKit.uniqueName
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
        Record(P2P_OUT_TOPIC, senderKafkaTestToolKit.uniqueName, AppMessage(message))
    }

    // Create unauthenticated messages
    val numberOfUnauthenticatedMessages = 3
    val unauthenticatedMessagesContent = (1..numberOfUnauthenticatedMessages).associate {
        senderKafkaTestToolKit.uniqueName to senderKafkaTestToolKit.uniqueName
    }
    val unauthenticatedRecords = unauthenticatedMessagesContent.map { (messageId, content) ->
        val messageHeader = UnauthenticatedMessageHeader.newBuilder()
            .setDestination(receiver)
            .setSource(sender)
            .setSubsystem(subSystem)
            .setMessageId(messageId)
            .build()
        val message = UnauthenticatedMessage.newBuilder()
            .setHeader(messageHeader)
            .setPayload(ByteBuffer.wrap(content.toByteArray()))
            .build()
        Record(P2P_OUT_TOPIC, senderKafkaTestToolKit.uniqueName, AppMessage(message))
    }

    // Accept messages
    val receivedAuthenticatedMessages = ConcurrentHashMap<String, String>()
    val receivedUnauthenticatedMessages = ConcurrentHashMap<String, String>()
    val countDown = CountDownLatch(numberOfUnauthenticatedMessages + numberOfAuthenticatedMessages)
    receiverKafkaTestToolKit.acceptRecordsFromKafka<String, AppMessage>(P2P_IN_TOPIC) { record ->
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
            receivedUnauthenticatedMessages[message.header.messageId] = String(message.payload.array())
            countDown.countDown()
        }
    }.use {
        // Send messages
        senderKafkaTestToolKit.publishRecordsToKafka(unauthenticatedRecords + authenticatedRecords)
        countDown.await(5, TimeUnit.MINUTES)
    }

    assertThat(receivedAuthenticatedMessages).containsAllEntriesOf(authenticatedMessagesIdToContent)
    assertThat(receivedUnauthenticatedMessages).containsAllEntriesOf(unauthenticatedMessagesContent)
}

fun String.clearX500Name(): String {
    return MemberX500Name.parse(this).toString()
}