package net.corda.simulator.exceptions

import net.corda.v5.base.exceptions.CordaRuntimeException

class NoProtocolAnnotationException(flowClass: Class<*>) : CordaRuntimeException(
    "No @InitiatingFlow or @InitiatedBy annotation found on flow class ${flowClass.simpleName}"
)
