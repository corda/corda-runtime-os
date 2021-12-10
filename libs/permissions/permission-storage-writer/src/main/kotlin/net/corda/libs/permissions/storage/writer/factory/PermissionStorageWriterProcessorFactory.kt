package net.corda.libs.permissions.storage.writer.factory

import net.corda.libs.permissions.storage.reader.PermissionStorageReader
import net.corda.libs.permissions.storage.writer.PermissionStorageWriterProcessor
import javax.persistence.EntityManagerFactory

/**
 * The [PermissionStorageWriterProcessorFactory] constructs instances of [PermissionStorageWriterProcessor].
 */
interface PermissionStorageWriterProcessorFactory {

    fun create(entityManagerFactory: EntityManagerFactory, reader: PermissionStorageReader): PermissionStorageWriterProcessor
}