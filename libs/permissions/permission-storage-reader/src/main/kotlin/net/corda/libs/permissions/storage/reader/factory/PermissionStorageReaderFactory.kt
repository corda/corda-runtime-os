package net.corda.libs.permissions.storage.reader.factory

import net.corda.libs.permissions.validation.cache.PermissionValidationCache
import net.corda.libs.permissions.storage.reader.PermissionStorageReader
import net.corda.messaging.api.publisher.Publisher
import javax.persistence.EntityManagerFactory
import net.corda.libs.permissions.management.cache.PermissionManagementCache
import java.util.concurrent.atomic.AtomicReference

interface PermissionStorageReaderFactory {

    fun create(
        permissionValidationCacheRef: AtomicReference<PermissionValidationCache?>,
        permissionManagementCacheRef: AtomicReference<PermissionManagementCache?>,
        publisher: Publisher,
        entityManagerFactory: EntityManagerFactory
    ): PermissionStorageReader
}