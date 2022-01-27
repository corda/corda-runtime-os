package net.corda.introspiciere.junit

import net.corda.introspiciere.http.HelloWorldReq
import net.corda.introspiciere.http.IdentitiesRequester
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import java.io.File
import java.util.*

class DeployCluster(
    private val name: String,
) : BeforeAllCallback, BeforeEachCallback, AfterEachCallback, AfterAllCallback {

    private val introspiciereEndpoint = "http://localhost:7070"
    fun helloworld(): String {
        return HelloWorldReq(introspiciereEndpoint).greetings()
    }

    fun createKeyAndAddIdentity(alias: String, algorithm: String) {
        IdentitiesRequester(introspiciereEndpoint).createKeyAndAddIdentity(alias, algorithm)
    }

    override fun beforeAll(context: ExtensionContext?) {
        deploy()
    }

    override fun beforeEach(context: ExtensionContext?) {
        deploy()
    }

    override fun afterEach(context: ExtensionContext?) {
        delete()
    }

    override fun afterAll(context: ExtensionContext?) {
        delete()
    }

    private lateinit var portForwarding: Process

    private fun delete() {
        if (::portForwarding.isInitialized) portForwarding.destroy()
        exec("kubectl delete namespace $name --wait=false")
    }

    private fun deploy() {
        exec("kubectl create namespace $name")

        val username = System.getenv("CORDA_ARTIFACTORY_USERNAME")
        val password = System.getenv("CORDA_ARTIFACTORY_PASSWORD")
        val byteArray = "$username:$password".toByteArray()
        val auth = Base64.getEncoder().encodeToString(byteArray)

        val authsJson = File("auths.json")
        authsJson.writeText("""
            {
                "auths": {
                    "*.software.r3.com": {
                        "auth": "$auth"
                    }
                }
            } 
        """.trimIndent())
        exec("kubectl create secret generic test-docker-registry-cred " +
                "--from-file=.dockerconfigjson=auths.json --type=kubernetes.io/dockerconfigjson -n $name")
        authsJson.delete()

        exec("corda-cli cluster configure k8s $name")

        val simpleCluster = File("simple-cluster.yaml")
        simpleCluster.writeText("""
            cluster:
              zookeepers: 1
              brokers: 1
              workers:
                P2PWorker:
                P2PLinkWorker:
        """.trimIndent())
        exec("corda-cli cluster deploy -f simple-cluster.yaml -c $name | kubectl apply -f -")
        simpleCluster.delete()

        exec("corda-cli cluster status -c $name")

        exec("kubectl apply -f ../introspiciere-server/k8s-introspiciere-server.yaml -n $name")
        Thread.sleep(30000) // wait for pod to start running
        portForwarding = exec("kubectl port-forward service/introspiciere-server 7070:7070 -n $name", ensureSuccess = false)

        exec("corda-cli cluster wait -c $name")
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
                println("Process meant to keep running died:\n${processes.last().errorStream.bufferedReader().readText()}")
                throw CommandExecutionFailed(processes.last().exitValue())
            }
            println("  Process is still running...")
        }

        return processes.last()
    }

    class CommandExecutionFailed(exitValue: Int) : Exception("Command failed with exit value $exitValue")
}