package net.corda.libs.permissions.cache.impl.factory

import java.util.concurrent.ConcurrentHashMap
import net.corda.data.permissions.Group
import net.corda.data.permissions.Role
import net.corda.data.permissions.User
import net.corda.libs.permissions.cache.factory.PermissionCacheTopicProcessorFactory
import net.corda.libs.permissions.cache.impl.processor.PermissionTopicProcessor
import net.corda.libs.permissions.cache.processor.PermissionCacheTopicProcessor
import org.osgi.service.component.annotations.Component

@Component(service = [PermissionCacheTopicProcessorFactory::class])
class PermissionCacheTopicProcessorFactoryImpl : PermissionCacheTopicProcessorFactory {
    override fun createUserTopicProcessor(
        userData: ConcurrentHashMap<String, User>,
        onSnapshotCallback: () -> Unit,
    ): PermissionCacheTopicProcessor<String, User> {
        return PermissionTopicProcessor(String::class.java, User::class.java, userData, onSnapshotCallback)
    }

    override fun createGroupTopicProcessor(
        groupData: ConcurrentHashMap<String, Group>,
        onSnapshotCallback: () -> Unit,
    ): PermissionCacheTopicProcessor<String, Group> {
        return PermissionTopicProcessor(String::class.java, Group::class.java, groupData, onSnapshotCallback)
    }

    override fun createRoleTopicProcessor(
        roleData: ConcurrentHashMap<String, Role>,
        onSnapshotCallback: () -> Unit,
    ): PermissionCacheTopicProcessor<String, Role> {
        return PermissionTopicProcessor(String::class.java, Role::class.java, roleData, onSnapshotCallback)
    }
}