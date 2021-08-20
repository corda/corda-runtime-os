package net.corda.p2p.linkmanager.delivery

import net.corda.lifecycle.Lifecycle
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.p2p.linkmanager.LinkManager
import net.corda.p2p.linkmanager.LinkManagerNetworkMap
import net.corda.p2p.linkmanager.messaging.MessageConverter
import net.corda.p2p.linkmanager.delivery.SessionReplayer.IdentityLookup
import net.corda.p2p.schema.Schema
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class InMemorySessionReplayer(
    sessionMessageReplayPeriod: Long,
    publisherFactory: PublisherFactory,
    private val networkMap: LinkManagerNetworkMap
): Lifecycle, SessionReplayer {

    companion object {
        const val MESSAGE_REPLAYER_CLIENT_ID = "session-message-replayer-client"
    }

    private var logger = LoggerFactory.getLogger(this::class.java.name)

    @Volatile
    private var running = false
    private val startStopLock = ReentrantLock()
    private val config = PublisherConfig(MESSAGE_REPLAYER_CLIENT_ID, null)
    private val publisher = publisherFactory.createPublisher(config)
    private val replayScheduler = ReplayScheduler(sessionMessageReplayPeriod, ::replayMessage)

    override val isRunning: Boolean
        get() = running

    override fun start() {
        startStopLock.withLock {
            if (!isRunning) {
                publisher.start()
                replayScheduler.start()
                running = true
            }
        }
    }

    override fun stop() {
        startStopLock.withLock {
            if (isRunning) {
                running = false
            }
        }
    }

    override fun addMessageForReplay(uniqueId: String, messageReplay: SessionReplayer.SessionMessageReplay) {
        replayScheduler.addForReplay(Instant.now().toEpochMilli(), uniqueId, messageReplay)
    }

    override fun removeMessageFromReplay(uniqueId: String) {
        replayScheduler.removeFromReplay(uniqueId)
    }

    private fun replayMessage(messageReplay: SessionReplayer.SessionMessageReplay) {

        val responderMemberInfo = when (val dest = messageReplay.dest) {
            is IdentityLookup.HoldingIdentity -> {
                val memberInfo = networkMap.getMemberInfo(dest.id)
                if (memberInfo == null) {
                    logger.warn("Attempted to replay a session negotiation message (type ${messageReplay.message::class.java.simpleName})" +
                    " with peer ${dest.id} which is not in the network map. The message was not replayed.")
                    return
                }
                memberInfo
            }
            is IdentityLookup.PublicKeyHash -> {
                val memberInfo = networkMap.getMemberInfo(dest.hash, dest.groupId)
                if (memberInfo == null) {
                    logger.warn("Attempted to replay a session negotiation message (type ${messageReplay.message::class.java.simpleName})" +
                        " with public key hash ${messageReplay.dest} which is not in the network map. The message was not replayed.")
                    return
                }
                memberInfo
            }
        }

        val networkType = networkMap.getNetworkType(responderMemberInfo.holdingIdentity.groupId)
        if (networkType == null) {
            logger.warn("Attempted to replay a session negotiation message (type ${messageReplay.message::class.java.simpleName}) but" +
                    " could not find the network type in the NetworkMap for group ${responderMemberInfo.holdingIdentity.groupId}." +
                    " The message was not replayed.")
            return
        }

        val message = MessageConverter.createLinkOutMessage(messageReplay.message, responderMemberInfo, networkType)
        publisher.publish(listOf(Record(Schema.LINK_OUT_TOPIC, LinkManager.generateKey(), message)))
    }
}