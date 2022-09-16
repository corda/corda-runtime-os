package net.corda.v5.application.flows

import java.util.UUID
import net.corda.v5.application.crypto.DigitalSignatureVerificationService
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.DoNotImplement
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name

/**
 * [FlowEngine] provides core flow related functionality.
 *
 * Corda provides an instance of [DigitalSignatureVerificationService] to flows via property injection.
 */
@DoNotImplement
interface FlowEngine {

    /**
     * Gets the flow id that identifies this flow.
     *
     * A subFlow shares the same flow id as the flow that invoked it via [FlowEngine.subFlow].
     */
    val flowId: UUID

    /**
     * Gets the [MemberX500Name] of the current virtual node executing the flow.
     */
    val virtualNodeName: MemberX500Name

    /**
     * Gets the context properties of the current flow.
     */
    val flowContextProperties: FlowContextProperties

    /**
     * Executes the given [SubFlow].
     *
     * This function returns once the [SubFlow] completes, returning either:
     *
     * - The result executing of [SubFlow.call].
     * - An exception thrown by [SubFlow.call].
     *
     * Any open [FlowSession]s created within a [SubFlow] annotated with [InitiatingFlow] are sent:
     *
     * - Session close messages after successfully completing the [SubFlow].
     * - Session error messages when an exception is thrown from the [SubFlow].
     *
     * @param subFlow The [SubFlow] to execute.
     * @param R The type returned by [subFlow].
     *
     * @return The result of executing [SubFlow.call].
     *
     * @throws CordaRuntimeException General type of exception thrown by most Corda APIs.
     *
     * @see SubFlow
     */
    @Suspendable
    fun <R> subFlow(subFlow: SubFlow<R>): R
}
