package net.corda.libs.permissions.storage.reader.factory

import net.corda.libs.permissions.validation.cache.PermissionValidationCache
import net.corda.libs.permissions.storage.reader.PermissionStorageReader
import net.corda.messaging.api.publisher.Publisher
import javax.persistence.EntityManagerFactory
import net.corda.libs.permissions.management.cache.PermissionManagementCache

interface PermissionStorageReaderFactory {

    fun create(
        permissionValidationCache: PermissionValidationCache,
        permissionManagementCache: PermissionManagementCache,
        publisher: Publisher,
        entityManagerFactory: EntityManagerFactory
    ): PermissionStorageReader
}