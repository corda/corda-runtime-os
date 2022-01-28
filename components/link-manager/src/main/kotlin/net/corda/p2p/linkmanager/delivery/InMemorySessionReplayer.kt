package net.corda.p2p.linkmanager.delivery

import net.corda.configuration.read.ConfigurationReadService
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.DominoTile
import net.corda.lifecycle.domino.logic.DominoTileV2
import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.lifecycle.domino.logic.LifecycleWithDominoTileV2
import net.corda.lifecycle.domino.logic.util.PublisherWithDominoLogic
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.p2p.linkmanager.LinkManager
import net.corda.p2p.linkmanager.LinkManagerNetworkMap
import net.corda.p2p.linkmanager.messaging.MessageConverter
import net.corda.p2p.linkmanager.sessions.SessionManager
import net.corda.schema.Schemas.P2P.Companion.LINK_OUT_TOPIC
import net.corda.v5.base.util.debug
import org.slf4j.LoggerFactory
import java.time.Instant

@Suppress("LongParameterList")
class InMemorySessionReplayer(
    publisherFactory: PublisherFactory,
    configurationReaderService: ConfigurationReadService,
    coordinatorFactory: LifecycleCoordinatorFactory,
    configuration: SmartConfig,
    private val networkMap: LinkManagerNetworkMap
): LifecycleWithDominoTileV2 {

    companion object {
        const val MESSAGE_REPLAYER_CLIENT_ID = "session-message-replayer-client"
    }

    private var logger = LoggerFactory.getLogger(this::class.java.name)

    private val publisher = PublisherWithDominoLogic(
        publisherFactory,
        coordinatorFactory,
        PublisherConfig(MESSAGE_REPLAYER_CLIENT_ID),
        configuration
    )

    private val replayScheduler = ReplayScheduler(coordinatorFactory, configurationReaderService,
        LinkManagerConfiguration.MESSAGE_REPLAY_PERIOD_KEY, ::replayMessage)

    override val dominoTile = DominoTileV2(
        this::class.java.simpleName,
        coordinatorFactory,
        dependentChildren = setOf(replayScheduler.dominoTile, publisher.dominoTile, networkMap.dominoTile),
        managedChildren = setOf(replayScheduler.dominoTile, publisher.dominoTile)
    )

    data class SessionMessageReplay(
        val message: Any,
        val sessionId: String,
        val source: LinkManagerNetworkMap.HoldingIdentity,
        val dest: LinkManagerNetworkMap.HoldingIdentity,
        val sentSessionMessageCallback: (key: SessionManager.SessionKey, sessionId: String) -> Unit
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

    fun removeAllMessagesFromReplay() {
        replayScheduler.removeAllMessagesFromReplay()
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
        logger.debug { "Replaying session message ${message.payload.javaClass} for session ${messageReplay.sessionId}." }
        publisher.publish(listOf(Record(LINK_OUT_TOPIC, LinkManager.generateKey(), message)))
        messageReplay.sentSessionMessageCallback(
            SessionManager.SessionKey(messageReplay.source, messageReplay.dest),
            messageReplay.sessionId
        )
    }
}