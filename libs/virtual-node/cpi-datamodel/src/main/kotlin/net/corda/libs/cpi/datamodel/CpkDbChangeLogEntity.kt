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
import javax.persistence.Version

/**
 * Representation of a DB ChangeLog (Liquibase) file associated with a CPK.
 */
@Entity
@Table(name = "cpk_db_change_log", schema = DbSchema.CONFIG)
class CpkDbChangeLogEntity(
    @EmbeddedId
    var id: CpkDbChangeLogKey,
    @Column(name = "cpk_file_checksum", nullable = false, unique = true)
    val fileChecksum: String,
    @Column(name = "content", nullable = false)
    override val content: String,
    @Column(name = "changeset_id", nullable = false)
    val changesetId: UUID
) : CpkDbChangelog {
    // This structure does not distinguish the root changelogs from changelog include files
    // (or CSVs, which we do not need to support). So, to find the root, you need to look for a filename
    // convention. See the comment in the companion object of VirtualNodeDbChangeLog.
    // for the convention used when populating these records.
    @Version
    @Column(name = "entity_version", nullable = false)
    var entityVersion: Int = 0

    @Column(name = "is_deleted", nullable = false)
    var isDeleted: Boolean = false

    // this TS is managed on the DB itself
    @Column(name = "insert_ts", insertable = false, updatable = false)
    val insertTimestamp: Instant? = null

    override val filePath get() = id.filePath
}

/**
 * Composite primary key for a Cpk Change Log Entry.
 */
@Embeddable
data class CpkDbChangeLogKey(
    @Column(name = "cpk_name", nullable = false)
    var cpkName: String,
    @Column(name = "cpk_version", nullable = false)
    var cpkVersion: String,
    @Column(name = "cpk_signer_summary_hash", nullable = false)
    var cpkSignerSummaryHash: String,
    @Column(name = "file_path", nullable = false)
    val filePath: String,
) : Serializable


/*
 * Find all the db changelogs for a CPI
 */
fun findDbChangeLogForCpi(
    entityManager: EntityManager,
    cpi: CpiIdentifier
): List<CpkDbChangeLogEntity> = entityManager.createQuery(
    "SELECT changelog " +
            "FROM ${CpkDbChangeLogEntity::class.simpleName} AS changelog INNER JOIN " +
            "${CpiCpkEntity::class.simpleName} AS cpi " +
            "ON changelog.id.cpkName = cpi.metadata.id.cpkName AND " +
            "   changelog.id.cpkVersion = cpi.id.cpkVersion AND " +
            "   changelog.id.cpkSignerSummaryHash = cpi.id.cpkSignerSummaryHash "+
            "WHERE cpi.id.cpiName = :name AND "+
            "      cpi.id.cpiVersion = :version AND "+
            "      cpi.id.cpiSignerSummaryHash = :signerSummaryHash AND "+
            "      changelog.isDeleted = FALSE",
    // TODO - what order should we return?
    CpkDbChangeLogEntity::class.java
)
    .setParameter("name", cpi.name)
    .setParameter("version", cpi.version)
    .setParameter("signerSummaryHash", cpi.signerSummaryHash.toString())
    .resultList
