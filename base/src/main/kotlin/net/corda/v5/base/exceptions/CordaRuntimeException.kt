package net.corda.v5.base.exceptions

import java.util.Arrays
import java.util.Objects

/**
 * Base class for all exceptions used for runtime error conditions in Corda.
 *
 * This is the exception class that is used to throw and handle all exceptions you could
 * encounter at runtime in a flow. This class and subclasses can be serialized by Corda
 * so are safe to throw in flows.
 *
 * @constructor Constructor used to wrap any execption in a safe way, taking the original exception class name,
 * message and causes as parameters. This can wrap third party exceptions that cannot be serialized.
 */
open class CordaRuntimeException(
    override var originalExceptionClassName: String?,
    private var _message: String?,
    private var _cause: Throwable?
) : RuntimeException(null, null, true, true), CordaThrowable {

    /**
     * Constructor with just a message and a cause, for rethrowing exceptions that can be serialized.
     */
    constructor(message: String?, cause: Throwable?) : this(null, message, cause)

    /**
     * Constructor with just a message (creating a fresh execption).
     */
    constructor(message: String?) : this(null, message, null)

    override val message: String?
        get() = if (originalExceptionClassName == null) originalMessage else {
            if (originalMessage == null) "$originalExceptionClassName" else "$originalExceptionClassName: $originalMessage"
        }

    override val cause: Throwable?
        get() = _cause ?: super.cause

    override fun setMessage(message: String?) {
        _message = message
    }

    override fun setCause(cause: Throwable?) {
        _cause = cause
    }

    override fun addSuppressed(suppressed: Array<Throwable>) {
        for (suppress in suppressed) {
            addSuppressed(suppress)
        }
    }

    override val originalMessage: String?
        get() = _message

    override fun hashCode(): Int {
        return Arrays.deepHashCode(stackTrace) xor Objects.hash(originalExceptionClassName, originalMessage)
    }

    override fun equals(other: Any?): Boolean {
        return other is CordaRuntimeException &&
                originalExceptionClassName == other.originalExceptionClassName &&
                message == other.message &&
                cause == other.cause &&
                Arrays.equals(stackTrace, other.stackTrace) &&
                Arrays.equals(suppressed, other.suppressed)
    }
}