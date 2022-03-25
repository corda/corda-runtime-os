package net.corda.libs.permissions.cache.impl.factory

import java.util.concurrent.ConcurrentHashMap
import net.corda.data.permissions.summary.UserPermissionSummary
import net.corda.libs.permissions.cache.PermissionValidationCache
import net.corda.libs.permissions.cache.factory.PermissionValidationCacheFactory
import net.corda.libs.permissions.cache.impl.PermissionValidationCacheImpl
import org.osgi.service.component.annotations.Component

@Component(service = [PermissionValidationCacheFactory::class])
class PermissionValidationCacheFactoryImpl : PermissionValidationCacheFactory {

    override fun createPermissionValidationCache(
        permissionSummaryData: ConcurrentHashMap<String, UserPermissionSummary>
    ): PermissionValidationCache {
        return PermissionValidationCacheImpl(permissionSummaryData)
    }
}