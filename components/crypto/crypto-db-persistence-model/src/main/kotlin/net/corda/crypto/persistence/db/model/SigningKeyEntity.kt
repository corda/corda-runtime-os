package net.corda.crypto.persistence.db.model

import net.corda.db.schema.DbSchema
import java.io.Serializable
import java.time.Instant
import javax.persistence.Column
import javax.persistence.Embeddable
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.IdClass
import javax.persistence.Table

@Entity
@Table(name = DbSchema.CRYPTO_SIGNING_KEY_TABLE, schema = DbSchema.CRYPTO)
@IdClass(SigningKeyEntityPrimaryKey::class)
@Suppress("LongParameterList")
class SigningKeyEntity(
    @Id
    @Column(name = "tenant_id", nullable = false, updatable = false)
    var tenantId: String,

    @Id
    @Column(name = "key_id", nullable = false, updatable = false)
    var keyId: String,

    @Column(name = "created", nullable = false, updatable = false)
    var created: Instant,

    @Column(name = "category", nullable = false, updatable = false)
    var category: String,

    @Column(name = "scheme_code_name", nullable = false, updatable = false)
    var schemeCodeName: String,

    @Column(name = "public_key", nullable = false, updatable = false, columnDefinition="BLOB")
    var publicKey: ByteArray,

    @Column(name = "key_material", nullable = true, updatable = false, columnDefinition="BLOB")
    var keyMaterial: ByteArray?,

    @Column(name = "encoding_version", nullable = true, updatable = false)
    var encodingVersion: Int?,

    @Column(name = "master_key_alias", nullable = true, updatable = false)
    var masterKeyAlias: String?,

    @Column(name = "alias", nullable = true, updatable = false)
    var alias: String?,

    @Column(name = "hsm_alias", nullable = true, updatable = false)
    var hsmAlias: String?,

    @Column(name = "external_id", nullable = true, updatable = false)
    var externalId: String?
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SigningKeyEntity

        if (tenantId != other.tenantId) return false
        if (keyId != other.keyId) return false

        return true
    }

    override fun hashCode(): Int {
        var result = tenantId.hashCode()
        result = 31 * result + keyId.hashCode()
        return result
    }
}

@Embeddable
data class SigningKeyEntityPrimaryKey(
    val tenantId: String,
    val keyId: String
): Serializable