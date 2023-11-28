package com.r3.corda.testing.bundle1

import net.corda.v5.base.annotations.CordaSerializable

@CordaSerializable
data class Cash(val amount: Int)
