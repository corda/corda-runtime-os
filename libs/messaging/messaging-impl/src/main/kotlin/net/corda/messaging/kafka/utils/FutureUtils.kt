package net.corda.messaging.kafka.utils

import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

fun CompletableFuture<*>.tryGetResult(timeout: Long, cancelOnTimeout: Boolean = false): Any? {
    return try {
        get(timeout, TimeUnit.MILLISECONDS)
    } catch (ex: Exception) {
        return handleFutureException(ex, this, cancelOnTimeout)
    }
}

fun CompletableFuture<*>.tryGetResult(): Any? {
    return try {
        get()
    } catch (ex: Exception) {
        return handleFutureException(ex, this)
    }
}

@Suppress("ThrowsCount")
private fun handleFutureException(ex: Exception, future: CompletableFuture<*>, cancelOnTimeout: Boolean = false): Any? {
    when (ex) {
        is TimeoutException -> {
            if (cancelOnTimeout) {
                future.cancel(true)
            }
            return null
        }
        is CancellationException -> {
            return null
        }
        is ExecutionException -> {
            //get the cause exception thrown if available
            throw ex.cause ?: throw CordaMessageAPIIntermittentException("Future failed to execute", ex)
        }
        else -> {
            throw CordaMessageAPIIntermittentException("Future failed to execute", ex)
        }
    }
}
