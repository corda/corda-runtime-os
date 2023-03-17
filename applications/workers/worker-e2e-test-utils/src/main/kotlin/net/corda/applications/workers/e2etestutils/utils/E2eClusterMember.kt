package net.corda.applications.workers.e2etestutils.utils

import net.corda.v5.base.types.MemberX500Name
import org.assertj.core.api.Assertions.fail

data class E2eClusterMember(
    private val x500Name: String,
    val isNotary: Boolean = false
) {
    val name: String = MemberX500Name.parse(x500Name).toString()

    private var _holdingId: String? = null

    var holdingId: String
        get() = _holdingId
            ?: fail("Holding ID was never set for member. Virtual node must be created for member first.")
        set(value) {
            _holdingId = value
        }
}