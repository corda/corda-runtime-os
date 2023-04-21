package net.corda.simulator.exceptions

import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name

class NoKeyGeneratedException(member: MemberX500Name) : CordaRuntimeException(
    "An attempt was made to sign data but no key has been generated for member \"$member\""
)