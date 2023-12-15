package net.corda.ledger.common.flow.flows

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.exceptions.CordaRuntimeException
import java.util.function.Function

/**
 * [Payload] represents a response sent to and received by a session that is either successful or unsuccessful.
 *
 * Using this interface allows flows to branch their logic if expected errors occur in the counterparty flow while restricting the use of
 * exceptions from the platform to represent exceptional behaviour.
 */
@CordaSerializable
sealed class Payload<T> {

    /**
     * Gets the [Payload]'s underlying value if it represents a successful response, or throw an exception if it represents an error.
     *
     * If the [Payload] is a:
     * - [Success] - [Success.value] is retrieved.
     * - [Failure] - An exception with [Failure.message] is thrown.
     *
     * @throws CordaRuntimeException If the [Payload] is a [Failure].
     */
    abstract fun getOrThrow(): T

    /**
     * Gets the [Payload]'s underlying value if it represents a successful response, or throw an exception if it represents an error.
     *
     * If the [Payload] is a:
     * - [Success] - [Success.value] is retrieved.
     * - [Failure] - An exception created in [throwOnFailure] is thrown.
     *
     * Use this overload to throw an exception that is not a [CordaRuntimeException], throw different types of exceptions depending on
     * [Failure.reason] or execute extra logic before throwing an exception.
     *
     * @param throwOnFailure A [Function] that returns a [Throwable] to be thrown by [getOrThrow].
     *
     * @throws CordaRuntimeException If the [Payload] is a [Failure].
     */
    abstract fun getOrThrow(throwOnFailure: Function<Failure<*>, Throwable>): T

    /**
     * [Success] represents a successful response.
     *
     * @property value The underlying payload.
     */
    data class Success<T>(val value: T) : Payload<T>() {

        override fun getOrThrow(): T {
            return value
        }

        override fun getOrThrow(throwOnFailure: Function<Failure<*>, Throwable>): T {
            return value
        }
    }

    /**
     * [Failure] indicates that an expected error occurred.
     *
     * @property message The error message.
     * @property reason The reason for the error. [reason] should refer to a statically defined string that can also be accessed by the
     * flow that received this [Failure].
     */
    data class Failure<T>(val message: String, val reason: String?) : Payload<T>() {

        constructor(message: String) : this(message, reason = null)

        override fun getOrThrow(): T {
            throw CordaRuntimeException(message)
        }

        override fun getOrThrow(throwOnFailure: Function<Failure<*>, Throwable>): T {
            throw throwOnFailure.apply(this)
        }
    }
}
