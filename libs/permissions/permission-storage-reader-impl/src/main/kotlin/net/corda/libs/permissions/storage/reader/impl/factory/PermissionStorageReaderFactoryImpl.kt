package net.corda.libs.permissions.storage.reader.impl.factory

import net.corda.libs.permissions.cache.PermissionValidationCache
import net.corda.libs.permissions.storage.reader.PermissionStorageReader
import net.corda.libs.permissions.storage.reader.factory.PermissionStorageReaderFactory
import net.corda.libs.permissions.storage.reader.impl.PermissionStorageReaderImpl
import net.corda.libs.permissions.storage.reader.impl.repository.PermissionRepositoryImpl
import net.corda.messaging.api.publisher.Publisher
import org.osgi.service.component.annotations.Component
import javax.persistence.EntityManagerFactory
import net.corda.libs.permissions.cache.PermissionManagementCache
import net.corda.libs.permissions.storage.reader.impl.summary.PermissionSummaryReconcilerImpl

@Component(service = [PermissionStorageReaderFactory::class])
class PermissionStorageReaderFactoryImpl : PermissionStorageReaderFactory {

    override fun create(
        permissionValidationCache: PermissionValidationCache,
        permissionManagementCache: PermissionManagementCache,
        publisher: Publisher,
        entityManagerFactory: EntityManagerFactory
    ): PermissionStorageReader {

        return PermissionStorageReaderImpl(
            permissionValidationCache,
            permissionManagementCache,
            PermissionRepositoryImpl(entityManagerFactory),
            publisher,
            PermissionSummaryReconcilerImpl()
        )
    }
}