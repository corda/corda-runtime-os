package net.corda.libs.permissions.cache.processor

import net.corda.data.permissions.User
import net.corda.messaging.api.processor.CompactedProcessor

interface PermissionCacheUserProcessor : CompactedProcessor<String, User>