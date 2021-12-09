package net.corda.cli.application

import net.corda.cli.api.serviceUsers.HttpServiceUser
import net.corda.cli.api.serviceUsers.ServiceUser
import net.corda.cli.application.services.HttpRpcService

class ServiceManager(private val serviceUsers: List<ServiceUser>) {

    private val httpService = HttpRpcService()

    // Load services based on the required types
    fun loadServices() {
        serviceUsers.forEach { serviceUser ->
            if (serviceUser is HttpServiceUser) {
                serviceUser.setHttpService(httpService)
            }
        }
    }
}