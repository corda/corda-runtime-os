package net.corda.httprpc.exception

class ResourceNotFoundException(resource: String, id: String, statusCode: Int) : HttpApiException("$resource $id not found.", statusCode) {
    constructor(resource: String, id: String) : this(resource, id, 404)
}