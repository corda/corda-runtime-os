package net.corda.membership.datamodel

import net.corda.db.schema.DbSchema
import java.time.Instant
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table

/**
 * An entity representing a registration request either sent by a member to an MGM, or received by an MGM from a Member.
 */
@Entity
@Table(name = DbSchema.VNODE_GROUP_REGISTRATION_TABLE)
@Suppress("LongParameterList")
class RegistrationRequestEntity(
    /**
     * A unique ID for the registration request as set by the registering member.
     */
    @Id
    @Column(name = "registration_id", nullable = false, updatable = false)
    val registrationId: String,

    /**
     * The holding identity ID of the registering member.
     */
    @Column(name = "holding_identity_id", nullable = false, updatable = false)
    val holdingIdentityShortHash: String,

    /**
     * The last status of the registration request.
     */
    @Column(nullable = false)
    var status: String,

    /**
     * The instant representing when this registration request was received or created.
     */
    @Column(nullable = false, updatable = false)
    val created: Instant,

    /**
     * The instant representing the last time this registration request was updated.
     * Managed by the DB and is not explicitly set during INSERT or UPDATE
     */
    @Column(name = "last_modified", nullable = false)
    var lastModified: Instant,

    /**
     * The serialized member context provided during registration. Serialized as [KeyValuePairList].
     */
    @Column(nullable = false, updatable = false, columnDefinition = "BLOB")
    val context: ByteArray,

    /**
     * Signature key of member signature, can be sued to verify the signature.
     */
    @Column(name = "signature_key", nullable = false, updatable = false, columnDefinition = "BLOB")
    val signatureKey: ByteArray,

    /**
     * Byte array of the member signature, exactly as returned by crypto signing operations.
     */
    @Column(name = "signature_content", nullable = false, updatable = false, columnDefinition = "BLOB")
    val signatureContent: ByteArray,

    /**
     * Signature spec of member signature.
     */
    // TODO Are we going to be storing `ParameterizedSignatureSpec` here?
    //  If so need to consider saving extra signature spec parameters as recorded in https://r3-cev.atlassian.net/browse/CORE-11685
    @Column(name = "signature_spec", nullable = false, updatable = false)
    val signatureSpec: String,

    /**
     * Latest serial seen by the member when calling registration.
     */
    @Column(name = "serial", nullable = true)
    val serial: Long?,

    /**
     * Reason why the request is in the status specified by [status].
     */
    @Column(nullable = true)
    var reason: String? = null,
) {

    override fun equals(other: Any?): Boolean {
        if (other === this) return true
        if (other == null) return false
        if (other !is RegistrationRequestEntity) return false
        return other.registrationId == this.registrationId
    }

    override fun hashCode(): Int {
        return registrationId.hashCode()
    }
}
