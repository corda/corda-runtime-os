package net.corda.p2p.linkmanager.delivery

import net.corda.configuration.read.ConfigurationReadService
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.lifecycle.domino.logic.util.PublisherWithDominoLogic
import net.corda.membership.grouppolicy.GroupPolicyProvider
import net.corda.membership.lib.exceptions.BadGroupPolicyException
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.p2p.linkmanager.LinkManager
import net.corda.p2p.linkmanager.common.MessageConverter
import net.corda.p2p.linkmanager.grouppolicy.networkType
import net.corda.p2p.linkmanager.membership.lookup
import net.corda.p2p.linkmanager.sessions.SessionManager
import net.corda.schema.Schemas.P2P.LINK_OUT_TOPIC
import net.corda.utilities.debug
import net.corda.utilities.time.Clock
import org.slf4j.LoggerFactory

@Suppress("LongParameterList")
internal class InMemorySessionReplayer(
    publisherFactory: PublisherFactory,
    configurationReaderService: ConfigurationReadService,
    coordinatorFactory: LifecycleCoordinatorFactory,
    messagingConfiguration: SmartConfig,
    private val groupPolicyProvider: GroupPolicyProvider,
    private val membershipGroupReaderProvider: MembershipGroupReaderProvider,
    private val clock: Clock
): LifecycleWithDominoTile {

    companion object {
        private const val MESSAGE_REPLAYER_CLIENT_ID = "session-message-replayer-client"
    }

    private var logger = LoggerFactory.getLogger(this::class.java.name)

    private val publisher = PublisherWithDominoLogic(
        publisherFactory,
        coordinatorFactory,
        PublisherConfig(MESSAGE_REPLAYER_CLIENT_ID, false),
        messagingConfiguration
    )

    private val replayScheduler = ReplayScheduler<SessionManager.SessionCounterparties, SessionMessageReplay>(
        coordinatorFactory,
        configurationReaderService,
        false,
        ::replayMessage,
        clock = clock
    )

    override val dominoTile = ComplexDominoTile(
        this::class.java.simpleName,
        coordinatorFactory,
        dependentChildren = setOf(
            replayScheduler.dominoTile.coordinatorName,
            publisher.dominoTile.coordinatorName,
            LifecycleCoordinatorName.forComponent<GroupPolicyProvider>(),
            LifecycleCoordinatorName.forComponent<MembershipGroupReaderProvider>(),
        ),
        managedChildren = setOf(replayScheduler.dominoTile.toNamedLifecycle(), publisher.dominoTile.toNamedLifecycle())
    )

    data class SessionMessageReplay(
        val message: Any,
        val sessionId: String,
        val sessionCounterparties: SessionManager.SessionCounterparties,
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
            replayScheduler.addForReplay(clock.instant().toEpochMilli(), uniqueId, messageReplay, counterparties)
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
        messageId: MessageId
    ) {
        val destinationMemberInfo = membershipGroupReaderProvider.lookup(
            messageReplay.sessionCounterparties.ourId,
            messageReplay.sessionCounterparties.counterpartyId,
            messageReplay.sessionCounterparties.status
        )
        if (destinationMemberInfo == null) {
            logger.warn(
                "Attempted to replay a session negotiation message (type " +
                        "${messageReplay.message::class.java.simpleName})" +
                        " for session with ID ${messageReplay.sessionId}" +
                        " between ${messageReplay.sessionCounterparties.ourId}" +
                        " and peer ${messageReplay.sessionCounterparties.counterpartyId} with " +
                        "status ${messageReplay.sessionCounterparties.status} " +
                        "which is not in the members map. The message was not replayed."
            )
            return
        }

        if (destinationMemberInfo.serial != messageReplay.sessionCounterparties.serial) {
            logger.warn(
                "Attempted to replay a session negotiation message (type " +
                        "${messageReplay.message::class.java.simpleName})" +
                        " for session with ID ${messageReplay.sessionId}" +
                        " between ${messageReplay.sessionCounterparties.ourId}" +
                        " and peer ${messageReplay.sessionCounterparties.counterpartyId} with " +
                        "serial ${messageReplay.sessionCounterparties.serial} " +
                        "which is not in the members map. Member was found but with serial " +
                        "${destinationMemberInfo.serial}. " +
                        "The message was not replayed."
            )
            if(destinationMemberInfo.serial > messageReplay.sessionCounterparties.serial) {
                logger.warn(
                    "Replay message is attempting to use ${messageReplay.sessionCounterparties.counterpartyId}" +
                            " with serial number ${messageReplay.sessionCounterparties.serial}, but the current serial " +
                            "number ${destinationMemberInfo.serial} is higher so message will never send. " +
                            "Dropping message replay."
                )
                removeMessageFromReplay(messageId, messageReplay.sessionCounterparties)
            }
            return
        }

        val networkType = try {
            groupPolicyProvider.getP2PParameters(messageReplay.sessionCounterparties.ourId)?.networkType
        } catch (except: BadGroupPolicyException) {
            logger.warn("Attempted to replay a session negotiation message (type ${messageReplay.message::class.java.simpleName}) but" +
                " the group policy data is unavailable or cannot be parsed for ${messageReplay.sessionCounterparties.ourId}. Error:" +
                " ${except.message}. The message was not replayed.")
            return
        }
        if (networkType == null) {
            logger.warn("Attempted to replay a session negotiation message (type ${messageReplay.message::class.java.simpleName}) but" +
                " could not find the network type in the GroupPolicyProvider for ${messageReplay.sessionCounterparties.ourId}." +
                " The message was not replayed.")
            return
        }

        val message = MessageConverter.createLinkOutMessage(
            messageReplay.message,
            messageReplay.sessionCounterparties.ourId,
            destinationMemberInfo,
            networkType
        )
        logger.debug { "Replaying session message ${message.payload.javaClass} for session ${messageReplay.sessionId}." }
        publisher.publish(listOf(Record(LINK_OUT_TOPIC, LinkManager.generateKey(), message)))
        messageReplay.sentSessionMessageCallback(
            messageReplay.sessionCounterparties,
            messageReplay.sessionId
        )
    }
}
