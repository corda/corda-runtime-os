package net.corda.messaging.kafka.utils

import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException


@Suppress("TooGenericExceptionCaught")
fun tryGetFutureResult(future: CompletableFuture<*>, timeout: Long, cancelOnTimeout: Boolean = false): Any? {
    return try {
        future.get(timeout, TimeUnit.MILLISECONDS)
    } catch (ex: Exception) {
        return handleFutureException(ex, future, cancelOnTimeout)
    }
}

@Suppress("TooGenericExceptionCaught")
fun tryGetFutureResult(future: CompletableFuture<*>): Any? {
    return try {
        future.get()
    } catch (ex: Exception) {
        return handleFutureException(ex, future)
    }
}

@Suppress("ThrowsCount")
fun handleFutureException(ex: Exception, future: CompletableFuture<*>, cancelOnTimeout: Boolean = false): Any? {
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
            //get the exception thrown by the processor if available
            throw ex.cause ?: throw CordaMessageAPIIntermittentException("Future failed to execute", ex)
        }
        else -> {
            throw CordaMessageAPIIntermittentException("Future failed to execute", ex)
        }
    }
}
