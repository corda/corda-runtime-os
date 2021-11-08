package com.example.sandbox.cpk1

import com.example.sandbox.library.SandboxQuery
import net.corda.v5.application.flows.Flow
import org.osgi.framework.BundleEvent
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

/** Returns [BundleEvent]s visible to this sandbox. */
@Suppress("unused")
@Component(name = "bundle-event.one.flow")
class BundleEventOneFlow @Activate constructor(
    @Reference(target = "(component.name=sandbox.query)")
    private val sandboxQuery: SandboxQuery
) : Flow<List<BundleEvent>> {
    override fun call() = sandboxQuery.getBundleEvents()
}
