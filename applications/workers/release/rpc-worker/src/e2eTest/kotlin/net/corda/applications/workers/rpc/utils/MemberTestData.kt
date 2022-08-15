package net.corda.applications.workers.rpc.utils

import net.corda.v5.base.types.MemberX500Name

data class MemberTestData(
    private val x500Name: String
) {
    val name: String = MemberX500Name.parse(x500Name).toString()
}