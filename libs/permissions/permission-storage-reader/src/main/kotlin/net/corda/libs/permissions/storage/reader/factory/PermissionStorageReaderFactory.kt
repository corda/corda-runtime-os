package net.corda.libs.permissions.storage.reader.factory

import net.corda.libs.permissions.cache.PermissionCache
import net.corda.libs.permissions.storage.reader.PermissionStorageReader
import net.corda.messaging.api.publisher.Publisher
import javax.persistence.EntityManagerFactory

interface PermissionStorageReaderFactory {

    fun create(permissionCache: PermissionCache, publisher: Publisher, entityManagerFactory: EntityManagerFactory): PermissionStorageReader
}