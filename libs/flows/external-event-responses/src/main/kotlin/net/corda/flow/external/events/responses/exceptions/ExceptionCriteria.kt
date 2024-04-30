package net.corda.flow.external.events.responses.exceptions

/**
 * Criteria to evaluate an exception type for responding to an external event.
 *
 * Use this to construct a criteria to evaluate a particular exception. This class will evaluate the top level exception
 * and all causes to see if the criteria is met, and return true if so. This can be used to determine which errors are
 * fatal, transient or platform.
 */
data class ExceptionCriteria<T : Throwable>(val type: Class<T>, val check: (T) -> Boolean = { _ -> true }) {
    fun meetsCriteria(exception: Throwable?): Boolean {
        if (exception == null) {
            return false
        }
        val meetsCriteria = if (type.isAssignableFrom(exception::class.java)) {
            check(type.cast(exception))
        } else {
            false
        }
        return (meetsCriteria || meetsCriteria(exception.cause))
    }
}

inline fun <reified T : Throwable> criteria(
    noinline check: (T) -> Boolean = { _ -> true }
): ExceptionCriteria<T> = ExceptionCriteria(T::class.java, check)
