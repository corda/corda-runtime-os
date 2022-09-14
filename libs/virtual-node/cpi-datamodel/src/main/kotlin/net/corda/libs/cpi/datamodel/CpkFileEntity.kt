package net.corda.libs.cpi.datamodel

import net.corda.db.schema.DbSchema
import java.time.Instant
import javax.persistence.Column
import javax.persistence.EmbeddedId
import javax.persistence.Entity
import javax.persistence.Lob
import javax.persistence.Table
import javax.persistence.EntityManager
import javax.persistence.NamedQuery
import javax.persistence.Version

// Now only used for testing- move to test code?
const val QUERY_NAME_UPDATE_CPK_FILE_DATA = "CpkFileEntity.updateFileData"
const val QUERY_PARAM_FILE_CHECKSUM = "fileChecksum"
const val QUERY_PARAM_DATA = "data"
const val QUERY_PARAM_ENTITY_VERSION = "entityVersion"
const val QUERY_PARAM_INCREMENTED_ENTITY_VERSION = "incrementedEntityVersion"
const val QUERY_PARAM_ID = "id"

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
@NamedQuery(
    // Now only used for testing- move to test code?
    name = QUERY_NAME_UPDATE_CPK_FILE_DATA,
    query = "UPDATE CpkFileEntity f" +
            " SET f.fileChecksum = :$QUERY_PARAM_FILE_CHECKSUM," +
            " f.data = :$QUERY_PARAM_DATA," +
            " f.entityVersion = :$QUERY_PARAM_INCREMENTED_ENTITY_VERSION," +
            " f.insertTimestamp = CURRENT_TIMESTAMP" +
            " WHERE f.entityVersion = :$QUERY_PARAM_ENTITY_VERSION" +
            " AND f.id = :$QUERY_PARAM_ID"
)
data class CpkFileEntity(
    @EmbeddedId
    val id: CpkKey,
    @Column(name = "file_checksum", nullable = false, unique = true)
    val fileChecksum: String,
    @Lob
    @Column(name = "data", nullable = false)
    val data: ByteArray,
    @Column(name = "is_deleted", nullable = false)
    val isDeleted: Boolean = false,
    @Version
    @Column(name = "entity_version", nullable = false)
    val entityVersion: Int = 0,
    // this TS is managed on the DB itself
    @Column(name = "insert_ts", insertable = false, updatable = false)
    val insertTimestamp: Instant? = null // changed on update
) {

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