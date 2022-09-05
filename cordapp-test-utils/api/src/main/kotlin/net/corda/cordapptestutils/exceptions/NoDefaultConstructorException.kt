package net.corda.cordapptestutils.exceptions

import net.corda.v5.base.exceptions.CordaRuntimeException

class NoDefaultConstructorException(flowClass: Class<*>) : CordaRuntimeException(
    "No default constructor found on flow class ${flowClass.simpleName}"
)
