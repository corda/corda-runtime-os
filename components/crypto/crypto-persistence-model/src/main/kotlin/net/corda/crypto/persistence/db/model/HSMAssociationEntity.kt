package net.corda.crypto.persistence.db.model

import net.corda.db.schema.DbSchema
import java.time.Instant
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table

/**
 * Defines a tenant associations for HSMs per category.
 */
@Entity
@Table(name = DbSchema.CRYPTO_HSM_ASSOCIATION_TABLE)
@Suppress("LongParameterList")
class HSMAssociationEntity(
    /**
     * The association id.
     */
    @Id
    @Column(name = "id", nullable = false, updatable = false, length = 36)
    var id: String,

    /**
     * Tenant which the configuration belongs to.
     */
    @Column(name = "tenant_id", nullable = false, updatable = false, length = 12)
    var tenantId: String,

    @Column(name = "hsm_id", nullable = false, updatable = false)
    var hsmId: String,

    /**
     * When the configuration was created.
     */
    @Column(name = "timestamp", nullable = false, updatable = false)
    var timestamp: Instant,

    /**
     * Master key alias which should be used.
     */
    @Column(name = "master_key_alias", nullable = true, updatable = false, length = 30)
    var masterKeyAlias: String?,
)
