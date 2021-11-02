package com.example.sandbox.cpk1

import org.osgi.service.component.annotations.Component
import java.util.function.Function

/** Retrieves the calling sandbox group. */
@Suppress("unused")
@Component(name = "get.calling.sandbox.group.function")
class GetCallingSandboxGroupFunction : Function<Any, Any> {
    override fun apply(sandboxContextService: Any): Any {
        val method = sandboxContextService::class.java.getMethod("getCallingSandboxGroup")
        return method.invoke(sandboxContextService)
    }
}
