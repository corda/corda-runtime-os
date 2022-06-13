package net.corda.libs.cpi.datamodel

import net.corda.db.schema.DbSchema
import java.time.Instant
import javax.persistence.Column
import javax.persistence.EmbeddedId
import javax.persistence.Entity
import javax.persistence.Lob
import javax.persistence.Table
import javax.persistence.EntityManager
import javax.persistence.Version

/**
 * Cpk binary data
 * NOTE: CPK binary is separate from the [CpkMetadataEntity] as it is possible a CPK is contained in
 * multiple CPIs. This is why the file checksum is the key.
 *
 * @property id the composite primary key consisting of cpk name, version and signer summary hash
 * @property fileChecksum for the binary data
 * @property data
 * @property insertTimestamp when the CPK Data was inserted.
 */
@Entity
@Table(name = "cpk_file", schema = DbSchema.CONFIG)
data class CpkFileEntity(
    @EmbeddedId
    var id: CpkKey,
    @Column(name = "file_checksum", nullable = false, unique = true)
    val fileChecksum: String,
    @Lob
    @Column(name = "data", nullable = false)
    val data: ByteArray,
    @Column(name = "is_deleted", nullable = false)
    var isDeleted: Boolean = false
) {
    @Version
    @Column(name = "entity_version", nullable = false)
    var entityVersion: Int = 0
    // this TS is managed on the DB itself
    @Column(name = "insert_ts", insertable = false, updatable = false)
    val insertTimestamp: Instant? = null

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CpkFileEntity

        if (fileChecksum != other.fileChecksum) return false
        if (!data.contentEquals(other.data)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = fileChecksum.hashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }
}

fun EntityManager.findCpkChecksumsNotIn(checksums: List<String>): List<String> {
    return createQuery(
        "SELECT cpk.fileChecksum FROM ${CpkFileEntity::class.simpleName} cpk " +
                "WHERE cpk.fileChecksum NOT IN (:checksums)",
        String::class.java
    )
        .setParameter(
            "checksums",
            checksums.ifEmpty { "null" })
        .resultList
}

fun EntityManager.findCpkDataEntity(checksum: String): CpkFileEntity? = createQuery(
    "FROM ${CpkFileEntity::class.java.simpleName} WHERE fileChecksum = :checksum",
    CpkFileEntity::class.java
)
    .setParameter("checksum", checksum)
    .resultList
    .firstOrNull()