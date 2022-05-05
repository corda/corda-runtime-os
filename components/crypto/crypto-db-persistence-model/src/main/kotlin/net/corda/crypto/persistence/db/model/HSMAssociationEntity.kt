package net.corda.crypto.persistence.db.model

import net.corda.db.schema.DbSchema
import java.time.Instant
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.FetchType
import javax.persistence.Id
import javax.persistence.JoinColumn
import javax.persistence.Lob
import javax.persistence.ManyToOne
import javax.persistence.Table
import javax.persistence.Version

/**
 * Defines a tenant associations for HSMs per category.
 */
@Entity
@Table(name = DbSchema.CRYPTO_HSM_ASSOCIATION_TABLE)
class HSMAssociationEntity(
    /**
     * The association id.
     */
    @Id
    @Column(name = "id", nullable = false, updatable = false, length = 36)
    val id: String,

    /**
     * Tenant which the configuration belongs to.
     */
    @Column(name = "tenant_id", nullable = false, updatable = false, length = 12)
    var tenantId: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "config_id", nullable = false, updatable = false)
    var config: HSMConfigEntity,

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

    /**
     * The secret which can be used when calculating an HSM alias based on the tenant's id and alias using HMAC,
     * the field is provided only in order to prevent tenants to guess each other aliases,
     * it's encrypted using system key.
     */
    @Lob
    @Column(name = "alias_secret", nullable = true, updatable = false, columnDefinition="BLOB")
    var aliasSecret: ByteArray?
)
