package net.corda.rest.security

class RestContextProviderImpl : RestContextProvider {
    override val principal:String get() = CURRENT_REST_CONTEXT.get().principal
}