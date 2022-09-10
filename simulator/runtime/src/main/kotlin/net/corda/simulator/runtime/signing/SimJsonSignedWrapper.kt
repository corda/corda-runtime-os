package net.corda.simulator.runtime.signing

data class SimJsonSignedWrapper(
    val clearData: ByteArray,
    val pemEncodedPublicKey: String,
    val signatureSpecName : String,
    val keyParameters: KeyParameters
    ) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SimJsonSignedWrapper

        if (!clearData.contentEquals(other.clearData)) return false
        if (pemEncodedPublicKey != other.pemEncodedPublicKey) return false
        if (signatureSpecName != other.signatureSpecName) return false
        if (keyParameters != other.keyParameters) return false

        return true
    }

    override fun hashCode(): Int {
        var result = clearData.contentHashCode()
        result = 31 * result + pemEncodedPublicKey.hashCode()
        result = 31 * result + signatureSpecName.hashCode()
        result = 31 * result + keyParameters.hashCode()
        return result
    }

}
