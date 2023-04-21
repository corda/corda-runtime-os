package net.corda.flow.application.services

import net.corda.flow.application.versioning.VersionedReceiveFlowFactory
import net.corda.flow.application.versioning.VersionedSendFlowFactory
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable

/**
 * [VersioningService] is used to version system flows.
 *
 * Call [versionedSubFlow] to version an inline system flow. Every [versionedSubFlow] with a [VersionedSendFlowFactory] passed in should
 * have a matching call to [versionedSubFlow] with a [VersionedReceiveFlowFactory] on the other side of a session.
 *
 * Do not call [peekCurrentVersioning], [setCurrentVersioning], [resetCurrentVersioning] as they are part of the versioning protocol rather
 * than API for versioning system flows.
 */
interface VersioningService {

    /**
     * Executes a versioned [SubFlow].
     *
     * @param versionedFlowFactory The [VersionedSendFlowFactory] that constructs an instance of the versioned [SubFlow].
     * @param sessions The [FlowSession]s involved in the versioned [SubFlow].
     * @param R The return type of the versioned [SubFlow].
     *
     * @return The result of the versioned [SubFlow].
     */
    @Suspendable
    fun <R : Any?> versionedSubFlow(versionedFlowFactory: VersionedSendFlowFactory<R>, sessions: List<FlowSession>): R

    /**
     * Executes a versioned [SubFlow].
     *
     * @param versionedFlowFactory The [VersionedReceiveFlowFactory] that constructs an instance of the versioned [SubFlow].
     * @param session The [FlowSession] involved in the versioned [SubFlow].
     * @param R The return type of the versioned [SubFlow].
     *
     * @return The result of the versioned [SubFlow].
     */
    @Suspendable
    fun <R : Any?> versionedSubFlow(versionedFlowFactory: VersionedReceiveFlowFactory<R>, session: FlowSession): R

    /**
     * Gets the current version information from the flow context properties.
     *
     * @return The current version information, `null` otherwise.
     */
    fun peekCurrentVersioning(): Pair<Int, LinkedHashMap<String, Any>>?

    /**
     * Sets the current version information.
     *
     * @param version The agreed platform version to set in the flow context properties.
     */
    fun setCurrentVersioning(version: Int)

    /**
     * Resets the version information.
     */
    fun resetCurrentVersioning()
}