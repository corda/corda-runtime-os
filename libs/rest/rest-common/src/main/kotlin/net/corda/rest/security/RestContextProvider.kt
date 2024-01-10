package net.corda.rest.security

interface RestContextProvider {
    val principal: String
}

