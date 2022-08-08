package net.corda.testutils.exceptions

import net.corda.v5.base.exceptions.CordaRuntimeException

class NoInitiatingFlowAnnotationException(flowClass: Class<*>) : CordaRuntimeException(
    "No @InitiatingFlow annotation found on flow class ${flowClass.simpleName}")
