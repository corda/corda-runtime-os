package net.corda.simulator.exceptions

import net.corda.v5.base.exceptions.CordaRuntimeException

class NoDefaultConstructorException(flowClass: Class<*>) : CordaRuntimeException(
    "No default constructor found on flow class ${flowClass.simpleName}"
)
