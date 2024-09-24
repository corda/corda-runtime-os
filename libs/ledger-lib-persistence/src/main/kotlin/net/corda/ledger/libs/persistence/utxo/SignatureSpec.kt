package net.corda.ledger.libs.persistence.utxo

data class SignatureSpec(val signatureName: String, val customDigestName: String?, val params: ByteArray?) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as net.corda.ledger.libs.persistence.utxo.SignatureSpec

        if (signatureName != other.signatureName) return false
        if (customDigestName != other.customDigestName) return false
        if (params != null) {
            if (other.params == null) return false
            if (!params.contentEquals(other.params)) return false
        } else if (other.params != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = signatureName.hashCode()
        result = 31 * result + (customDigestName?.hashCode() ?: 0)
        result = 31 * result + (params?.contentHashCode() ?: 0)
        return result
    }
}