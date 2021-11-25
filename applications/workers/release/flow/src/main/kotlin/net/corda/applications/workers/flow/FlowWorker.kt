package net.corda.applications.workers.flow

import net.corda.osgi.api.Application
import org.osgi.service.component.annotations.Component

@Component(service = [Application::class])
@Suppress("unused")
class FlowWorker: Application {
    override fun startup(args: Array<String>) {
        println("jjj hello from flow worker")
    }

    override fun shutdown() {
        println("jjj goodbye from flow worker")
    }
}