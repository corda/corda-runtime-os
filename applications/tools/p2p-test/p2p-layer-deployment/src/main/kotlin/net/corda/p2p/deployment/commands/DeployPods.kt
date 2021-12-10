package net.corda.p2p.deployment.commands

import net.corda.p2p.deployment.DeploymentException
import net.corda.p2p.deployment.pods.Pod

class DeployPods(
    private val namespaceName: String,
    private val pods: Collection<Pod>,
) :
    Runnable {
    override fun run() {
        val yamls = DeployYamls(pods.flatMap { it.yamls(namespaceName) })
        yamls.run()

        val runningPods = getPods()
        pods.forEach { pod ->
            pod.readyLog?.also {
                val wait = WaitForLogs(namespaceName, pod, runningPods, it)
                wait.run()
            }
        }
    }

    @Suppress("ThrowsCount")
    private fun getPods(): Collection<String> {
        repeat(300) {
            Thread.sleep(1000)
            val pods = ProcessRunner.execute(
                "kubectl",
                "get",
                "pod",
                "-n",
                namespaceName
            )
            val allPods = pods.lines()
                .drop(1)
                .map {
                    it.split("\\s+".toRegex())
                }.filter {
                    it.size > 3
                }.associate {
                    it[0] to it[2]
                }
            val badContainers = allPods.filterValues {
                it == "Error" || it == "CrashLoopBackOff" || it == "ErrImagePull" || it == "ImagePullBackOff"
            }
            if (badContainers.isNotEmpty()) {
                println("Error in ${badContainers.keys}")
                badContainers.keys.forEach {
                    ProcessRunner.follow(
                        "kubectl",
                        "describe",
                        "pod",
                        "-n",
                        namespaceName,
                        it
                    )
                    throw DeploymentException("Error in pods")
                }
            }
            val waitingFor = allPods.filterValues { it != "Running" }
            if (waitingFor.isEmpty()) {
                return allPods.keys
            } else {
                println("Waiting for:")
                waitingFor.forEach { (name, status) ->
                    println("\t $name ($status)")
                }
            }
        }
        throw DeploymentException("Waiting too long for $namespaceName")
    }
}
