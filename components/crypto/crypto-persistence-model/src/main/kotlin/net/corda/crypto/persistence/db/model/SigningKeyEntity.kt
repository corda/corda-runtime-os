package net.corda.crypto.persistence.db.model

import net.corda.db.schema.DbSchema
import java.io.Serializable
import java.time.Instant
import javax.persistence.Column
import javax.persistence.Embeddable
import javax.persistence.Entity
import javax.persistence.EnumType
import javax.persistence.Enumerated
import javax.persistence.Id
import javax.persistence.IdClass
import javax.persistence.Table

/**
 * An entity representing information about a key pair belonging to a tenant.
 *
 * As the crypto manages keys on behalf of members and cluster itself the tenant is defined as either
 * a member id (derived from the VNODE id of that member) or word  'cluster' for the keys belonging to the cluster.
 *
 * The only fields whcih are allowed to be changed are:
 */
@Entity
@Table(name = DbSchema.CRYPTO_SIGNING_KEY_TABLE)
@IdClass(SigningKeyEntityPrimaryKey::class)
@Suppress("LongParameterList")
class SigningKeyEntity(
    /**
     * Tenant which the key belongs to.
     */
    @Id
    @Column(name = "tenant_id", nullable = false, updatable = false, length = 12)
    var tenantId: String,

    /**
     * The key id, which is calculated as SHA256 converted to HEX string with only first 12 characters if it.
     */
    @Id
    @Column(name = "key_id", nullable = false, updatable = false, length = 12)
    var keyId: String,

    /**
     * The full key id, which is calculated as SHA256 converted to HEX string.
     */
    @Column(name = "full_key_id", nullable = false, updatable = false)
    var fullKeyId: String,

    /**
     * When the key was generated.
     */
    @Column(name = "timestamp", nullable = false, updatable = false)
    var timestamp: Instant,

    /**
     * HSM category where the private key of the pair was generated.
     */
    @Column(name = "category", nullable = false, updatable = false, length = 64)
    var category: String,

    /**
     * The key's signature scheme code name.
     */
    @Column(name = "scheme_code_name", nullable = false, updatable = false, length = 64)
    var schemeCodeName: String,

    /**
     * The public key of the pair.
     */
    @Column(name = "public_key", nullable = false, updatable = false, columnDefinition="BLOB")
    var publicKey: ByteArray,

    /**
     * If the private key was wrapped that array will contain the encrypted private key.
     */
    @Column(name = "key_material", nullable = true, updatable = false, columnDefinition="BLOB")
    var keyMaterial: ByteArray?,

    /**
     * Encoding version of the key.
     */
    @Column(name = "encoding_version", nullable = true, updatable = false)
    var encodingVersion: Int?,

    /**
     * If the private key was wrapped that defines the alias of the wrapping which was used to wrap it.
     */
    @Column(name = "master_key_alias", nullable = true, updatable = false, length = 64)
    var masterKeyAlias: String?,

    /**
     * The key's alias as assigned by a tenant, must be unique per tenant. Note that it should not be used
     * as the alias to store/generate the key in an HSM.
     */
    @Column(name = "alias", nullable = true, updatable = false, length = 64)
    var alias: String?,

    /**
     * If the key is persisted in an HSM that defines the alias which was used to persist it.
     */
    @Column(name = "hsm_alias", nullable = true, updatable = false, length = 64)
    var hsmAlias: String?,

    /**
     * Some external id associated with the key pair by the tenant.
     */
    @Column(name = "external_id", nullable = true, updatable = false, length = 64)
    var externalId: String?,

    @Column(name = "hsm_id", nullable = false, updatable = true, length = 36)
    var hsmId: String,

    /**
     * Defines how wrapping key should be used for each tenant.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    var status: SigningKeyEntityStatus
)

@Embeddable
data class SigningKeyEntityPrimaryKey(
    val tenantId: String,
    val keyId: String
): Serializable