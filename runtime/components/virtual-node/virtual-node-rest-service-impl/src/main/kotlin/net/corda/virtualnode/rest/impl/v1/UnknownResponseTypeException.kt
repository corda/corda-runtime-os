package net.corda.virtualnode.rest.impl.v1

class UnknownResponseTypeException(type: String) : Exception("Encountered a response of unknown type: $type")
