package net.corda.crypto.persistence.db.model

import net.corda.db.schema.DbSchema
import java.time.Instant
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.FetchType
import javax.persistence.Id
import javax.persistence.JoinColumn
import javax.persistence.ManyToOne
import javax.persistence.Table

/**
 * Defines a tenant associations for HSMs per category.
 */
@Entity
@Table(name = DbSchema.CRYPTO_HSM_CATEGORY_ASSOCIATION_TABLE)
@Suppress("LongParameterList")
class HSMCategoryAssociationEntity(
    /**
     * The association id.
     */
    @Id
    @Column(name = "id", nullable = false, updatable = false, length = 36)
    val id: String,

    /**
     * Tenant which the association belongs to, added as the means to keep uniqueness for tenant/category/deprecation
     */
    @Column(name = "tenant_id", nullable = false, updatable = false, length = 12)
    var tenantId: String,

    /**
     * Category (LEDGER, TLS, etc.) which the configuration is described for.
     */
    @Column(name = "category", nullable = false, updatable = false, length = 20)
    var category: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hsm_association_id", nullable = false, updatable = false)
    var hsm: HSMAssociationEntity,

    /**
     * When the association was created.
     */
    @Column(name = "timestamp", nullable = false, updatable = false)
    var timestamp: Instant,

    /**
     * When the association was deprecated.
     */
    @Column(name = "deprecated_at", nullable = true, updatable = false)
    var deprecatedAt: Instant?
)
