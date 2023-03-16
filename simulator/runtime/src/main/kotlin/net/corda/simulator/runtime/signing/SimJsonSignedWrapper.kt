package net.corda.simulator.runtime.signing

/**
 * A wrapper around a key that includes information about its signing. This is converted to JSON and used instead
 * of a real signature.
 *
 * @param originalData The original data to be "signed".
 * @param pemEncodedPublicKey The public key that would correspond to the private key for signing in real Corda.
 * @param signatureSpecName The name of the signature spec; only used for verification.
 * @param keyParameters The parameters passed to Simulator when the key was created.
 */
data class SimJsonSignedWrapper(
    val originalData: ByteArray,
    val pemEncodedPublicKey: String,
    val signatureSpecName : String,
    val keyParameters: KeyParameters
    ) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SimJsonSignedWrapper

        if (!originalData.contentEquals(other.originalData)) return false
        if (pemEncodedPublicKey != other.pemEncodedPublicKey) return false
        if (signatureSpecName != other.signatureSpecName) return false
        if (keyParameters != other.keyParameters) return false

        return true
    }

    override fun hashCode(): Int {
        var result = originalData.contentHashCode()
        result = 31 * result + pemEncodedPublicKey.hashCode()
        result = 31 * result + signatureSpecName.hashCode()
        result = 31 * result + keyParameters.hashCode()
        return result
    }

}
