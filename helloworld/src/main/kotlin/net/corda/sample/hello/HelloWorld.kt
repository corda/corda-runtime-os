package net.corda.sample.hello

import net.corda.v5.base.util.contextLogger


fun main() {
    val helloWorld = HelloWorld()
    helloWorld.logHello()
}

class HelloWorld  {
    companion object {
        private val log = contextLogger()
    }

    fun logHello() {
        log.info("Hello world!")
    }
}
