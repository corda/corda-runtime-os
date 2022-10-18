package net.corda.libs.cpi.datamodel

import net.corda.db.schema.DbSchema
import net.corda.libs.packaging.core.CpiIdentifier
import java.io.Serializable
import java.time.Instant
import java.util.UUID
import javax.persistence.Column
import javax.persistence.Embeddable
import javax.persistence.EmbeddedId
import javax.persistence.Entity
import javax.persistence.EntityManager
import javax.persistence.Table

/**
 * Audit representation of a DB ChangeLog (Liquibase) file associated with a CPK.
 */
@Entity
@Table(name = "cpk_db_change_log_audit", schema = DbSchema.CONFIG)
class CpkDbChangeLogAuditEntity(
    @EmbeddedId
    var id: CpkDbChangeLogAuditKey,
    @Column(name = "content", nullable = false)
    val content: String,
    @Column(name = "is_deleted", nullable = false)
    var isDeleted: Boolean = false
) {
    // this TS is managed on the DB itself
    @Column(name = "insert_ts", insertable = false, updatable = false)
    val insertTimestamp: Instant? = null

    constructor(cpkDbChangeLogEntity: CpkDbChangeLogEntity) : this(
        CpkDbChangeLogAuditKey(
            cpkDbChangeLogEntity.id,
            cpkDbChangeLogEntity.fileChecksum,
            cpkDbChangeLogEntity.changeUUID
        ),
        cpkDbChangeLogEntity.content,
        cpkDbChangeLogEntity.isDeleted
    )
}

@Embeddable
data class CpkDbChangeLogAuditKey(
    @Column(name = "cpk_name", nullable = false)
    var cpkName: String,
    @Column(name = "cpk_version", nullable = false)
    var cpkVersion: String,
    @Column(name = "cpk_signer_summary_hash", nullable = false)
    var cpkSignerSummaryHash: String,
    @Column(name = "cpk_file_checksum", nullable = false)
    val fileChecksum: String,
    @Column(name = "change_uuid", nullable = false)
    var changeUUID: UUID,
    @Column(name = "file_path", nullable = false)
    val filePath: String,
) : Serializable {
    constructor(cpkDbChangeLogKey: CpkDbChangeLogKey, fileChecksum: String, changeUUID: UUID) : this(
        cpkDbChangeLogKey.cpkName,
        cpkDbChangeLogKey.cpkVersion,
        cpkDbChangeLogKey.cpkSignerSummaryHash,
        fileChecksum,
        changeUUID,
        cpkDbChangeLogKey.filePath
    )
}

/*
 * Find all the audit db changelogs for a CPI
 */
fun findDbChangeLogAuditForCpi(
    entityManager: EntityManager,
    cpi: CpiIdentifier
): List<CpkDbChangeLogAuditEntity> = entityManager.createQuery(
    "SELECT changelog " +
        "FROM ${CpkDbChangeLogAuditEntity::class.simpleName} AS changelog INNER JOIN " +
        "${CpiCpkEntity::class.simpleName} AS cpi " +
        "ON changelog.id.cpkName = cpi.metadata.id.cpkName AND " +
        "   changelog.id.cpkVersion = cpi.id.cpkVersion AND " +
        "   changelog.id.cpkSignerSummaryHash = cpi.id.cpkSignerSummaryHash " +
        "WHERE cpi.id.cpiName = :name AND " +
        "      cpi.id.cpiVersion = :version AND " +
        "      cpi.id.cpiSignerSummaryHash = :signerSummaryHash",
    CpkDbChangeLogAuditEntity::class.java
)
    .setParameter("name", cpi.name)
    .setParameter("version", cpi.version)
    .setParameter("signerSummaryHash", cpi.signerSummaryHash?.toString() ?: "")
    .resultList

/*
 * Find all the audit db changelogs for a CPI
 */
fun findDbChangeLogAuditForCpi(
    entityManager: EntityManager,
    cpi: CpiIdentifier,
    changeUUIDs: Set<UUID>
): List<CpkDbChangeLogAuditEntity> = entityManager.createQuery(
    "SELECT changelog " +
        "FROM ${CpkDbChangeLogAuditEntity::class.simpleName} AS changelog INNER JOIN " +
        "${CpiCpkEntity::class.simpleName} AS cpi " +
        "ON changelog.id.cpkName = cpi.metadata.id.cpkName AND " +
        "   changelog.id.cpkVersion = cpi.id.cpkVersion AND " +
        "   changelog.id.cpkSignerSummaryHash = cpi.id.cpkSignerSummaryHash " +
        "WHERE cpi.id.cpiName = :name AND " +
        "      cpi.id.cpiVersion = :version AND " +
        "      cpi.id.cpiSignerSummaryHash = :signerSummaryHash AND " +
        "      changelog.id.changeUUID IN :changeUUIDs",
    CpkDbChangeLogAuditEntity::class.java
)
    .setParameter("name", cpi.name)
    .setParameter("version", cpi.version)
    .setParameter("signerSummaryHash", cpi.signerSummaryHash?.toString() ?: "")
    .setParameter("changeUUIDs", changeUUIDs)
    .resultList
