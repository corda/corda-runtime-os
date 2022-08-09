package net.corda.testutils.exceptions

import net.corda.v5.application.flows.Flow
import net.corda.v5.base.exceptions.CordaRuntimeException

class UnrecognizedFlowClassException(flowClass: Class<*>, acceptableClasses: List<Class<out Flow>>)
    : CordaRuntimeException("Flow class ${flowClass.simpleName} was not recognized. It should extend one of: " +
        acceptableClasses.joinToString(","))
