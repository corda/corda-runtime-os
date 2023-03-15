package net.corda.flow.application.versioning.impl.sessions

import net.corda.flow.application.sessions.FlowSessionInternal
import net.corda.utilities.reflection.castIfPossible
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException

class VersionReceivingFlowSessionImpl(
    private val initialSerializedPayload: ByteArray,
    private val delegate: FlowSessionInternal,
    private val serializationService: SerializationService
) : VersionReceivingFlowSession, FlowSessionInternal by delegate {

    private var hasReceivedInitialPayload = false

    @Suspendable
    override fun <R : Any> sendAndReceive(receiveType: Class<R>, payload: Any): R {
        return if (hasReceivedInitialPayload) {
            delegate.sendAndReceive(receiveType, payload)
        } else {
            delegate.send(payload)
            hasReceivedInitialPayload = true
            getInitialPayload(receiveType)
        }
    }

    @Suspendable
    override fun <R : Any> receive(receiveType: Class<R>): R {
        return if (hasReceivedInitialPayload) {
            delegate.receive(receiveType)
        } else {
            hasReceivedInitialPayload = true
            return getInitialPayload(receiveType)
        }
    }

    override fun <R: Any> getInitialPayloadIfNotAlreadyReceived(receiveType: Class<R>): R? {
        return if (!hasReceivedInitialPayload) {
            hasReceivedInitialPayload = true
            getInitialPayload(receiveType)
        } else {
            null
        }
    }

    private fun <R: Any> getInitialPayload(receiveType: Class<R>): R {
        val payload = serializationService.deserialize(initialSerializedPayload, receiveType)
        return receiveType.castIfPossible(payload) ?: throw CordaRuntimeException(
            "Expecting to receive a $receiveType but received a ${initialSerializedPayload::class.java.name} instead, payload: " +
                    "($initialSerializedPayload)"
        )
    }

    override fun equals(other: Any?): Boolean {
        return delegate == other
    }

    override fun hashCode(): Int = delegate.hashCode()

    override fun toString(): String {
        return "VersionReceivingFlowSessionImpl(initialSerializedPayload=${initialSerializedPayload.contentToString()}, " +
                "delegate=$delegate, hasReceivedInitialPayload=$hasReceivedInitialPayload)"
    }


}