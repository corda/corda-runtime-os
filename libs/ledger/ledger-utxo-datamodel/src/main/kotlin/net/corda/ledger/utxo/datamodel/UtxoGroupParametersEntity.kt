package net.corda.ledger.utxo.datamodel

import java.io.Serializable
import java.time.Instant
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table

@Entity
@Table(name = "utxo_group_parameters")
class UtxoGroupParametersEntity(
    @Id
    @Column(name = "hash", nullable = false, updatable = false)
    var hash: String,

    @Column(name = "parameters")
    var parameters: ByteArray,

    @Column(name = "signature_public_key")
    var signaturePublicKey: ByteArray,

    @Column(name = "signature_content")
    var signatureContent: ByteArray,

    @Column(name = "signature_spec")
    var signatureSpec: String,

    @Column(name = "created", nullable = false)
    var created: Instant
) : Serializable {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UtxoGroupParametersEntity

        if (hash != other.hash) return false

        return true
    }

    override fun hashCode(): Int {
        return hash.hashCode()
    }

    companion object {
        private const val serialVersionUID: Long = -4061537399985645696L
    }
}
