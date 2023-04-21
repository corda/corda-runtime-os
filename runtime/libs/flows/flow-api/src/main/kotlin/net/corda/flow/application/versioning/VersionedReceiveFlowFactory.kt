package net.corda.flow.application.versioning

import net.corda.v5.application.flows.SubFlow
import net.corda.v5.application.messaging.FlowSession

/**
 * [VersionedReceiveFlowFactory] creates a versioned [SubFlow] on the receiving side of a session.
 *
 * Example usage:
 *
 * ```kotlin
 * class UtxoReceiveFinalityFlowVersionedFlowFactory(
 *     private val validator: UtxoTransactionValidator
 * ) : VersionedReceiveFlowFactory<UtxoSignedTransaction> {
 *
 *     override val versionedInstanceOf: Class<UtxoReceiveFinalityFlow> = UtxoReceiveFinalityFlow::class.java
 *
 *     override fun create(version: Int, session: FlowSession): SubFlow<UtxoSignedTransaction> {
 *         return when {
 *             version >= 10 -> UtxoReceiveFinalityFlowV2(session, validator)
 *             version >= 1 -> UtxoReceiveFinalityFlowV1(session, validator)
 *             else -> throw IllegalArgumentException()
 *         }
 *     }
 * }
 * ```
 */
interface VersionedReceiveFlowFactory<T> : VersionedFlowFactory<T> {

    /**
     * Creates an instance of a versioned [SubFlow].
     *
     * This method should use the [version] to determine what version of the [SubFlow] to construct.
     *
     * @param version The agreed platform version.
     * @param session A [FlowSession].
     *
     * @return An instance of a versioned [SubFlow].
     *
     * @throws IllegalArgumentException If the [version] is not supported by the factory.
     */
    fun create(version: Int, session: FlowSession): SubFlow<T>
}