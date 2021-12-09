package net.corda.cli.api.services

interface HttpService : CliService {

    fun get(): String
    fun put(): String
    fun patch(): String
    fun post(): String
    fun delete(): String

}