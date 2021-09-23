package net.corda.p2p.linkmanager.delivery

import net.corda.lifecycle.Lifecycle
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.p2p.linkmanager.LinkManager
import net.corda.p2p.linkmanager.LinkManagerNetworkMap
import net.corda.p2p.linkmanager.messaging.MessageConverter
import net.corda.p2p.schema.Schema
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

class InMemorySessionReplayer(
    sessionMessageReplayPeriod: Duration,
    publisherFactory: PublisherFactory,
    private val networkMap: LinkManagerNetworkMap
): Lifecycle, SessionReplayer {

    companion object {
        const val MESSAGE_REPLAYER_CLIENT_ID = "session-message-replayer-client"
    }

    private var logger = LoggerFactory.getLogger(this::class.java.name)

    @Volatile
    private var running = false
    private val startStopLock = ReentrantReadWriteLock()
    private val config = PublisherConfig(MESSAGE_REPLAYER_CLIENT_ID, 1)
    private val publisher = publisherFactory.createPublisher(config)
    private val replayScheduler = ReplayScheduler(sessionMessageReplayPeriod, ::replayMessage)

    override val isRunning: Boolean
        get() = running

    override fun start() {
        startStopLock.write {
            if (!isRunning) {
                publisher.start()
                replayScheduler.start()
                running = true
            }
        }
    }

    override fun stop() {
        startStopLock.write {
            if (isRunning) {
                running = false
                replayScheduler.stop()
            }
        }
    }

    override fun addMessageForReplay(uniqueId: String, messageReplay: SessionReplayer.SessionMessageReplay) {
        startStopLock.read {
            if (!running) {
                throw IllegalStateException("A message was added for replay before the InMemorySessionReplayer was started.")
            }
            replayScheduler.addForReplay(Instant.now().toEpochMilli(), uniqueId, messageReplay)
        }
    }

    override fun removeMessageFromReplay(uniqueId: String) {
        replayScheduler.removeFromReplay(uniqueId)
    }

    private fun replayMessage(messageReplay: SessionReplayer.SessionMessageReplay) {

        val memberInfo = networkMap.getMemberInfo(messageReplay.dest)
        if (memberInfo == null) {
            logger.warn("Attempted to replay a session negotiation message (type ${messageReplay.message::class.java.simpleName})" +
                " with peer ${messageReplay.dest} which is not in the network map. The message was not replayed.")
            return
        }

        val networkType = networkMap.getNetworkType(memberInfo.holdingIdentity.groupId)
        if (networkType == null) {
            logger.warn("Attempted to replay a session negotiation message (type ${messageReplay.message::class.java.simpleName}) but" +
                " could not find the network type in the NetworkMap for group ${memberInfo.holdingIdentity.groupId}." +
                " The message was not replayed.")
            return
        }

        val message = MessageConverter.createLinkOutMessage(messageReplay.message, memberInfo, networkType)
        publisher.publish(listOf(Record(Schema.LINK_OUT_TOPIC, LinkManager.generateKey(), message)))
    }
}