package net.corda.flow.application.versioning.impl.sessions

import net.corda.flow.application.sessions.FlowSessionInternal
import net.corda.flow.application.versioning.impl.AgreedVersionAndPayload
import net.corda.v5.application.messaging.FlowSession

/**
 * [VersionSendingFlowSession] is a [FlowSession] that sends version information to the peer side of the session. This sending of
 * version information occurs on the first operation called on the session. The session can already be initiated and have communicated
 * with a peer already, but once a [VersionSendingFlowSession] is created the first operation will send the version information.
 *
 * [VersionSendingFlowSession] delegates to normal [FlowSession] behaviour once the version information has been sent.
 *
 * [VersionSendingFlowSession] is used to improve performance by removing an extra send and receive that would need to occur when executing
 * pairs of versioned flows.
 *
 * @see VersionReceivingFlowSession
 */
interface VersionSendingFlowSession : FlowSessionInternal {

    /**
     * Gets the payload to send over the session.
     *
     * Either the [serializedPayload] or a serialized [AgreedVersionAndPayload] is returned:
     * - When version information has already been sent or [getPayloadToSend] or [getVersioningPayloadToSend] have been called, then
     *   [serializedPayload] is returned.
     * - Otherwise an [AgreedVersionAndPayload] containing [serializedPayload] is returned.
     *
     * @param serializedPayload The [serializedPayload] to return or contain in an [AgreedVersionAndPayload].
     *
     * @return Either returns [serializedPayload] or a serialized [AgreedVersionAndPayload].
     */
    fun getPayloadToSend(serializedPayload: ByteArray): ByteArray

    /**
     * Gets the versioning payload to send over the session.
     *
     * Either `null` or a serialized [AgreedVersionAndPayload] is returned:
     * - When version information has already been sent or [getPayloadToSend] or [getVersioningPayloadToSend] have been called, then `null`
     *   is returned.
     * - Otherwise an [AgreedVersionAndPayload] is returned.
     *
     * @return Either returns `null` or a serialized [AgreedVersionAndPayload].
     */
    fun getVersioningPayloadToSend(): ByteArray?
}