package net.corda.crypto.persistence.db.model

import net.corda.db.schema.DbSchema
import java.io.Serializable
import java.time.Instant
import java.util.UUID
import javax.persistence.Column
import javax.persistence.Embeddable
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.IdClass
import javax.persistence.Table

@Entity
@Table(name = DbSchema.CRYPTO_SIGNING_KEY_MATERIAL_TABLE)
@IdClass(SigningKeyMaterialEntityId::class)
class SigningKeyMaterialEntity(
    /*
     * Reference ot the wrapping key used to create key material
     */

    @Id
    @Column(name = "wrapping_key_id", nullable = false)
    var wrappingKeyId: UUID,

    /**
     * Reference to the signing key this is one copy of the material for
     */
    @Id
    @Column(name = "signing_key_id", nullable = false)
    var signingKeyId: UUID,

    /**
     * The encrypted key material.
     */
    @Column(name = "signing_key_material", nullable = false)
    var keyMaterial: ByteArray,

    /**
     * Time signigng key material was created
     */
    @Column(name = "created", nullable = false)
    var created: Instant,

) {
    override fun hashCode() = wrappingKeyId.hashCode() + signingKeyId.hashCode() * 31
    override fun equals(other: Any?) =
                other != null &&
                other is SigningKeyMaterialEntity &&
                other.wrappingKeyId.equals(this.wrappingKeyId) &&
                other.signingKeyId.equals(this.signingKeyId)
}
@Embeddable
data class SigningKeyMaterialEntityId(
    val wrappingKeyId: UUID,
    val signingKeyId: UUID,
) : Serializable
