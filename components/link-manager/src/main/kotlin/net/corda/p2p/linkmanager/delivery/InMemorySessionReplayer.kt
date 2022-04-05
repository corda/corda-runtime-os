package net.corda.p2p.linkmanager.delivery

import net.corda.configuration.read.ConfigurationReadService
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.lifecycle.domino.logic.util.PublisherWithDominoLogic
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.p2p.linkmanager.LinkManager
import net.corda.p2p.linkmanager.LinkManagerGroupPolicyProvider
import net.corda.p2p.linkmanager.LinkManagerInternalTypes
import net.corda.p2p.linkmanager.LinkManagerInternalTypes.toLMNetworkType
import net.corda.p2p.linkmanager.LinkManagerMembershipGroupReader
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
    private val groups: LinkManagerGroupPolicyProvider,
    private val members: LinkManagerMembershipGroupReader,
): LifecycleWithDominoTile {

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

    private val replayScheduler = ReplayScheduler(coordinatorFactory, configurationReaderService, false, ::replayMessage)

    override val dominoTile = ComplexDominoTile(
        this::class.java.simpleName,
        coordinatorFactory,
        dependentChildren = setOf(replayScheduler.dominoTile, publisher.dominoTile, groups.dominoTile, members.dominoTile),
        managedChildren = setOf(replayScheduler.dominoTile, publisher.dominoTile)
    )

    data class SessionMessageReplay(
        val message: Any,
        val sessionId: String,
        val source: LinkManagerInternalTypes.HoldingIdentity,
        val dest: LinkManagerInternalTypes.HoldingIdentity,
        val sentSessionMessageCallback: (counterparties: SessionManager.SessionCounterparties, sessionId: String) -> Unit
    )

    fun addMessageForReplay(
        uniqueId: String,
        messageReplay: SessionMessageReplay,
        counterparties: SessionManager.SessionCounterparties
    ) {
        dominoTile.withLifecycleLock {
            if (!isRunning) {
                throw IllegalStateException("A message was added for replay before the InMemorySessionReplayer was started.")
            }
            replayScheduler.addForReplay(Instant.now().toEpochMilli(), uniqueId, messageReplay, counterparties)
        }
    }

    fun removeMessageFromReplay(uniqueId: String, counterparties: SessionManager.SessionCounterparties) {
        replayScheduler.removeFromReplay(uniqueId, counterparties)
    }

    fun removeAllMessagesFromReplay() {
        replayScheduler.removeAllMessagesFromReplay()
    }

    private fun replayMessage(
        messageReplay: SessionMessageReplay,
    ) {
        val memberInfo = members.getMemberInfo(messageReplay.dest)
        if (memberInfo == null) {
            logger.warn("Attempted to replay a session negotiation message (type ${messageReplay.message::class.java.simpleName})" +
                " with peer ${messageReplay.dest} which is not in the members map. The message was not replayed.")
            return
        }

        val networkType = groups.getGroupInfo(memberInfo.holdingIdentity.groupId)?.networkType
        if (networkType == null) {
            logger.warn("Attempted to replay a session negotiation message (type ${messageReplay.message::class.java.simpleName}) but" +
                " could not find the network type in the GroupPolicyProvider for group ${memberInfo.holdingIdentity.groupId}." +
                " The message was not replayed.")
            return
        }

        val message = MessageConverter.createLinkOutMessage(messageReplay.message, memberInfo, networkType.toLMNetworkType())
        logger.debug { "Replaying session message ${message.payload.javaClass} for session ${messageReplay.sessionId}." }
        publisher.publish(listOf(Record(LINK_OUT_TOPIC, LinkManager.generateKey(), message)))
        messageReplay.sentSessionMessageCallback(
            SessionManager.SessionCounterparties(messageReplay.source, messageReplay.dest),
            messageReplay.sessionId
        )
    }
}