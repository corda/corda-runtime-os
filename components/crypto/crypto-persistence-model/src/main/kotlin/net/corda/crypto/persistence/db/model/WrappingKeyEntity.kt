package net.corda.crypto.persistence.db.model

import net.corda.db.schema.DbSchema
import java.time.Instant
import java.util.*
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table

/**
 * An entity representing a wrapping key which is used by Soft HSM implementation of the CryptoService
 * to wrap.
 *
 * The records are immutable.
 */
@Entity
@Suppress("LongParameterList")
@Table(name = DbSchema.CRYPTO_WRAPPING_KEY_TABLE)
class WrappingKeyEntity(
    @Id
    @Column(name = "id", nullable = false)
    var id: UUID,

    /**
     * Key alias must be unique across all tenants. The key can be reused by different tenants.
     */
    @Column(name = "alias", nullable = false, updatable = false, length = 64)
    var alias: String,

    @Column(name = "generation", nullable = false, updatable = true)
    var generation: Int,

    /**
     * When the key was generated.
     */
    @Column(name = "created", nullable = false, updatable = false)
    var created: Instant,
    
    /**
     * Encoding version of the key.
     */
    @Column(name = "encoding_version", nullable = false, updatable = false)
    var encodingVersion: Int,

    /**
     * Key's algorithm's.
     */
    @Column(name = "algorithm_name", nullable = false, updatable = false, length = 64)
    var algorithmName: String,

    /**
     * Key material for the wrapping key. It's encrypted by another wrapping key.
     */
    @Column(name = "key_material", nullable = false, updatable = true, columnDefinition = "BLOB")
    var keyMaterial: ByteArray,

    /**
     * When the key should be rotated, if ever. Null indicates no rotation scheduled.
     */
    @Column(name = "rotation_date", nullable = false)
    var rotationDate: Instant,

    /**
     * If true, the parent key is in the database and parentKeyReference is an id.
     * If false, the parent key is in Corda smart config, and parentkeyReference is the config path.
     */
    @Column(name = "is_parent_key_managed")
    var isParentKeyManaged: Boolean,

    /**
     * The id of the key used to wrap this wrapping key, or a config path.
     */
    @Column(name = "parent_key_reference", nullable = false, updatable = true)
    var parentKeyReference: String,
) {
    override fun hashCode() = alias.hashCode()
    override fun equals(other: Any?) = other != null && other is WrappingKeyEntity && other.id.equals(id)
}
