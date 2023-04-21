package net.corda.cli.api.services

interface HttpService {
    fun get(endpoint: String)
    fun put(endpoint: String, jsonBody: String)
    fun patch(endpoint: String, jsonBody: String)
    fun post(endpoint: String, jsonBody: String)
    fun delete(endpoint: String)

    var username: String?
    var password: String?
    var url: String?
}