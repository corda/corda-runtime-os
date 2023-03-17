package net.corda.flow.application.versioning

import net.corda.v5.application.flows.SubFlow
import net.corda.v5.application.messaging.FlowSession

/**
 * [VersionedSendFlowFactory] creates a versioned [SubFlow] on the initiating side of a session.
 *
 * Example usage:
 *
 * ```kotlin
 * class UtxoFinalityFlowVersionedFlowFactory(
 *     private val transaction: UtxoSignedTransactionInternal,
 *     private val pluggableNotaryClientFlow: Class<PluggableNotaryClientFlow>
 * ) : VersionedSendFlowFactory<UtxoSignedTransaction> {
 *
 *     override val versionedInstanceOf: Class<UtxoFinalityFlow> = UtxoFinalityFlow::class.java
 *
 *     override fun create(version: Int, sessions: List<FlowSession>): SubFlow<UtxoSignedTransaction> {
 *         return when {
 *             version >= 10 -> UtxoFinalityFlowV2(transaction, sessions, pluggableNotaryClientFlow)
 *             version >= 1 -> UtxoFinalityFlowV1(transaction, sessions, pluggableNotaryClientFlow)
 *             else -> throw IllegalArgumentException()
 *         }
 *     }
 * }
 * ```
 */
interface VersionedSendFlowFactory<T> : VersionedFlowFactory<T> {

    /**
     * Creates an instance of a versioned [SubFlow].
     *
     * This method should use the [version] to determine what version of the [SubFlow] to construct.
     *
     * @param version The agreed platform version.
     * @param sessions The [FlowSession]s.
     *
     * @return An instance of a versioned [SubFlow].
     *
     * @throws IllegalArgumentException If the [version] is not supported by the factory.
     */
    fun create(version: Int, sessions: List<FlowSession>): SubFlow<T>
}