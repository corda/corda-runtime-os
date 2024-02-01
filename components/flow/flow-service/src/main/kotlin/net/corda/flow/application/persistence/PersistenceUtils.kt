package net.corda.flow.application.persistence

import net.corda.v5.application.persistence.CordaPersistenceException
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import java.nio.ByteBuffer

/**
 * Catch a [CordaRuntimeException] thrown by [function] and rethrow as a [CordaPersistenceException].
 *
 * @param function The function to execute.
 * @param T The type to return.
 *
 * @return [T]
 *
 * @throws CordaPersistenceException When a [CordaRuntimeException] is thrown, it is caught and rethrown as a
 * [CordaPersistenceException].
 */
@Suspendable
inline fun <T> wrapWithPersistenceException(function: () -> T): T {
    return try {
        function()
    } catch (e: CordaRuntimeException) {
        throw CordaPersistenceException(e.message ?: "Exception occurred when executing persistence operation", e)
    }
}


fun List<ByteArray>.toByteBuffers() = this.map { ByteBuffer.wrap(it) }
fun List<ByteBuffer>.toByteArrays() = this.map { it.array()}

fun Map<String, ByteArray?>.toByteBuffers() = this.mapValues { value -> value.value?.let { ByteBuffer.wrap(value.value) }  }

fun Map<String, ByteBuffer?>.toByteArrays() = this.mapValues { value -> value.value?.array() }