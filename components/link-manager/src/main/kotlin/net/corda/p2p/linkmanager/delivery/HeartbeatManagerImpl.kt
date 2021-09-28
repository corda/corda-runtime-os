package net.corda.p2p.linkmanager.delivery

import net.corda.lifecycle.Lifecycle
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.p2p.HeartbeatMessage
import net.corda.p2p.app.HoldingIdentity
import net.corda.p2p.crypto.protocol.api.Session
import net.corda.p2p.linkmanager.LinkManager.Companion.generateKey
import net.corda.p2p.linkmanager.LinkManagerNetworkMap
import net.corda.p2p.linkmanager.messaging.MessageConverter.Companion.linkOutMessageFromHeartbeat
import net.corda.p2p.linkmanager.sessions.SessionManager.SessionKey
import net.corda.p2p.schema.Schema
import net.corda.v5.base.concurrent.getOrThrow
import net.corda.v5.base.util.contextLogger
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

class HeartbeatManagerImpl(
    publisherFactory: PublisherFactory,
    private val networkMap: LinkManagerNetworkMap,
    private val heartbeatPeriod: Duration,
    private val sessionTimeout: Duration,
) : Lifecycle, HeartbeatManager {

    companion object {
        const val HEARTBEAT_MANAGER_CLIENT_ID = "heartbeat-manager-client"
        private val logger = contextLogger()
    }

    @Volatile
    private var running = false
    private val startStopLock = ReentrantReadWriteLock()
    private lateinit var executorService: ScheduledExecutorService

    private val sessionKeys = ConcurrentHashMap<String, SessionKey>()
    private val trackedSessions = ConcurrentHashMap<SessionKey, TrackedSession>()

    private val config = PublisherConfig(HEARTBEAT_MANAGER_CLIENT_ID, 1)
    private val publisher = publisherFactory.createPublisher(config)

    /**
     * For each Session we track the following.
     * [lastSendTimestamp]: The last time we sent a message using this Session.
     * [lastAckTimestamp]: The last time we acknowledged a message sent using this Session.
     * [sentMessageIds]: The messageId's of each sent message.
     * [nextSequenceNumber]: The next sequence number to add to the Heartbeat Message for debug purposes.
     */
    class TrackedSession(
        var lastSendTimestamp: Long,
        var lastAckTimestamp: Long,
        val sentMessageIds: MutableSet<String> = mutableSetOf(),
        var nextSequenceNumber: Long = 1L,
        var sendingHeartbeats: Boolean = false
    )

    override val isRunning: Boolean
        get() = running

    override fun start() {
        startStopLock.write {
            if (!running) {
                executorService = Executors.newSingleThreadScheduledExecutor()
                publisher.start()
                running = true
            }
        }
    }

    override fun stop() {
        startStopLock.write {
            if (running) {
                executorService.shutdown()
                publisher.close()
                running = false
            }
        }
    }

    override fun sessionMessageSent(
        messageId: String,
        key: SessionKey,
        sessionId: String,
        destroySession: (key: SessionKey, sessionId: String) -> Any
    ) {
        startStopLock.read {
            if (!running) {
                throw IllegalStateException("A session message was added before the HeartbeatManager was started.")
            }
            trackedSessions.compute(key) { _, initialTrackedSession ->
                return@compute if (initialTrackedSession != null) {
                    initialTrackedSession.lastSendTimestamp = timeStamp()
                    initialTrackedSession.sentMessageIds.add(messageId)
                    initialTrackedSession
                } else {
                    executorService.schedule({ sessionTimeout(key, sessionId, destroySession) }, sessionTimeout.toMillis(), TimeUnit.MILLISECONDS)
                    val trackedSession = TrackedSession(timeStamp(), timeStamp())
                    trackedSession.sentMessageIds.add(messageId)
                    trackedSession
                }
            }
        }
    }

    override fun messageSent(messageId: String, key: SessionKey, session: Session) {
        startStopLock.read {
            if (!running) {
                throw IllegalStateException("A message was sent before the HeartbeatManager was started.")
            }
            val trackedSession = trackedSessions.computeIfPresent(key) { _, trackedSession ->
                trackedSession.lastSendTimestamp = timeStamp()
                trackedSession.sentMessageIds.add(messageId)
                if (!trackedSession.sendingHeartbeats) {
                    executorService.schedule({ sendHeartbeat(key, session) }, heartbeatPeriod.toMillis(), TimeUnit.MILLISECONDS)
                    trackedSession.sendingHeartbeats = true
                }
                trackedSession
            }
            if (trackedSession != null) {
                sessionKeys[messageId] = key
            } else {
                throw IllegalStateException("A message with ID $messageId, was sent on a session between ${key.ourId} and " +
                    "${key.responderId}}, which is not tracked.")
            }
        }
    }

    override fun messageAcknowledged(messageId: String) {
        startStopLock.read {
            if (!running) {
                throw IllegalStateException("A message was acknowledged before the HeartbeatManager was started.")
            }
            val sessionKey = sessionKeys[messageId] ?: return
            val sessionInfo = trackedSessions[sessionKey] ?: return
            logger.trace("Message acknowledged with Id $messageId.")
            sessionInfo.lastAckTimestamp = timeStamp()
            sessionInfo.sentMessageIds.remove(messageId)
        }
    }

    private fun sessionTimeout(key: SessionKey, sessionId: String, destroySession: (key: SessionKey, sessionId: String) -> Any) {
        val sessionInfo = trackedSessions[key] ?: return
        val timeSinceLastAck = timeStamp() - sessionInfo.lastAckTimestamp
        if (timeSinceLastAck >= sessionTimeout.toMillis()) {
            destroySession(key, sessionId)
            for (messageId in sessionInfo.sentMessageIds) {
                sessionKeys.remove(messageId)
            }
            trackedSessions.remove(key)
        } else {
            executorService.schedule(
                {sessionTimeout(key, sessionId, destroySession)},
                sessionTimeout.toMillis() - timeSinceLastAck,
                TimeUnit.MILLISECONDS
            )
        }
    }

    private fun sendHeartbeat(sessionKey: SessionKey, session: Session) {
        val sessionInfo = trackedSessions[sessionKey] ?: return
        val timeSinceLastAck = timeStamp() - sessionInfo.lastAckTimestamp
        val timeSinceLastSend = timeStamp() - sessionInfo.lastSendTimestamp

        if (timeSinceLastAck >= sessionTimeout.toMillis()) return
        if (timeSinceLastSend >= heartbeatPeriod.toMillis()) {
            logger.trace("Sending heartbeat message between ${sessionKey.ourId} (our Identity) and ${sessionKey.responderId}.")
            val heartBeatMessageId = generateKey()
            sessionInfo.sentMessageIds.add(heartBeatMessageId)
            sessionKeys[heartBeatMessageId] = sessionKey
            @Suppress("TooGenericExceptionCaught")
            try {
                sendHeartbeatMessage(
                    heartBeatMessageId,
                    sessionKey.ourId.toHoldingIdentity(),
                    sessionKey.responderId.toHoldingIdentity(),
                    session,
                    sessionInfo.nextSequenceNumber++
                )
            } catch (exception: Exception) {
                logger.error("An exception was thrown when sending a heartbeat message. The task will be retried again in" +
                    " ${sessionTimeout.toMillis()} ms.\nException:", exception)
            }
            executorService.schedule({ sendHeartbeat(sessionKey, session) }, heartbeatPeriod.toMillis(), TimeUnit.MILLISECONDS)
        } else {
            executorService.schedule({ sendHeartbeat(sessionKey, session) }, heartbeatPeriod.toMillis() - timeSinceLastSend, TimeUnit.MILLISECONDS)
        }
    }

    private fun sendHeartbeatMessage(
        messageId: String,
        source: HoldingIdentity,
        dest: HoldingIdentity,
        session: Session,
        sequenceNumber: Long
    ) {
        val heartbeatMessage = HeartbeatMessage(dest, source, messageId, sequenceNumber)
        val future = publisher.publish(
            listOf(
                Record(Schema.LINK_OUT_TOPIC, messageId, linkOutMessageFromHeartbeat(source, dest, heartbeatMessage, session, networkMap))
            )
        )
        future.single().getOrThrow()
    }

    private fun timeStamp(): Long {
        return Instant.now().toEpochMilli()
    }
}