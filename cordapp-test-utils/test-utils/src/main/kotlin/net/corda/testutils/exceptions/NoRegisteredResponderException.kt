package net.corda.testutils.exceptions

import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name

class NoRegisteredResponderException(x500: MemberX500Name, protocol: String) : CordaRuntimeException(
    "No ResponderFlow has been uploaded for protocol \"$protocol\", member $x500")