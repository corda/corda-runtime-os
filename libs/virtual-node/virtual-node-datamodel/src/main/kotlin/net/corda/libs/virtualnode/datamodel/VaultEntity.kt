package net.corda.libs.virtualnode.datamodel

import net.corda.db.schema.DbSchema.VNODE_VAULT_DB_TABLE
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table

/**
 * The entity for a virtual node vault.
 *
 * @param holdingIdentityId The short 12-character hash of the holding identity.
 */
@Entity
@Table(name = VNODE_VAULT_DB_TABLE)
data class VaultEntity(
    @Id
    @Column(name = "holding_identity_id", nullable = false)
    val holdingIdentityId: String,
)