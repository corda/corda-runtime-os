package com.example.securitymanager.two.flows

import net.corda.v5.application.flows.SubFlow
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component

@Component
class EnvironmentFlow
@Activate constructor() : SubFlow<Map<String, String> > {

    override fun call(): Map<String, String>  {
        return System.getenv()
    }
}

