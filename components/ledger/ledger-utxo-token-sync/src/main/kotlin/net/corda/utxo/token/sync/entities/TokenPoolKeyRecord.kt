package net.corda.utxo.token.sync.entities

import java.io.Serializable
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table

@Entity
@Table(name = "utxo_transaction_output")
class TokenPoolKeyRecord(
    @Id
    @Column(name = "token_type", nullable = false)
    val tokenType: String,

    @Id
    @Column(name = "token_issuer_hash", nullable = false)
    val issuerHash: String,

    @Id
    @Column(name = "token_notary_x500_name", nullable = false)
    val notaryX500Name: String,

    @Id
    @Column(name = "token_symbol", nullable = false)
    val symbol: String
):Serializable {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TokenPoolKeyRecord

        if (tokenType != other.tokenType) return false
        if (issuerHash != other.issuerHash) return false
        if (notaryX500Name != other.notaryX500Name) return false
        if (symbol != other.symbol) return false

        return true
    }

    override fun hashCode(): Int {
        var result = tokenType.hashCode()
        result = 31 * result + issuerHash.hashCode()
        result = 31 * result + notaryX500Name.hashCode()
        result = 31 * result + symbol.hashCode()
        return result
    }
}

