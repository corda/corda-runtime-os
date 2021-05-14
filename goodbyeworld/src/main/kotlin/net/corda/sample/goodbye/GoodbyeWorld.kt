package net.corda.sample.goodbye

import org.osgi.framework.BundleActivator
import org.osgi.framework.BundleContext
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Deactivate

@Component(immediate = true)
class GoodbyeWorld : BundleActivator {

    @Activate
    override fun start(context: BundleContext?) {
        println("net.corda.sample.goodbye.GoodbyeWorld START")
    }

    @Deactivate
    override fun stop(context: BundleContext?) {
        println("net.corda.sample.goodbye.GoodbyeWorld STOP")
    }

}

