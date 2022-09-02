package net.corda.cordapptestutils.exceptions

import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name

class NoRegisteredResponderException(member: MemberX500Name, protocol: String) : CordaRuntimeException(
    "No ResponderFlow has been uploaded for protocol \"$protocol\", member $member"
)