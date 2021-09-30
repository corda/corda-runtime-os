package net.corda.membership.impl

import net.corda.v5.application.node.MemberContext
import org.apache.commons.lang3.builder.HashCodeBuilder
import java.util.SortedMap

class MemberContextImpl(private val properties: SortedMap<String, String>) : MemberContext {
    override operator fun get(key: String): String? = properties[key]

    @Transient
    override val keys: Set<String> = properties.keys

    override val entries: Set<Map.Entry<String, String>> = properties.entries

    override fun toString(): String = StringBuilder().apply {
        append("MemberContext {\n")
        properties.forEach { (k, v) -> append("$k=$v\n") }
        append("}")
    }.toString()

    override fun equals(other: Any?): Boolean {
        if (other == null || other !is MemberContextImpl) return false
        if (this === other) return true
        return this.properties == other.properties
    }

    override fun hashCode() = HashCodeBuilder(71, 97).apply {
        properties.forEach { (k, v) -> append(k); append(v) }
    }.toHashCode()
}