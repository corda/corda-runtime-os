package net.corda.ledger.libs.persistence.utxo

data class SignatureWithKey(val publicKey: ByteArray, val bytes: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SignatureWithKey

        if (!publicKey.contentEquals(other.publicKey)) return false
        if (!bytes.contentEquals(other.bytes)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = publicKey.contentHashCode()
        result = 31 * result + bytes.contentHashCode()
        return result
    }
}