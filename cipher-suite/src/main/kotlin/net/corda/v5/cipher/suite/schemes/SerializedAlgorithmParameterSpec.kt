package net.corda.v5.cipher.suite.schemes

/**
 * Represents the result of [AlgorithmParameterSpec] serialization.
 *
 * @property clazz the fully qualified name of the parameters class which was serialized
 * @property bytes the byte array containing the result of the serialization
 */
class SerializedAlgorithmParameterSpec(
    val clazz: String,
    val bytes: ByteArray
)