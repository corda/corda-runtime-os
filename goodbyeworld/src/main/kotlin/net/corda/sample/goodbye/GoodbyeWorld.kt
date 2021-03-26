package net.corda.sample.goodbye

import net.corda.sample.api.hello.HelloWorld
import org.osgi.service.component.annotations.Reference

fun main() {
    // Some bootstrap code goes here

    GoodbyeWorld().callHelloWorld()
}

class GoodbyeWorld {

    
    @Reference
    var helloWorld: HelloWorld? = null

    fun callHelloWorld() {
        helloWorld?.sayHello() ?: println("We couldn't find hello world!!!")
    }

}
