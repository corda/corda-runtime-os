package net.corda.introspiciere.core

import org.junit.jupiter.api.extension.*
import java.io.File

class DeployKafka(
    private val name: String,
) : BeforeAllCallback, BeforeEachCallback, AfterEachCallback, AfterAllCallback {

    override fun beforeAll(context: ExtensionContext?) = deploy()
    override fun beforeEach(context: ExtensionContext?) = deploy()
    override fun afterEach(context: ExtensionContext?) = delete()
    override fun afterAll(context: ExtensionContext?) = delete()

    val client by lazy {
        SimpleKafkaClient(listOf("localhost:9092"))
    }

    private lateinit var portForwarding: Process

    private fun delete() {
        if (::portForwarding.isInitialized) portForwarding.destroy()
        exec("kubectl delete namespace $name --wait=false")
    }

    private fun deploy() {
        exec("kubectl create namespace $name")

        val proc = exec("kubectl apply -f - -n $name", ensureSuccess = false)

        proc.outputStream.bufferedWriter().use {
            it.write(this::class.java.getResource("/k8s-single-kafka-deployment.yaml").readText())
        }

        if (proc.waitFor() != 0) {
            throw CommandExecutionFailed(proc.exitValue())
        }
        Thread.sleep(5000) // wait for pod to start running

        exec("kubectl get all -n $name")

        portForwarding = exec("kubectl port-forward service/kafka-service 9092:9092 -n $name", ensureSuccess = false)
    }

    private fun exec(command: String, workDir: File? = null, ensureSuccess: Boolean = true): Process {
        println("Executing command:")
        println("  $command")
        if (workDir != null) println("  workdir: $workDir")

        val builders = command.split("|")
            .map { it.trim().split(" ") }
            .map { ProcessBuilder(it).directory(workDir) }
        val processes = ProcessBuilder.startPipeline(builders)

        if (ensureSuccess) {
            processes.last().waitFor()

            println("  Stdout:")
            val stdout = processes.last().inputStream.bufferedReader().readText()
            if (stdout.isEmpty()) println("    <empty>")
            else println(stdout.prependIndent("    "))

            println("  Stderr:")
            val stderr = processes.last().errorStream.bufferedReader().readText()
            if (stderr.isEmpty()) println("    <empty>")
            else println(stderr.prependIndent("    "))

            val exitValue = processes.last().waitFor()
            if (exitValue != 0) throw CommandExecutionFailed(exitValue)

        } else {
            Thread.sleep(5000)
            if (!processes.last().isAlive) {
                println("Process meant to keep running died:\n${
                    processes.last().errorStream.bufferedReader().readText()
                }")
                throw CommandExecutionFailed(processes.last().exitValue())
            }
            println("  Process is still running...")
        }

        return processes.last()
    }

    class CommandExecutionFailed(exitValue: Int) : Exception("Command failed with exit value $exitValue")
}