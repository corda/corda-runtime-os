package net.corda.flow.application.versioning.impl.sessions

import net.corda.flow.application.sessions.FlowSessionInternal
import net.corda.flow.application.versioning.impl.AgreedVersion
import net.corda.flow.application.versioning.impl.AgreedVersionAndPayload
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.Suspendable
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class VersionSendingFlowSessionImpl(
    private val version: Int,
    private val additionalContext: LinkedHashMap<String, Any>,
    private val delegate: FlowSessionInternal,
    private val serializationService: SerializationService
) : VersionSendingFlowSession, FlowSessionInternal by delegate {

    private companion object {
        val log: Logger = LoggerFactory.getLogger(VersionSendingFlowSessionImpl::class.java)
    }

    private var hasSentInitialPayload = false

    @Suspendable
    override fun <R : Any> sendAndReceive(receiveType: Class<R>, payload: Any): R {
        return delegate.sendAndReceive(receiveType, getPayloadToSend(payload))
    }

    @Suspendable
    override fun send(payload: Any) {
        return delegate.send(getPayloadToSend(payload))
    }

    @Suspendable
    override fun <R : Any> receive(receiveType: Class<R>): R {
        return if (hasSentInitialPayload) {
            delegate.receive(receiveType)
        } else {
            /*
            If the session does a receive as its first operation, then perform a separate send to tell the peer the flow version to use.
            This sequence of events is not optimized like doing a send first; however, this sequence is unlikely to occur in practice, but
            covering it could prevent weird bugs in the future.
             */
            val agreedVersion = AgreedVersion(version, additionalContext)
            if (log.isTraceEnabled) {
                log.trace(
                    "Session ${getSessionId()} is sending versioning information to peer. Sending $agreedVersion without an initial payload"
                )
            }
            val received = delegate.sendAndReceive(
                receiveType,
                AgreedVersionAndPayload(agreedVersion, serializedPayload = null)
            )
            hasSentInitialPayload = true
            received
        }
    }

    @Suspendable
    override fun close() {
        if (hasSentInitialPayload) {
            delegate.close()
        } else {
            /*
           If the session does a close as its first operation, then perform a separate send to tell the peer the flow version to use.
           This sequence of events is not optimized like doing a send first; however, this sequence is unlikely to occur in practice, but
           covering it could prevent weird bugs in the future.
            */
            val agreedVersion = AgreedVersion(version, additionalContext)
            if (log.isTraceEnabled) {
                log.trace(
                    "Session ${getSessionId()} is sending versioning information to peer. Sending $agreedVersion without an initial payload"
                )
            }
            hasSentInitialPayload = true
            delegate.send(AgreedVersionAndPayload(agreedVersion, serializedPayload = null))
            delegate.close()
        }
    }

    override fun getPayloadToSend(serializedPayload: ByteArray): ByteArray {
        return if (hasSentInitialPayload) {
            serializedPayload
        } else {
            val agreedVersion = AgreedVersion(version, additionalContext)
            if (log.isTraceEnabled) {
                log.trace(
                    "Session ${getSessionId()} is sending versioning information to peer. Sending $agreedVersion with an initial " +
                            "serialized payload of $serializedPayload"
                )
            }
            hasSentInitialPayload = true
            serializationService.serialize(AgreedVersionAndPayload(agreedVersion, serializedPayload)).bytes
        }
    }

    override fun getVersioningPayloadToSend(): ByteArray? {
        return if (hasSentInitialPayload) {
            null
        } else {
            val agreedVersion = AgreedVersion(version, additionalContext)
            if (log.isTraceEnabled) {
                log.trace(
                    "Session ${getSessionId()} is sending versioning information to peer. Sending $agreedVersion without an initial payload"
                )
            }
            hasSentInitialPayload = true
            serializationService.serialize(AgreedVersionAndPayload(agreedVersion, serializedPayload = null)).bytes
        }
    }

    private fun getPayloadToSend(payload: Any): Any {
        return if (hasSentInitialPayload) {
            payload
        } else {
            val agreedVersion = AgreedVersion(version, additionalContext)
            if (log.isTraceEnabled) {
                log.trace(
                    "Session ${getSessionId()} is sending versioning information to peer. Sending $agreedVersion with an initial " +
                            "payload of $payload"
                )
            }
            hasSentInitialPayload = true
            AgreedVersionAndPayload(agreedVersion, serializationService.serialize(payload).bytes)
        }
    }

    override fun equals(other: Any?): Boolean {
        return delegate == other
    }

    override fun hashCode(): Int = delegate.hashCode()

    override fun toString(): String {
        return "VersionSendingFlowSessionImpl(version=$version, additionalContext=$additionalContext, delegate=$delegate, " +
                "hasSentInitialPayload=$hasSentInitialPayload)"
    }


}