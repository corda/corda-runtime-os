package net.corda.utilities

val Throwable.rootCause: Throwable get() = cause?.rootCause ?: this
val Throwable.rootMessage: String?
    get() {
        var message = this.message
        var throwable = cause
        while (throwable != null) {
            if (throwable.message != null) {
                message = throwable.message
            }
            throwable = throwable.cause
        }
        return message
    }
