package net.corda.httprpc.exception

class ResourceNotFoundException(resource: String, id: String) : HttpApiException("$resource $id not found.")