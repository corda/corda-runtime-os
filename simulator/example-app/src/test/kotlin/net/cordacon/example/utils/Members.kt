package net.cordacon.example.utils

import net.corda.v5.base.types.MemberX500Name

/**
 * Creates a MemberX500 with the given common name
 */
fun createMember(commonName: String, orgUnit: String = "ExampleUnit", org: String = "ExampleOrg") : MemberX500Name =
    MemberX500Name.parse("CN=$commonName, OU=$orgUnit, O=$org, L=London, C=GB")
