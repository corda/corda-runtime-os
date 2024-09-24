package net.corda.ledger.libs.persistence.utxo

data class SignedGroupParameters(
    val groupParameters: ByteArray,
    val mgmSignature: SignatureWithKey,
    val mgmSignatureSpec: net.corda.ledger.libs.persistence.utxo.SignatureSpec,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SignedGroupParameters

        if (!groupParameters.contentEquals(other.groupParameters)) return false
        if (mgmSignature != other.mgmSignature) return false
        if (mgmSignatureSpec != other.mgmSignatureSpec) return false

        return true
    }

    override fun hashCode(): Int {
        var result = groupParameters.contentHashCode()
        result = 31 * result + mgmSignature.hashCode()
        result = 31 * result + mgmSignatureSpec.hashCode()
        return result
    }
}