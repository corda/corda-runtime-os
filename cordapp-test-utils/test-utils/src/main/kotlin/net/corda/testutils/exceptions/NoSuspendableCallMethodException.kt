package net.corda.testutils.exceptions

import net.corda.v5.base.exceptions.CordaRuntimeException

class NoSuspendableCallMethodException(flowClass: Class<*>) : CordaRuntimeException(
    "The call method for flow class $flowClass is missing a @Suspendable annotation"
)
