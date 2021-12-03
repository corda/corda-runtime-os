package net.corda.libs.permissions.storage.writer.impl.factory

import net.corda.libs.permissions.storage.writer.PermissionStorageWriterProcessor
import net.corda.libs.permissions.storage.writer.factory.PermissionStorageWriterProcessorFactory
import net.corda.libs.permissions.storage.writer.impl.PermissionStorageWriterProcessorImpl
import org.osgi.service.component.annotations.Component
import javax.persistence.EntityManagerFactory

@Component(service = [PermissionStorageWriterProcessorFactory::class])
class PermissionStorageWriterProcessorFactoryImpl : PermissionStorageWriterProcessorFactory {

    override fun create(entityManagerFactory: EntityManagerFactory): PermissionStorageWriterProcessor {
        return PermissionStorageWriterProcessorImpl(entityManagerFactory)
    }
}