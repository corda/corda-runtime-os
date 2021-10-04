package net.corda.membership.impl

import net.corda.membership.GroupPolicy

class GroupPolicyImpl(
    override val entries: Set<Map.Entry<String, Any>>,
    override val keys: Set<String>,
    override val size: Int,
    override val values: Collection<Any>,
    override val networkType: String?
) : GroupPolicy {
    override fun containsKey(key: String): Boolean {
        TODO("Not yet implemented")
    }

    override fun containsValue(value: Any): Boolean {
        TODO("Not yet implemented")
    }

    override fun get(key: String): Any? {
        TODO("Not yet implemented")
    }

    override fun isEmpty(): Boolean {
        TODO("Not yet implemented")
    }
}
