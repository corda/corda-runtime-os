package net.corda.libs.permissions.validation.cache.factory

import net.corda.data.permissions.summary.UserPermissionSummary
import net.corda.libs.permissions.cache.processor.PermissionCacheTopicProcessor
import java.util.concurrent.ConcurrentHashMap

/**
 * Factory for creating topic processors for the permission validation cache.
 */
interface PermissionValidationCacheTopicProcessorFactory {

    /**
     * Create a topic processor for Permission Summaries.
     *
     * @param permissionSummaryData the instance of a ConcurrentHashMap holding the Permission Summary data.
     * @param onSnapshotCallback the callback invoked after snapshot has been received.
     */
    fun createPermissionSummaryTopicProcessor(
        permissionSummaryData: ConcurrentHashMap<String, UserPermissionSummary>,
        onSnapshotCallback: () -> Unit,
    ): PermissionCacheTopicProcessor<String, UserPermissionSummary>
}
