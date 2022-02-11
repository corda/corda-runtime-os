package net.corda.libs.virtualnode.datamodel

import net.corda.db.schema.DbSchema.CONFIG
import net.corda.db.schema.DbSchema.HOLDING_IDENTITY_DB_TABLE
import java.util.UUID
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table

/**
 * The entity for a holding identity in the cluster database.
 *
 * @param holdingIdentityId The short 12-character hash of the holding identity.
 * @param holdingIdentityFullHash The full hash of the holding identity.
 * @param x500Name The X.500 name of the holding identity.
 * @param mgmGroupId The MGM group of the holding identity.
 * @param vaultDDLConnectionId A pointer to the holding identity's vault DDL details in the DB connection table.
 * @param cryptoDDLConnectionId A pointer to the holding identity's crypto DDL details in the DB connection table.
 * @param vaultDMLConnectionId A pointer to the holding identity's vault DML details in the DB connection table.
 * @param cryptoDMLConnectionId A pointer to the holding identity's crypto DML details in the DB connection table.
 * @param hsmConnectionId A pointer to the holding identity's entry in the HSM connection table.
 */
@Entity
@Table(name = HOLDING_IDENTITY_DB_TABLE, schema = CONFIG)
data class HoldingIdentityEntity(
    @Id
    @Column(name = "holding_identity_id", nullable = false)
    val holdingIdentityId: String,
    @Column(name = "holding_identity_full_hash", nullable = false)
    var holdingIdentityFullHash: String,
    @Column(name = "x500_name", nullable = false)
    var x500Name: String,
    @Column(name = "mgm_group_id", nullable = false)
    var mgmGroupId: String,
    @Column(name = "vault_ddl_connection_id", nullable = false)
    var vaultDDLConnectionId: UUID,
    @Column(name = "crypto_ddl_connection_id", nullable = false)
    var cryptoDDLConnectionId: UUID,
    @Column(name = "vault_dml_connection_id", nullable = false)
    var vaultDMLConnectionId: UUID,
    @Column(name = "crypto_dml_connection_id", nullable = false)
    var cryptoDMLConnectionId: UUID,
    @Column(name = "hsm_connection_id", nullable = false)
    var hsmConnectionId: UUID
)