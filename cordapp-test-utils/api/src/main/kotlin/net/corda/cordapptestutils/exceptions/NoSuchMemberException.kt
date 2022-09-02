package net.corda.cordapptestutils.exceptions

import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name

class NoSuchMemberException(name: MemberX500Name) : CordaRuntimeException(
    "No member with name \"${name}\" registered"
)
