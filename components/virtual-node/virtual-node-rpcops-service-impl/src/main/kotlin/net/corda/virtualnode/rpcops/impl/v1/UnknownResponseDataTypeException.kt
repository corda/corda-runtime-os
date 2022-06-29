package net.corda.virtualnode.rpcops.impl.v1

class UnknownResponseDataTypeException(type: String) : Exception("Encountered a response containing data unknown type: $type")