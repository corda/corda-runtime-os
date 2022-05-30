package com.example.securitymanager.one.flows

import net.corda.v5.application.flows.Flow
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component

@Component
class ReflectionFlow
@Activate constructor() : Flow<String> {
    private class Test(val value: String)

    override fun call(): String {
        val test = Test("test")
        return Test::class.java.getField("value").get(test) as String
    }
}

