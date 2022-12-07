package net.corda.crypto.cipher.suite.schemes

/**
 * Represents the result of [java.security.spec.AlgorithmParameterSpec] serialization.
 *
 * @param clazz The fully qualified name of the parameters class which was serialized.
 * @param bytes The byte array containing the result of the serialization.
 */
class SerializedAlgorithmParameterSpec(
    /**
     * he fully qualified name of the parameters class which was serialized.
     */
    val clazz: String,
    /**
     * The byte array containing the result of the serialization.
     */
    val bytes: ByteArray
)