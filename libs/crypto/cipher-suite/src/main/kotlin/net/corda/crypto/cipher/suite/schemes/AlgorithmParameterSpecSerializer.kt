package net.corda.crypto.cipher.suite.schemes

import java.security.spec.AlgorithmParameterSpec

/**
 * Custom serializer for [AlgorithmParameterSpec] as most of the implementations are not serializable and throw
 * an exception when trying to use default constructors so the JSON cannot be used as well.
 * The implementations are used by [net.corda.v5.cipher.suite.AlgorithmParameterSpecEncodingService].
 */
interface AlgorithmParameterSpecSerializer<T : AlgorithmParameterSpec> {
    /**
     * Serializes the given parameters into the byte array.
     *
     * @throws [IllegalArgumentException] if the serialization is not supported for the params.
     */
    fun serialize(params: T): ByteArray

    /**
     * Deserializes the given byte array into corresponding parameters.
     *
     * @throws [IllegalArgumentException] if the deserialization is not supported for the params.
     */
    fun deserialize(bytes: ByteArray): T
}