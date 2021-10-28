package net.corda.p2p.linkmanager.delivery

import net.corda.configuration.read.ConfigurationReadService
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.DominoTile
import net.corda.lifecycle.domino.logic.util.ResourcesHolder
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.p2p.linkmanager.LinkManager
import net.corda.p2p.linkmanager.LinkManagerNetworkMap
import net.corda.p2p.linkmanager.messaging.MessageConverter
import net.corda.p2p.linkmanager.sessions.SessionManager
import net.corda.p2p.schema.Schema
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

class InMemorySessionReplayer(
    publisherFactory: PublisherFactory,
    configurationReaderService: ConfigurationReadService,
    coordinatorFactory: LifecycleCoordinatorFactory,
    private val networkMap: LinkManagerNetworkMap,
): Lifecycle {

    companion object {
        const val MESSAGE_REPLAYER_CLIENT_ID = "session-message-replayer-client"
    }

    private var logger = LoggerFactory.getLogger(this::class.java.name)

    private val config = PublisherConfig(MESSAGE_REPLAYER_CLIENT_ID, 1)
    private val publisher = publisherFactory.createPublisher(config)
    private val replayScheduler = ReplayScheduler(coordinatorFactory, configurationReaderService,
        LinkManagerConfiguration.MESSAGE_REPLAY_PERIOD_KEY, ::replayMessage, setOf(networkMap.getDominoTile()))

    val dominoTile = DominoTile(this::class.java.simpleName, coordinatorFactory, ::startResources, setOf(replayScheduler.dominoTile))

    private fun startResources(resources: ResourcesHolder) {
        publisher.start()
        resources.keep(publisher)
        dominoTile.resourcesStarted(false)
    }

    override val isRunning: Boolean
        get() = dominoTile.isRunning

    override fun start() {
        if (!isRunning) {
            publisher.start()
            replayScheduler.start()
        }
    }

    override fun stop() {
        if (isRunning) {
            replayScheduler.stop()
        }
    }

    data class SessionMessageReplay(
        val message: Any,
        val sessionId: String,
        val source: LinkManagerNetworkMap.HoldingIdentity,
        val dest: LinkManagerNetworkMap.HoldingIdentity,
        val sentSessionMessageCallback: (key: SessionManager.SessionKey, sessionId: String) -> Any
    )

    fun addMessageForReplay(
        uniqueId: String,
        messageReplay: SessionMessageReplay
    ) {
        dominoTile.withLifecycleLock {
            if (!isRunning) {
                throw IllegalStateException("A message was added for replay before the InMemorySessionReplayer was started.")
            }
            replayScheduler.addForReplay(Instant.now().toEpochMilli(), uniqueId, messageReplay)
        }
    }

    fun removeMessageFromReplay(uniqueId: String) {
        replayScheduler.removeFromReplay(uniqueId)
    }

    private fun replayMessage(
        messageReplay: SessionMessageReplay,
    ) {
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
        messageReplay.sentSessionMessageCallback(
            SessionManager.SessionKey(messageReplay.source, messageReplay.dest),
            messageReplay.sessionId
        )
    }
}