package net.corda.crypto.persistence.db.model

import net.corda.db.schema.DbSchema
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.EnumType
import javax.persistence.Enumerated
import javax.persistence.FetchType
import javax.persistence.Id
import javax.persistence.JoinColumn
import javax.persistence.ManyToOne
import javax.persistence.Table

/**
 * An entity representing which category the config can be used for.
 *
 * The records are immutable.
 */
@Entity
@Table(name = DbSchema.CRYPTO_HSM_CATEGORY_MAP_TABLE)
class HSMCategoryMapEntity(
    /**
     * The association id.
     */
    @Id
    @Column(name = "id", nullable = false, updatable = false, length = 36)
    val id: String,

    @Column(name = "category", nullable = false, updatable = false, length = 20)
    var category: String,

    /**
     * Defines how wrapping key should be used for each tenant.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "key_policy", nullable = false, length = 16)
    var keyPolicy: PrivateKeyPolicy,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "config_id", nullable = false, updatable = false)
    var config: HSMConfigEntity
)
