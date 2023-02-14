package net.corda.libs.cpi.datamodel

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
@Table(name = "cpk_db_change_log")
class CpkDbChangeLogEntity(
    @EmbeddedId
    var id: CpkDbChangeLogKey,
    @Column(name = "content", nullable = false)
    val content: String,
    // This structure does not distinguish the root changelogs from changelog include files
    // (or CSVs, which we do not need to support). So, to find the root, you need to look for a filename
    // convention. See the comment in the companion object of VirtualNodeDbChangeLog.
    // for the convention used when populating these records.
    @Version
    @Column(name = "entity_version", nullable = false)
    var entityVersion: Int = 0,

    @Column(name = "is_deleted", nullable = false)
    var isDeleted: Boolean = false,

    // this TS is managed on the DB itself
    @Column(name = "insert_ts", insertable = false, updatable = false)
    val insertTimestamp: Instant? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CpkDbChangeLogEntity

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}

/**
 * Composite primary key for a Cpk Change Log Entry.
 */
@Embeddable
class CpkDbChangeLogKey(
    @Column(name = "cpk_file_checksum", nullable = false)
    var cpkFileChecksum: String,
    @Column(name = "file_path", nullable = false)
    val filePath: String
) : Serializable {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CpkDbChangeLogKey

        if (cpkFileChecksum != other.cpkFileChecksum) return false
        if (filePath != other.filePath) return false

        return true
    }

    override fun hashCode(): Int {
        var result = cpkFileChecksum.hashCode()
        result = 31 * result + filePath.hashCode()
        return result
    }
}

fun findChangelogEntitiesForGivenCpkFileChecksums(entityManager: EntityManager, cpkFileChecksums: Set<String>):
        Map<String, List<CpkDbChangeLogEntity>> {
    return cpkFileChecksums.chunked(100) { batch ->
        entityManager.createQuery(
            "FROM ${CpkDbChangeLogEntity::class.simpleName}" +
                    " WHERE id.cpkFileChecksum IN :cpkFileChecksums",
            CpkDbChangeLogEntity::class.java
        )
            .setParameter("cpkFileChecksums", batch)
            .resultList
    }.flatten()
        .groupBy { it.id.cpkFileChecksum }
}

fun findCurrentCpkChangeLogsForCpi(
    entityManager: EntityManager,
    cpiName: String,
    cpiVersion: String,
    cpiSignerSummaryHash: String?
): List<CpkDbChangeLogEntity> {
    return entityManager.createQuery(
        "SELECT changelog " +
                "FROM ${CpkDbChangeLogEntity::class.simpleName} AS changelog INNER JOIN " +
                "${CpiCpkEntity::class.simpleName} AS cpiCpk " +
                "ON changelog.id.cpkFileChecksum = cpiCpk.id.cpkFileChecksum " +
                "WHERE cpiCpk.id.cpiName = :name AND " +
                "      cpiCpk.id.cpiVersion = :version AND " +
                "      cpiCpk.id.cpiSignerSummaryHash = :signerSummaryHash AND " +
                "      changelog.isDeleted = FALSE " +
                "ORDER BY changelog.insertTimestamp DESC",
        CpkDbChangeLogEntity::class.java
    )
        .setParameter("name", cpiName)
        .setParameter("version", cpiVersion)
        .setParameter("signerSummaryHash", cpiSignerSummaryHash ?: "")
        .resultList
}
