package com.example.securitymanager.two.flows

import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.PropertyAccessor
import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.v5.application.flows.Flow
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component

@Component
class JsonFlow
@Activate constructor() : Flow<String> {
    private class Test(private val value: String)

    override fun call(): String {
        val test = Test("test")
        val mapper = ObjectMapper()
        mapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY)
        return mapper.writeValueAsString(test)
    }
}

