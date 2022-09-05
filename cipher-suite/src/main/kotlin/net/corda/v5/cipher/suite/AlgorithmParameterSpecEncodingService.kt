package net.corda.v5.cipher.suite

import net.corda.v5.cipher.suite.schemes.SerializedAlgorithmParameterSpec
import java.security.spec.AlgorithmParameterSpec

/**
 * Encoding service which can encode and decode signature parameters.
 * The service is required as almost all [AlgorithmParameterSpec] implementations are not serializable.
 */
interface AlgorithmParameterSpecEncodingService {
    /**
     * Serialize the given parameters into the byte array.
     *
     * @throws [IllegalArgumentException] if the serialization is not supported for the params.
     */
    fun serialize(params: AlgorithmParameterSpec): SerializedAlgorithmParameterSpec

    /**
     * Deserialize the given byte array into corresponding parameters.
     *
     * @throws [IllegalArgumentException] if the deserialization is not supported for the params.
     */
    fun deserialize(params: SerializedAlgorithmParameterSpec): AlgorithmParameterSpec
}