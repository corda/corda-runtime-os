package net.corda.libs.permissions.cache.processor

import net.corda.data.permissions.Group
import net.corda.messaging.api.processor.CompactedProcessor

interface PermissionCacheGroupProcessor : CompactedProcessor<String, Group>