package net.corda.flow.application.versioning.impl.sessions

import net.corda.flow.application.sessions.FlowSessionInternal
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.messaging.FlowSession

/**
 * [VersionReceivingFlowSession] is a [FlowSession] that handles the fact that the first payload received when executing a versioned flow
 * pair is the receival of version information along with the initial payload from the peer versioned flow. This means that the first time
 * a [FlowSession.receive], [FlowSession.sendAndReceive] or the [FlowMessaging] variants are called, rather than suspending a receiving to
 * receive data, it instantly returns the payload it already received as part of the versioning protocol.
 *
 * [VersionReceivingFlowSession] delegates to normal [FlowSession] behaviour once a receiving method has been called.
 *
 * [VersionReceivingFlowSession] is used to improve performance by removing an extra send and receive that would need to occur when
 * executing pairs of versioned flows.
 */
interface VersionReceivingFlowSession : FlowSessionInternal {

    /**
     * Gets the initial payload received during the versioning protocol if a receiving method has not been called, and returns `null`
     * otherwise.
     *
     * @param receiveType The [Class] of [R] to receive.
     *
     * @return An instance of [R] or `null`.
     */
    fun <R : Any> getInitialPayloadIfNotAlreadyReceived(receiveType: Class<R>): R?
}