package net.corda.simulator.runtime.testutils

import net.corda.v5.base.types.MemberX500Name

/**
 * Creates a MemberX500 with the given common name
 */
fun createMember(commonName: String) : MemberX500Name =
    MemberX500Name.parse("CN=$commonName, OU=ExampleUnit, O=ExampleOrg, L=London, C=GB")
