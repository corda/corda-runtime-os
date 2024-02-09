package net.corda.p2p.linkmanager.sessions

import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/***
 * The [DeadSessionMonitor] class is responsible for ensuring messages for sessions that appear to be dead are deleted.
 * The deletion will trigger a new session to be created on receipt of a new data message.
 *
 * The class uses a simple timeout strategy to identify when a session on the receiving side might be unavailable.
 * The timeout is measured as the time between a data message being sent and an ack received. The data message used will
 * be the first received for a session or the first received since the last ack.
 *
 * The monitor can also receive a session error signal from the receiving side, this will delete the session from the
 * monitor.
 */
internal class DeadSessionMonitor(
    private val scheduledExecutorService: ScheduledExecutorService,
    private val sessionCache: SessionCache,
) {
    companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val scheduledSessionDeletions = ConcurrentHashMap<String, ScheduledFuture<*>>()

    @Volatile
    private var sessionInactivityLimitSeconds: Long? = null

    fun onConfigChange(sessionInactivityLimitSeconds: Long) {
        this.sessionInactivityLimitSeconds = sessionInactivityLimitSeconds
    }

    fun messageSent(sessionId: String) {
        scheduledSessionDeletions.compute(sessionId) { _, existing -> existing ?: createScheduledDeletion(sessionId) }
    }

    fun ackReceived(sessionId: String) {
        cancelScheduledDeletion(sessionId)
    }

    fun sessionRemoved(sessionId: String) {
        cancelScheduledDeletion(sessionId)
    }

    private fun cancelScheduledDeletion(sessionId: String) {
        scheduledSessionDeletions.remove(sessionId)?.cancel(false)
    }

    private fun createScheduledDeletion(sessionId: String): ScheduledFuture<*> {
        val delay = checkNotNull(sessionInactivityLimitSeconds) {
            "Data messages sent or acknowledged before configuration has been read."
        }

        return scheduledExecutorService.schedule(
            {
                log.warn(
                    "No acks received for session '$sessionId' after '$delay' seconds. the session is " +
                        "assumed dead and will be recreated.",
                )
                sessionCache.deleteBySessionId(sessionId)
            },
            delay,
            TimeUnit.SECONDS,
        )
    }
}
