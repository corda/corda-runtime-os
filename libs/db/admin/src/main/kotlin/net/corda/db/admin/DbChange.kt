package net.corda.db.admin

import java.io.InputStream

/**
 * Encapsulates a Liquibase DB Change.
 *
 * Implementations are expected to be able to fetch the change log files.
 * This means we can provide a FileSystem, Kafka or DB implementation, for example.
 */
interface DbChange {
    /**
     * List of all the "master" change log files that need to be taken into account.
     * These can each have 'include' elements.
     * The [LiquibaseSchemaMigrator] is expected to combine them all.
     * [LinkedHashSet] because order and uniqueness are important. Log files will be
     * processed in order.
     * Each entry represents the "path" that can be used to [fetch] the ChangeLog file.
     *
     */
    val masterChangeLogFiles: LinkedHashSet<String>

    /**
     * List of all the change log files that need to be considered.
     * Each entry represents the "path" that can be used to [fetch] the ChangeLog file.
     */
    val changeLogFileList: Set<String>

    /**
     * Function that fetches the Change Log file identified with [name] and
     * returns this as an [InputStream].
     * This could, for example, fetch Change Log entries from Kafka, File System, DB etc.
     *
     * @param path to the ChangeLog file
     * @return [InputStream] representing the Change Log file.
     */
    fun fetch(path: String): InputStream
}
