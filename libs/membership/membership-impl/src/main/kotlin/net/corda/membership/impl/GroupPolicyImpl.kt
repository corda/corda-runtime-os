package net.corda.membership.impl

import net.corda.membership.GroupPolicy
import net.corda.membership.impl.GroupPolicyExtension.Companion.GROUP_ID

class GroupPolicyImpl(private val map: Map<String, Any>) : GroupPolicy {
    override val groupId: String
        get() = map[GROUP_ID].toString()
    override val entries: Set<Map.Entry<String, Any>>
        get() = map.entries
    override val keys: Set<String>
        get() = map.keys
    override val size: Int
        get() = map.size
    override val values: Collection<Any>
        get() = map.values

    override fun containsKey(key: String): Boolean = keys.contains(key)

    override fun containsValue(value: Any): Boolean = values.contains(value)

    override fun get(key: String): Any? = map[key]

    override fun isEmpty(): Boolean = size == 0
}
