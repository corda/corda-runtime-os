package net.corda.httprpc.server.exception

class MissingParameterException(override val message: String) : Exception(message)