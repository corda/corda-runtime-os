package net.corda.libs.virtualnode.maintenance.rpcops.impl.v1

class UnknownResponseTypeException(type: String) : Exception("Encountered a response of unknown type: $type")
