package net.corda.httprpc.server.impl.exception

class MissingParameterException(override val message: String) : Exception(message)