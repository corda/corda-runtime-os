package net.corda.testing.driver.impl

import net.corda.testing.driver.DriverDSL
import net.corda.testing.driver.Framework
import net.corda.v5.base.types.MemberX500Name

interface DriverInternalDSL : DriverDSL {
    fun getFramework(x500Name: MemberX500Name): Framework
}
