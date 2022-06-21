package net.corda.libs.cpi.datamodel

import net.corda.db.schema.DbSchema
import java.io.Serializable
import java.time.Instant
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
    val content: String,
) {
    @Version
    @Column(name = "entity_version", nullable = false)
    var entityVersion: Int = 0

    @Column(name = "is_deleted", nullable = false)
    var isDeleted: Boolean = false

    // this TS is managed on the DB itself
    @Column(name = "insert_ts", insertable = false, updatable = false)
    val insertTimestamp: Instant? = null
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

fun EntityManager.findCpkDbChangeLog(
    cpkName: String,
    cpkVersion: String,
    cpkSignerSummaryHash: String
): List<CpkDbChangeLogEntity> {
    return createQuery(
        "FROM ${CpkDbChangeLogEntity::class.simpleName} d " +
                "WHERE d.id.cpkName = :name AND d.id.cpkVersion = :version AND d.id.cpkSignerSummaryHash = :summaryHash",
        CpkDbChangeLogEntity::class.java
    )
        .setParameter("name", cpkName)
        .setParameter("version", cpkVersion)
        .setParameter("summaryHash", cpkSignerSummaryHash)
        .resultList
}