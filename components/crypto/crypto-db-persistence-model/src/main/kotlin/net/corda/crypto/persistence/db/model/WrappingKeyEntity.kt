package net.corda.crypto.persistence.db.model

import net.corda.db.schema.DbSchema
import java.time.Instant
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table

@Entity
@Table(name = DbSchema.CRYPTO_WRAPPING_KEY_TABLE)
class WrappingKeyEntity(
    @Id
    @Column(name = "alias", nullable = false, updatable = false)
    val alias: String,

    @Column(name = "created", nullable = false, updatable = false)
    var created: Instant,

    @Column(name = "encoding_version", nullable = false, updatable = false)
    var encodingVersion: Int,

    @Column(name = "algorithm_name", nullable = false, updatable = false)
    var algorithmName: String,

    @Column(name = "key_material", nullable = false, updatable = false, columnDefinition="BLOB")
    var keyMaterial: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is WrappingKeyEntity) return false
        if (alias != other.alias) return false
        return true
    }

    override fun hashCode(): Int {
        return alias.hashCode()
    }
}