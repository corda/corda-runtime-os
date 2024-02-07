package net.corda.e2etest.utilities.types

import net.corda.e2etest.utilities.SimpleResponse
import net.corda.e2etest.utilities.parseContextMap

/**
 * Simple data class representing member info returned from a REST API.
 * Includes functions for parsing commonly accessed data for tests.
 */
data class Member(
    val memberContext: Map<String, String>,
    val mgmContext: Map<String, String>
) {
    val name: String?
        get() = memberContext["corda.name"]

    val groupId: String?
        get() = memberContext["corda.groupId"]

    val isMgm: Boolean
        get() = mgmContext["corda.mgm"]?.let { it.toBoolean() } ?: false

    val status: String?
        get() = mgmContext["corda.status"]

    val serial: Long?
        get() = mgmContext["corda.serial"]?.toLong()
}

/**
 * Creates a list of [Member] from a [SimpleResponse].
 * Only works if the [SimpleResponse] is the response of a member lookup.
 */
fun SimpleResponse.jsonToMemberList(): List<Member> =
    toJson()["members"].map { json ->
        Member(
            json.get("memberContext").parseContextMap(),
            json.get("mgmContext").parseContextMap()
        )
    }

@Suppress("unused")
fun SimpleResponse.jsonToRegistrationContext(): Map<String, String> =
    toJson().firstOrNull()?.get("memberInfoSubmitted")?.get("data")?.parseContextMap() ?: emptyMap()