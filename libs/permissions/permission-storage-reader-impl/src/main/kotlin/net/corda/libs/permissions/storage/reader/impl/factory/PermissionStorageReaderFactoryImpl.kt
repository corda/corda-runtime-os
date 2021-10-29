package net.corda.libs.permissions.storage.reader.impl.factory

import net.corda.libs.permissions.cache.PermissionCache
import net.corda.libs.permissions.storage.reader.PermissionStorageReader
import net.corda.libs.permissions.storage.reader.factory.PermissionStorageReaderFactory
import net.corda.libs.permissions.storage.reader.impl.PermissionStorageReaderImpl
import net.corda.messaging.api.publisher.Publisher
import org.osgi.service.component.annotations.Component
import javax.persistence.EntityManagerFactory

@Component(service = [PermissionStorageReaderFactory::class])
class PermissionStorageReaderFactoryImpl : PermissionStorageReaderFactory {

    override fun create(
        permissionCache: PermissionCache,
        entityManagerFactory: EntityManagerFactory,
        publisher: Publisher
    ): PermissionStorageReader {
        return PermissionStorageReaderImpl(permissionCache, entityManagerFactory, publisher)
    }
}