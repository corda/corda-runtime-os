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
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class InMemorySessionReplayer(
    sessionMessageReplayPeriod: Long,
    publisherFactory: PublisherFactory,
    private val networkMap: LinkManagerNetworkMap
): Lifecycle {

    companion object {
        const val MESSAGE_REPLAYER_CLIENT_ID = "session-message-replayer-client"
    }

    private var logger = LoggerFactory.getLogger(this::class.java.name)

    @Volatile
    private var running = false
    private val startStopLock = ReentrantLock()
    private val config = PublisherConfig(MESSAGE_REPLAYER_CLIENT_ID, null)
    private val publisher = publisherFactory.createPublisher(config)
    private val replayManager = ReplayManager(sessionMessageReplayPeriod, ::replayMessage)

    override val isRunning: Boolean
        get() = running

    override fun start() {
        startStopLock.withLock {
            if (!isRunning) {
                publisher.start()
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

    sealed class DestIdLookup {
        data class HoldingIdentity(val id: LinkManagerNetworkMap.HoldingIdentity): DestIdLookup()

        data class PublicKeyHash(val hash: ByteArray, val groupId: String): DestIdLookup() {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false

                other as PublicKeyHash

                if (!hash.contentEquals(other.hash)) return false

                return true
            }

            override fun hashCode(): Int {
                return hash.contentHashCode()
            }
        }
    }

    data class SessionMessageReplay(
        val message: Any,
        val source: LinkManagerNetworkMap.HoldingIdentity,
        val dest: DestIdLookup
    )

    fun addMessageForReplay(uniqueId: String, messageReplay: SessionMessageReplay) {
        replayManager.addForReplay(uniqueId, messageReplay)
    }

    fun removeMessageFromReplay(uniqueId: String) {
        replayManager.removeFromReplay(uniqueId)
    }

    private fun replayMessage(messageReplay: SessionMessageReplay) {
        val networkType = networkMap.getNetworkType(messageReplay.source.groupId)
        if (networkType == null) {
            logger.warn("Attempted to replay a session negotiation message (type ${messageReplay::class.java.simpleName}) but could not" +
                " find the network type in the NetworkMap for our identity ${messageReplay.source}. The message was not replayed.")
            return
        }

        val responderMemberInfo = when (val dest = messageReplay.dest) {
            is DestIdLookup.HoldingIdentity -> {
                val memberInfo = networkMap.getMemberInfo(dest.id)
                if (memberInfo == null) {
                    logger.warn("Attempted to replay a session negotiation message (type ${messageReplay::class.java.simpleName}) with" +
                            " peer ${messageReplay.dest} which is not in the network map. The message was not replayed.")
                    return
                }
                memberInfo
            }
            is DestIdLookup.PublicKeyHash -> {
                val memberInfo = networkMap.getMemberInfo(dest.hash, dest.groupId)
                if (memberInfo == null) {
                    logger.warn("Attempted to replay a session negotiation message (type ${messageReplay::class.java.simpleName}) with" +
                        " peer ${messageReplay.dest} which is not in the network map. The message was not replayed.")
                    return
                }
                memberInfo
            }
        }

        val message = MessageConverter.createLinkOutMessage(messageReplay.message, responderMemberInfo, networkType)
        publisher.publish(listOf(Record(Schema.LINK_OUT_TOPIC, LinkManager.generateKey(), message)))
    }
}