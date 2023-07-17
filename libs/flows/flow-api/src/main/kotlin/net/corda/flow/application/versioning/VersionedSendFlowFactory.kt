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
 *     private val pluggableNotaryClientFlow: Class<PluggableNotaryClientFlow>,
 *     private val serializationService: SerializationService
 * ) : VersionedSendFlowFactory<UtxoSignedTransaction> {
 *
 *     override val versionedInstanceOf: Class<UtxoFinalityFlow> = UtxoFinalityFlow::class.java
 *
 *     override fun create(version: Int, sessions: List<FlowSession>): SubFlow<UtxoSignedTransaction> {
 *         val finalityVersion = when {
 *             version >= CORDA_5_1.value -> UtxoFinalityVersion.V2
 *             version in 1 until CORDA_5_1.value -> UtxoFinalityVersion.V1
 *             else -> throw IllegalArgumentException()
 *         }
 *         return UtxoFinalityFlowV1(transaction, sessions, pluggableNotaryClientFlow, serializationService, finalityVersion)
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