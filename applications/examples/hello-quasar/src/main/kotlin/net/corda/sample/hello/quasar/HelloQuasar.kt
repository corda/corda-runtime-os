package net.corda.sample.hello.quasar

import co.paralleluniverse.fibers.DefaultFiberScheduler
import co.paralleluniverse.fibers.Fiber
import co.paralleluniverse.fibers.Suspendable
import co.paralleluniverse.fibers.instrument.JavaAgent
import co.paralleluniverse.io.serialization.ByteArraySerializer
import co.paralleluniverse.io.serialization.kryo.KryoSerializer
import co.paralleluniverse.strands.Strand
import net.corda.osgi.api.Application
import net.corda.osgi.api.Shutdown
import org.osgi.framework.FrameworkUtil
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory
import java.io.Serializable
import java.nio.file.Files

data class Person(
    val name: String,
    val surname: String,
    val age: Int
) : Serializable

@Component(immediate = true, service = [Application::class])
class HelloQuasar @Activate constructor(
    @Reference private val shutDownService: Shutdown) : Application {

    private companion object {
        private val log = LoggerFactory.getLogger(HelloQuasar::class.java)
    }

    init {
        log.info("Constructing ${javaClass.name}")
    }

    val serializer = Fiber.getFiberSerializer(false) as KryoSerializer
    private val fiberFile = Files.createTempFile(null, ".fiber")

    fun newFiber(): Fiber<String> {
        return object : Fiber<String>() {
            @Suspendable
            override fun run(): String {
                val p = Person("John", "Doe", 32)
                log.info("Fiber started")
                parkAndSerialize { f: Fiber<*>, _: ByteArraySerializer ->
                    Files.newOutputStream(fiberFile).use {
                        serializer.write(it, f)
                    }
                    log.debug("Fiber written to '$fiberFile'")
                }
                log.info("Fiber resumed")
                return p.toString()
            }
        }
    }


    override fun startup(args : Array<String>) {
        log.info("${JavaAgent::class.java.name} is active: ${JavaAgent.isActive()}")
        val fiber = newFiber().start()
        serializer.getKryo().classLoader = javaClass.classLoader
        while(true) {
            Thread.sleep(1000)
            if(fiber.state == Strand.State.WAITING) {
                log.info("Fiber state: ${fiber.state.name}")
                break
            } else {
                log.info("Fiber state: ${fiber.state.name}")
            }
        }
        val deserialized : Fiber<String> = Files.newInputStream(fiberFile).use {
            @Suppress("UNCHECKED_CAST")
            serializer.read(it) as Fiber<String>
        }
        Fiber.unparkDeserialized(deserialized, DefaultFiberScheduler.getInstance())
        log.info("Fiber return value: ${deserialized.get()}")
        shutDownService.shutdown(FrameworkUtil.getBundle(this::class.java))
    }

    override fun shutdown() {
    }
}
