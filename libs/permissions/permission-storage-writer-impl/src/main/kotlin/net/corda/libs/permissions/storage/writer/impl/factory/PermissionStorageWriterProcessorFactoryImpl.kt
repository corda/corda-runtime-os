package net.corda.libs.permissions.storage.writer.impl.factory

import net.corda.libs.permissions.storage.reader.PermissionStorageReader
import net.corda.libs.permissions.storage.writer.PermissionStorageWriterProcessor
import net.corda.libs.permissions.storage.writer.factory.PermissionStorageWriterProcessorFactory
import net.corda.libs.permissions.storage.writer.impl.PermissionStorageWriterProcessorImpl
import net.corda.libs.permissions.storage.writer.impl.permission.impl.PermissionWriterImpl
import org.osgi.service.component.annotations.Component
import javax.persistence.EntityManagerFactory
import net.corda.libs.permissions.storage.writer.impl.role.impl.RoleWriterImpl
import net.corda.libs.permissions.storage.writer.impl.user.impl.UserWriterImpl

@Component(service = [PermissionStorageWriterProcessorFactory::class])
class PermissionStorageWriterProcessorFactoryImpl : PermissionStorageWriterProcessorFactory {

    override fun create(entityManagerFactory: EntityManagerFactory, reader: PermissionStorageReader): PermissionStorageWriterProcessor {
        return PermissionStorageWriterProcessorImpl(
            reader,
            UserWriterImpl(entityManagerFactory),
            RoleWriterImpl(entityManagerFactory),
            PermissionWriterImpl(entityManagerFactory)
        )
    }
}