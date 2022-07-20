package net.corda.libs.permissions.storage.writer.factory

import net.corda.libs.permissions.storage.reader.PermissionStorageReader
import net.corda.libs.permissions.storage.writer.PermissionStorageWriterProcessor
import java.util.function.Supplier
import javax.persistence.EntityManagerFactory

/**
 * The [PermissionStorageWriterProcessorFactory] constructs instances of [PermissionStorageWriterProcessor].
 */
interface PermissionStorageWriterProcessorFactory {

    fun create(
        entityManagerFactory: EntityManagerFactory,
        readerSupplier: Supplier<PermissionStorageReader?>
    ): PermissionStorageWriterProcessor
}