package net.corda.p2p.deployment.commands

import net.corda.p2p.deployment.DeploymentException
import net.corda.p2p.deployment.pods.Pod
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class WaitForLogs(
    private val namespaceName: String,
    private val pod: Pod,
    private val runningPods: Collection<String>,
    private val waitTo: Regex,
) : Runnable {
    override fun run() {
        val podName = runningPods.firstOrNull { it.startsWith(pod.app) } ?: throw DeploymentException("Could not find pod ${pod.app}")
        println("Waiting for $podName...")
        val log = ProcessBuilder()
            .command(
                "kubectl",
                "logs",
                "-n", namespaceName,
                podName,
                "-f"
            ).start()
        val latch = CountDownLatch(1)
        thread {
            log.inputStream.reader().useLines { lines ->
                lines.forEach {
                    if (it.matches(waitTo)) {
                        latch.countDown()
                        return@thread
                    }
                }
            }
        }
        val failed = !latch.await(3, TimeUnit.MINUTES)
        log.destroy()
        if (failed) {
            throw DeploymentException("Waiting too long for $podName")
        }
    }
}
