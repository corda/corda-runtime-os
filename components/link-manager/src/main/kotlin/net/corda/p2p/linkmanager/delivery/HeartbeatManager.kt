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
import net.corda.p2p.linkmanager.LinkManagerNetworkMap.Companion.toHoldingIdentity
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
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

class HeartbeatManager(
    publisherFactory: PublisherFactory,
    private val networkMap: LinkManagerNetworkMap,
    private val heartbeatPeriod: Duration,
    private val timeOutPeriods: Int,
) : Lifecycle {

    companion object {
        const val HEARTBEAT_MANAGER_CLIENT_ID = "heartbeat-manager-client"
        private val logger = contextLogger()
    }

    @Volatile
    private var running = false
    private val startStopLock = ReentrantReadWriteLock()
    private lateinit var executorService: ScheduledExecutorService

    private val sessionKeys = ConcurrentHashMap<String, SessionKey>()
    private val trackedSessions = ConcurrentHashMap<SessionKey, SessionTracker>()

    private val sessionNegotiationTimeoutFutures = ConcurrentHashMap<String, ScheduledFuture<*>>()
    private val sessionNegotiationTimeoutTimestamps = ConcurrentHashMap<String, Long>()

    private val config = PublisherConfig(HEARTBEAT_MANAGER_CLIENT_ID, 1)
    private val publisher = publisherFactory.createPublisher(config)

    class SessionTracker(var heartbeatFuture: ScheduledFuture<*>, var lastAckTimestamp: Long) {
        var sentMessageIds = mutableListOf<String>()
        var nextSequenceNumber = 1L
    }


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

    fun sessionMessageAdded(uniqueId: String, destroyPendingSession: (sessionId: String) -> Any) {
        startStopLock.read {
            if (!running) {
                throw IllegalStateException("A session message was added before the HeartbeatManager was started.")
            }
            sessionNegotiationTimeoutTimestamps[uniqueId] = timeStamp() + heartbeatPeriod.toMillis() * timeOutPeriods
            sessionNegotiationTimeoutFutures.computeIfAbsent(uniqueId) {
            executorService.schedule(
                    { timeOutHeartbeat(it, destroyPendingSession) },
                    heartbeatPeriod.toMillis() * timeOutPeriods,
                    TimeUnit.MILLISECONDS
                )
            }
        }
    }

    fun sessionMessageAcknowledged(uniqueId: String) {
        startStopLock.read {
            if (!running) {
                throw IllegalStateException("A session message was acknowledged before the HeartbeatManager was started.")
            }
            sessionNegotiationTimeoutTimestamps[uniqueId] = timeStamp()
            val future = sessionNegotiationTimeoutFutures.remove(uniqueId)
            future?.cancel(false)
        }
    }

    private fun timeOutHeartbeat(sessionId: String, destroyPendingSession: (sessionId: String) -> Any) {
        val timeNow = timeStamp()
        val lastAckTimestamp = sessionNegotiationTimeoutTimestamps[sessionId] ?: return
        if (timeNow - lastAckTimestamp >= timeOutPeriods * heartbeatPeriod.toMillis()) {
            destroyPendingSession(sessionId)
            sessionNegotiationTimeoutFutures.remove(sessionId)
            sessionNegotiationTimeoutTimestamps.remove(sessionId)
        }
        sessionNegotiationTimeoutFutures[sessionId] = executorService.schedule(
            { timeOutHeartbeat(sessionId, destroyPendingSession) },
            heartbeatPeriod.toMillis() - timeNow,
            TimeUnit.MILLISECONDS
        )
    }

    fun messageSent(messageId: String,
                    source: HoldingIdentity,
                    dest: HoldingIdentity,
                    session: Session,
                    destroySession: (sessionKey: SessionKey) -> Any
    ) {
        startStopLock.read {
            if (!running) {
                throw IllegalStateException("A message was sent before the HeartbeatManager was started.")
            }
            val sessionKey = SessionKey(source.toHoldingIdentity(), dest.toHoldingIdentity())
            trackedSessions.computeIfAbsent(sessionKey) {
                val future = executorService.schedule(
                    {sendHeartbeatOrTimeout(sessionKey, session, destroySession)},
                    heartbeatPeriod.toMillis(),
                    TimeUnit.MILLISECONDS
                )
                val tracker = SessionTracker(future, timeStamp())
                tracker.sentMessageIds.add(messageId)
                tracker
            }
            sessionKeys[messageId] = sessionKey
        }
    }

    fun messageAcknowledged(messageId: String, session: Session, destroySession: (sessionKey: SessionKey) -> Any) {
        startStopLock.read {
            if (!running) {
                throw IllegalStateException("A message was acknowledged before the HeartbeatManager was started.")
            }
            val sessionKey = sessionKeys[messageId] ?: return
            val sessionInfo = trackedSessions[sessionKey] ?: return
            logger.trace("Message acknowledged with Id $messageId.")
            sessionInfo.lastAckTimestamp = timeStamp()
            sessionInfo.heartbeatFuture.cancel(false)
            sessionInfo.heartbeatFuture = executorService.schedule(
                { sendHeartbeatOrTimeout(sessionKey, session, destroySession) },
                heartbeatPeriod.toMillis(),
                TimeUnit.MILLISECONDS
            )
            sessionInfo.sentMessageIds.remove(messageId)
        }
    }

    private fun sendHeartbeatOrTimeout(
        sessionKey: SessionKey,
        session: Session,
        destroySession: (sessionKey: SessionKey) -> Any,
    ) {
        val sessionInfo = trackedSessions[sessionKey] ?: return
        val timeNow = timeStamp()
        if (timeNow - sessionInfo.lastAckTimestamp >= timeOutPeriods * heartbeatPeriod.toMillis()) {
            logger.info("Session between ${sessionKey.ourId} (our Identity) and ${sessionKey.responderId} timed out.")
            destroySession(sessionKey)
            for (messageId in sessionInfo.sentMessageIds) {
                sessionKeys.remove(messageId)
            }
            trackedSessions.remove(sessionKey)
        } else if (timeNow - sessionInfo.lastAckTimestamp >= heartbeatPeriod.toMillis()) {
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
                    " ${heartbeatPeriod.toMillis()} ms.\nException:", exception)
            }

            sessionInfo.heartbeatFuture = executorService.schedule(
                { sendHeartbeatOrTimeout(sessionKey, session, destroySession) },
                heartbeatPeriod.toMillis(),
                TimeUnit.MILLISECONDS
            )
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