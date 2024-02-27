package net.corda.libs.permissions.validation.cache.impl.factory

import net.corda.data.permissions.summary.UserPermissionSummary
import net.corda.libs.permissions.cache.impl.processor.PermissionTopicProcessor
import net.corda.libs.permissions.cache.processor.PermissionCacheTopicProcessor
import net.corda.libs.permissions.validation.cache.factory.PermissionValidationCacheTopicProcessorFactory
import org.osgi.service.component.annotations.Component
import java.util.concurrent.ConcurrentHashMap

@Component(service = [PermissionValidationCacheTopicProcessorFactory::class])
class PermissionValidationCacheTopicProcessorFactoryImpl : PermissionValidationCacheTopicProcessorFactory {

    override fun createPermissionSummaryTopicProcessor(
        permissionSummaryData: ConcurrentHashMap<String, UserPermissionSummary>,
        onSnapshotCallback: () -> Unit,
    ): PermissionCacheTopicProcessor<String, UserPermissionSummary> {
        return PermissionTopicProcessor(String::class.java, UserPermissionSummary::class.java, permissionSummaryData, onSnapshotCallback)
    }
}
