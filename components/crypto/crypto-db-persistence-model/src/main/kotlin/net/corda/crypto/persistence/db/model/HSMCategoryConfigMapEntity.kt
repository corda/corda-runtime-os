package net.corda.crypto.persistence.db.model

import net.corda.db.schema.DbSchema
import java.io.Serializable
import javax.persistence.Column
import javax.persistence.Embeddable
import javax.persistence.Entity
import javax.persistence.FetchType
import javax.persistence.Id
import javax.persistence.IdClass
import javax.persistence.JoinColumn
import javax.persistence.ManyToOne
import javax.persistence.Table

/**
 * An entity representing which category the config can be used for.
 *
 * The records are immutable.
 */
@Entity
@Table(name = DbSchema.CRYPTO_HSM_CATEGORY_CONFIG_MAP_TABLE)
@IdClass(HSMCategoryConfigMapEntityPrimaryKey::class)
class HSMCategoryConfigMapEntity(
    @Id
    @Column(name = "category", nullable = false, updatable = false, length = 64)
    var category: String,

    @Id
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "config_id", nullable = false, updatable = false)
    var config: HSMConfigEntity
)

@Embeddable
data class HSMCategoryConfigMapEntityPrimaryKey(
    var category: String,
    var configId: String
): Serializable