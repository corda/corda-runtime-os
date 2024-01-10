package net.corda.libs.permissions.storage.reader.factory

import net.corda.libs.permissions.management.cache.PermissionManagementCache
import net.corda.libs.permissions.storage.reader.PermissionStorageReader
import net.corda.libs.permissions.validation.cache.PermissionValidationCache
import net.corda.messaging.api.publisher.Publisher
import java.util.concurrent.atomic.AtomicReference
import javax.persistence.EntityManagerFactory

interface PermissionStorageReaderFactory {

    fun create(
        permissionValidationCacheRef: AtomicReference<PermissionValidationCache?>,
        permissionManagementCacheRef: AtomicReference<PermissionManagementCache?>,
        publisher: Publisher,
        entityManagerFactory: EntityManagerFactory
    ): PermissionStorageReader
}
