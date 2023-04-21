package net.corda.e2etest.utilities.types

import net.corda.e2etest.utilities.SimpleResponse
import net.corda.e2etest.utilities.parseContextMap

/**
 * Simple data class representing group parameters returned from a REST API.
 * Includes functions for parsing commonly accessed data for tests.
 *
 * @param context the full string-string context map returned by the rest API representing the group parameters
 */
data class GroupParameters(val context: Map<String, String>) {
    private companion object {
        const val NOTARY_PREFIX = "corda.notary.service."
    }

    val epoch: String?
        get() = context["corda.epoch"]

    val notaries: List<Notary>
        get() = parseNotaries()

    private fun parseNotaries(): List<Notary> {
        val notaryServiceIndexes = context.filter {
            it.key.startsWith(NOTARY_PREFIX)
        }.map {
            it.key.substringAfter(NOTARY_PREFIX).substringBefore(".")
        }.toSet()
        return notaryServiceIndexes.map { serviceIdx ->
            val notaryServicePrefix = NOTARY_PREFIX + serviceIdx
            Notary(
                context["$notaryServicePrefix.name"],
                context["$notaryServicePrefix.flow.protocol.name"],
                context.filter {
                    it.key.startsWith("$notaryServicePrefix.flow.protocol.version")
                }.map { it.value },
                context.filter {
                    it.key.startsWith("$notaryServicePrefix.keys")
                }.map { it.value },
            )
        }
    }
}

/**
 * Creates [GroupParameters] from a [SimpleResponse].
 * Only works if the [SimpleResponse] is the response of a group parameters lookup.
 */
fun SimpleResponse.jsonToGroupParameters(): GroupParameters = GroupParameters(toJson().parseContextMap())