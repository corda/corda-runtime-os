package net.corda.components.test.internal

import net.corda.configuration.read.ConfigurationReadService
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.createCoordinator
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket

@Component(service = [TestService::class])
class TestService @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService
) : Lifecycle {

    private val coordinator = coordinatorFactory.createCoordinator<TestService>(::eventHandler)
    private fun eventHandler(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        logger.debug { "FlowService received: $event" }
        logger.debug { "FlowService received: ${coordinator.status}" }
        when (event) {
            is StartEvent -> {
                logger.info("TestService has started")
            }
        }
    }

    companion object {
        private val logger = contextLogger()

        // private val configSections = setOf(BOOT_CONFIG, MESSAGING_CONFIG, FLOW_CONFIG)
        private val configSections = emptySet<String>()
    }

    override val isRunning: Boolean
        get() = coordinator.isRunning

    override fun start() {
        logger.info("Trying to start")
        val socketServer = ServerSocket(4132)
        val clientSocket: Socket = socketServer.accept()
        val writer = PrintWriter(clientSocket.getOutputStream(), true)
        val reader = BufferedReader(
            InputStreamReader(clientSocket.getInputStream())
        )

        coordinator.start()

        var inputLine = reader.readLine()
        while (isRunning && inputLine != null) {
            writer.println(inputLine)
            println(inputLine)
            if (inputLine.equals("Bye.")) break
            inputLine = reader.readLine()
        }
    }

    override fun stop() {
        logger.info("Trying to stop")
        coordinator.stop()
    }
}
