@file:Suppress("unused")
package com.example.sandbox.cpk2

import com.example.sandbox.library.SandboxQuery
import net.corda.v5.application.flows.Flow
import org.osgi.framework.BundleEvent
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(name = "bundle-event.two.flow")
class BundleEventTwoFlow @Activate constructor(
    @Reference(target = "(component.name=sandbox.query)")
    private val sandboxQuery: SandboxQuery
) : Flow<List<BundleEvent>> {
    override fun call(): List<BundleEvent> {
        return sandboxQuery.getBundleEvents()
    }
}
