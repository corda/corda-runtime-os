package net.corda.libs.permissions.storage.writer.impl.factory

import net.corda.libs.permissions.storage.reader.PermissionStorageReader
import net.corda.libs.permissions.storage.writer.PermissionStorageWriterProcessor
import net.corda.libs.permissions.storage.writer.factory.PermissionStorageWriterProcessorFactory
import net.corda.libs.permissions.storage.writer.impl.PermissionStorageWriterProcessorImpl
import net.corda.libs.permissions.storage.writer.impl.group.impl.GroupWriterImpl
import net.corda.libs.permissions.storage.writer.impl.permission.impl.PermissionWriterImpl
import net.corda.libs.permissions.storage.writer.impl.role.impl.RoleWriterImpl
import net.corda.libs.permissions.storage.writer.impl.user.impl.UserWriterImpl
import org.osgi.service.component.annotations.Component
import java.util.function.Supplier
import javax.persistence.EntityManagerFactory

@Component(service = [PermissionStorageWriterProcessorFactory::class])
@Suppress("Unused")
class PermissionStorageWriterProcessorFactoryImpl : PermissionStorageWriterProcessorFactory {

    override fun create(
        entityManagerFactory: EntityManagerFactory,
        readerSupplier: Supplier<PermissionStorageReader?>
    ): PermissionStorageWriterProcessor {
        return PermissionStorageWriterProcessorImpl(
            readerSupplier,
            UserWriterImpl(entityManagerFactory),
            RoleWriterImpl(entityManagerFactory),
            GroupWriterImpl(entityManagerFactory),
            PermissionWriterImpl(entityManagerFactory)
        )
    }
}
