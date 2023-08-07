package com.example.messenger

import net.corda.v5.application.flows.SubFlow
import net.corda.v5.testing.PlatformMessageProvider
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Suppress("unused")
@Component
class PlatformMessengerFlow @Activate constructor(
    @Reference(target = "(corda.sandbox=*)")
    private val messageProvider: PlatformMessageProvider
): SubFlow<String> {
    override fun call(): String {
        return messageProvider.message
    }
}
