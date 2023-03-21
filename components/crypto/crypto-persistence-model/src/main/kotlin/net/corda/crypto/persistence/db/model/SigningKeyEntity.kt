package net.corda.crypto.persistence.db.model

import net.corda.db.schema.DbSchema
import java.time.Instant
import java.util.UUID
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.EnumType
import javax.persistence.Enumerated
import javax.persistence.Id
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
@Suppress("LongParameterList")
class SigningKeyEntity(
    /*
     * Synthetic UUID of the key pair
     */
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    var id: UUID,

    /**
     * Tenant which the key belongs to.
     */
    @Column(name = "tenant_id", nullable = false, updatable = false, length = 12)
    var tenantId: String,

    /**
     * The short key id, which is calculated as SHA256 converted to HEX string with only first 12 characters if it.
     */
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
    @Column(name = "created", nullable = false)
    var created: Instant,

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
     * Encoding version of the key.
     */
    @Column(name = "encoding_version", nullable = true, updatable = false)
    var encodingVersion: Int?,

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
) {
        override fun hashCode() = id.hashCode()
        override fun equals(other: Any?) = other != null && other is SigningKeyEntity && other.id.equals(id)
}
