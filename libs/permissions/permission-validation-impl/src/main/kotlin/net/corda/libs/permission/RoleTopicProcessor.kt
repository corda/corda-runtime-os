package net.corda.libs.permission

import net.corda.data.permissions.Role
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import net.corda.v5.base.annotations.VisibleForTesting
import java.util.*

class RoleTopicProcessor : CompactedProcessor<String, Role> {

    @VisibleForTesting
    internal val roleData: MutableMap<String, Role> = Collections.synchronizedMap(mutableMapOf())

    fun getRole(id: String) = roleData[id]

    override val keyClass = String::class.java
    override val valueClass = Role::class.java

    override fun onSnapshot(currentData: Map<String, Role>) {
        roleData.putAll(currentData)
    }

    override fun onNext(
            newRecord: Record<String, Role>,
            oldValue: Role?,
            currentData: Map<String, Role>
    ) {
        val role = newRecord.value
        val roleId = newRecord.key

        if (role == null) {
            roleData.remove(roleId)
        } else {
            roleData[roleId] = role
        }
    }
}