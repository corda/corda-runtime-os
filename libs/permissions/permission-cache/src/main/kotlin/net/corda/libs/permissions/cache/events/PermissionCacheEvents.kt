package net.corda.libs.permissions.cache.events

import net.corda.lifecycle.LifecycleEvent

class UserTopicSnapshotReceived : LifecycleEvent
class GroupTopicSnapshotReceived : LifecycleEvent
class RoleTopicSnapshotReceived : LifecycleEvent
class PermissionTopicSnapshotReceived : LifecycleEvent