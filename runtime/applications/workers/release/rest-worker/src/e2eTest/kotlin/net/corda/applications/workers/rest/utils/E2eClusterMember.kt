package net.corda.applications.workers.rest.utils

import net.corda.v5.base.types.MemberX500Name
import org.assertj.core.api.Assertions.fail

enum class E2eClusterMemberRole {
    NOTARY
}

data class E2eClusterMember(
    private val x500Name: String,
    val roles: List<E2eClusterMemberRole> = emptyList()
) {
    constructor(x500Name: String, role: E2eClusterMemberRole): this(x500Name, listOf(role))

    val name: String = MemberX500Name.parse(x500Name).toString()

    private var _holdingId: String? = null

    var holdingId: String
        get() = _holdingId
            ?: fail("Holding ID was never set for member. Virtual node must be created for member first.")
        set(value) {
            _holdingId = value
        }
}

fun E2eClusterMember.isNotary() = roles.contains(E2eClusterMemberRole.NOTARY)