@file:JvmName("SerializationUtils")
package net.corda.utilities.serialization

import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.serialization.SerializedBytes

/**
 * Deserializes the input serialized bytes into an object of type [T].
 *
 * @param serializedBytes The [SerializedBytes] to deserialize.
 * @param T The type to deserialize to.
 *
 * @return A new instance of type [T] created from the input [serializedBytes].
 */
inline fun <reified T : Any> SerializationService.deserialize(serializedBytes: SerializedBytes<T>): T {
    return this.deserialize(serializedBytes, T::class.java)
}

/**
 * Deserializes the input serialized bytes into an object of type [T].
 *
 * @param bytes The [ByteArray] to deserialize.
 * @param T The type to deserialize to.
 *
 * @return A new instance of type [T] created from the input [bytes].
 */
inline fun <reified T : Any> SerializationService.deserialize(bytes: ByteArray): T {
    return this.deserialize(bytes, T::class.java)
}


/**
 * Wrap with null error handling
 *
 * @param T The return type expected
 * @param E The exception type to throw
 * @param message The message to pass tot the exception on failure
 * @param exClazz The exception class
 * @param f the block to wrap
 * @return type T
 */
inline fun <reified T: Any, E: Exception>wrapWithNullErrorHandling(message: String, exClazz: Class<E>, f: () -> T?): T = try {
    f()?: throw exClazz.getDeclaredConstructor().newInstance(message)
} catch (ex: CordaRuntimeException) {
    throw exClazz.getDeclaredConstructor().newInstance(message, ex)
} catch (ex: Exception) {
    throw CordaRuntimeException(message, ex)
}