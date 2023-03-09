package net.corda.crypto.persistence.v50ga

import net.corda.db.schema.DbSchema
import java.time.Instant
import java.util.Date
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
@Table(name = DbSchema.CRYPTO_WRAPPING_KEY_TABLE)

/**
 * An entity representing a wrapping key which is used by Soft HSM implementation of the CryptoService
 * to wrap.
 *
 * The records are immutable.
 */
@Entity
@Table(name = DbSchema.CRYPTO_WRAPPING_KEY_TABLE)
class WrappingKeyEntity(
    /**
     * Key alias must be unique across all tenants. The key can be reused by different tenants.
     */
    @Id
    @Column(name = "alias", nullable = false, updatable = false, length = 64)
    val alias: String,

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
     * Key material for the wrapping key. It's encrypted by by another key which is obtained through the configuration.
     */
    @Column(name = "key_material", nullable = false, updatable = false, columnDefinition = "BLOB")
    var keyMaterial: ByteArray
    
    @Column(name = "rotation_date", nullable = true, updatable = true, columnDefinition = "DATE")
    var rotationDate: Date
)