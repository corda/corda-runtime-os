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
import javax.persistence.Lob
import javax.persistence.ManyToOne
import javax.persistence.Table
import javax.persistence.Version

/**
 * Defines a tenant associations for HSMs per category.
 */
@Entity
@Table(name = DbSchema.CRYPTO_HSM_TENANT_ASSOCIATION_TABLE)
@IdClass(HSMTenantAssociationEntityPrimaryKey::class)
class HSMTenantAssociationEntity(
    /**
     * Tenant which the configuration belongs to.
     */
    @Id
    @Column(name = "tenant_id", nullable = false, updatable = false, length = 12)
    var tenantId: String,

    /**
     * Category (LEDGER, TLS, etc.) which the configuration is described for.
     */
    @Id
    @Column(name = "category", nullable = false, updatable = false, length = 64)
    var category: String,

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "config_id", nullable = false, updatable = false)
    var config: HSMConfigEntity,

    /**
     * Master key alias which should be used.
     */
    @Column(name = "master_key_alias", nullable = true, updatable = true, length = 64)
    var masterKeyAlias: String?,

    /**
     * The secret which can be used when calculating an HSM alias based on the tenant's id and alias using HMAC,
     * the field is provided only in order to prevent tenants to guess each other aliases,
     * it's encrypted using system key.
     */
    @Lob
    @Column(name = "alias_Secret", nullable = true, updatable = true, columnDefinition="BLOB")
    var aliasSecret: ByteArray?
) {
    @Version
    @Column(name = "version", nullable = false)
    var version: Int = -1
}

@Embeddable
data class HSMTenantAssociationEntityPrimaryKey(
    var tenantId: String,
    var category: String
): Serializable