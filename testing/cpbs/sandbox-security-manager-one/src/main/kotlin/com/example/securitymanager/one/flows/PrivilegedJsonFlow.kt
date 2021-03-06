package com.example.securitymanager.one.flows

import com.example.securitymanager.two.util.JsonUtil
import net.corda.v5.application.flows.SubFlow
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component

@Component
class PrivilegedJsonFlow
@Activate constructor() : SubFlow<String> {
    private class Test(private val value: String)

    override fun call(): String {
        val test = Test("test")
        return JsonUtil.privilegedToJson(test)
    }
}

