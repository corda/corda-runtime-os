package net.corda.libs.permissions.storage.reader.impl.factory

import net.corda.libs.permissions.management.cache.PermissionManagementCache
import net.corda.libs.permissions.storage.reader.PermissionStorageReader
import net.corda.libs.permissions.storage.reader.factory.PermissionStorageReaderFactory
import net.corda.libs.permissions.storage.reader.impl.PermissionStorageReaderImpl
import net.corda.libs.permissions.storage.reader.impl.repository.PermissionRepositoryImpl
import net.corda.libs.permissions.storage.reader.impl.summary.PermissionSummaryReconcilerImpl
import net.corda.libs.permissions.validation.cache.PermissionValidationCache
import net.corda.messaging.api.publisher.Publisher
import org.osgi.service.component.annotations.Component
import java.util.concurrent.atomic.AtomicReference
import javax.persistence.EntityManagerFactory

@Component(service = [PermissionStorageReaderFactory::class])
class PermissionStorageReaderFactoryImpl : PermissionStorageReaderFactory {

    override fun create(
        permissionValidationCacheRef: AtomicReference<PermissionValidationCache?>,
        permissionManagementCacheRef: AtomicReference<PermissionManagementCache?>,
        publisher: Publisher,
        entityManagerFactory: EntityManagerFactory
    ): PermissionStorageReader {
        return PermissionStorageReaderImpl(
            permissionValidationCacheRef,
            permissionManagementCacheRef,
            PermissionRepositoryImpl(entityManagerFactory),
            publisher,
            PermissionSummaryReconcilerImpl()
        )
    }
}
