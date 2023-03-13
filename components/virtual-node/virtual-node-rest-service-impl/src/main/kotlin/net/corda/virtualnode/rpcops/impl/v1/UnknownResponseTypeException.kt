package net.corda.virtualnode.rpcops.impl.v1

class UnknownResponseTypeException(type: String) : Exception("Encountered a response of unknown type: $type")
