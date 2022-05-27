package com.example.securitymanager.one.flows

import net.corda.v5.application.flows.Flow
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component

@Component
class EnvironmentFlow
@Activate constructor() : Flow<Map<String, String> > {

    override fun call(): Map<String, String>  {
        return System.getenv()
    }
}

