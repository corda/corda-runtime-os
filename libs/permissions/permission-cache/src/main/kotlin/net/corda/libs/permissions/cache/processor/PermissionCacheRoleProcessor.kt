package net.corda.libs.permissions.cache.processor

import net.corda.data.permissions.Role
import net.corda.messaging.api.processor.CompactedProcessor

interface PermissionCacheRoleProcessor : CompactedProcessor<String, Role>