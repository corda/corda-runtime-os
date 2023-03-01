package net.corda.membership.datamodel

import net.corda.db.schema.DbSchema
import java.nio.ByteBuffer
import java.util.Objects
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.EntityManager
import javax.persistence.Id
import javax.persistence.Table

/**
 * Gets the current GroupParameters based on `epoch` number.
 * If no group parameters have been persisted yet, then null is returned.
 */
fun EntityManager.getCurrentGroupParameters(): GroupParametersEntity? {
    val criteriaBuilder = criteriaBuilder
    val criteriaQuery = criteriaBuilder.createQuery(GroupParametersEntity::class.java)

    val root = criteriaQuery.from(GroupParametersEntity::class.java)
    val query = criteriaQuery.select(root)
        .orderBy(criteriaBuilder.desc(root.get<String>("epoch")))

    return createQuery(query).setMaxResults(1).resultList.firstOrNull()
}

/**
 * An entity representing group parameters as visible by the current VNode.
 */
@Entity
@Table(name = DbSchema.VNODE_GROUP_PARAMETERS)
class GroupParametersEntity(
    @Id
    @Column(name = "epoch", nullable = false, updatable = false)
    val epoch: Int,

    @Column(name = "parameters", nullable = false, updatable = false)
    val parameters: ByteArray,

    @Column(name = "signature_public_key", nullable = true, updatable = false)
    val signaturePublicKey: ByteArray?,

    @Column(name = "signature_context", nullable = true, updatable = false)
    val signatureContext: ByteArray?,

    @Column(name = "signature_content", nullable = true, updatable = false)
    val signatureContent: ByteArray?,
) {
    override fun equals(other: Any?): Boolean {
        if (other === this) return true
        if (other == null) return false
        if (other !is GroupParametersEntity) return false
        return other.epoch == this.epoch && other.parameters.contentEquals(this.parameters)
    }

    override fun hashCode(): Int {
        return Objects.hash(epoch, ByteBuffer.wrap(parameters))
    }

}
