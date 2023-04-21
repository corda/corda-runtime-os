package net.corda.simulator.exceptions

import net.corda.v5.base.exceptions.CordaRuntimeException

class ResponderFlowException(cause: Throwable) : CordaRuntimeException(
    "An error was encountered in the responding flow. Note that exceptions should not be used for " +
            "normal inter-flow communication; use a structure that can return an error result instead." +
            "Also note that real Corda will not pass on the cause exception " +
            "to avoid serializing secure information to peers.",
    cause
)