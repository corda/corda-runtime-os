package net.corda.libs.permissions.cache.processor

import net.corda.messaging.api.processor.CompactedProcessor

/**
 * Interface for topic processors that maintaining permission cache.
 */
interface PermissionCacheTopicProcessor<K : Any, V : Any> : CompactedProcessor<K, V>