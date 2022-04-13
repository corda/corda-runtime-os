package net.corda.v5.services

import net.corda.v5.base.annotations.DoNotImplement

/**
 * This event is dispatched when State Machine is fully started.
 *
 * If a handler for this event throws [CordaServiceCriticalFailureException] - this is the way to flag that it will not make
 * sense for Corda node to continue its operation. The lifecycle events dispatcher will endeavor to terminate node's JVM as soon
 * as practically possible.
 */
@DoNotImplement
interface StateMachineStarted : ServiceLifecycleEvent

/**
 * Please see [StateMachineStarted] for the purpose of this exception.
 */
class CordaServiceCriticalFailureException(message : String, cause: Throwable?) : RuntimeException(message, cause) {
    constructor(message : String) : this(message, null)
}