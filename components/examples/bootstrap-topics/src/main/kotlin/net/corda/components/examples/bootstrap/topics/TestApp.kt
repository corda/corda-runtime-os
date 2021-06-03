package net.corda.components.examples.bootstrap.topics

import net.corda.osgi.api.Application

//@Component(immediate = true)
class TestApp  : Application {


    override fun startup(args: Array<String>) {
        println("BEEEEEEEEEEEEEEEEEEES")
    }

    override fun shutdown() {
        TODO("Not yet implemented")
    }
}