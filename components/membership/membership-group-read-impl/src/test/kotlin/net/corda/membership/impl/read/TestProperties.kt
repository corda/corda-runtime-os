package net.corda.membership.impl.read

import net.corda.v5.base.types.MemberX500Name

class TestProperties {
    companion object {
        const val GROUP_ID_1 = "ABC-123"
        const val GROUP_ID_2 = "DEF-456"

        val aliceName get() = x500Name("Alice")
        val bobName get() = x500Name("Bob")
        val charlieName get() = x500Name("Charlie")

        private fun x500Name(
            org: String,
            locality: String = "London",
            country: String = "GB"
        ) = MemberX500Name(org, locality, country)
    }
}