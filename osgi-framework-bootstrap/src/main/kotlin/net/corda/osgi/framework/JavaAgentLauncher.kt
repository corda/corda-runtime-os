package net.corda.osgi.framework

import java.lang.instrument.Instrumentation
import java.util.Properties

object JavaAgentLauncher {

    @JvmStatic
    fun premain(@Suppress("UNUSED_PARAMETER") agentArguments : String, instrumentation: Instrumentation) {
        val cl = JavaAgentLauncher::class.java.classLoader
        cl.getResources("META-INF/javaAgents.properties").asSequence().forEach { url ->
            val properties = Properties()
            url.openStream().use(properties::load)
            properties.entries.forEach { entry ->
                val agentClassName = entry.key as String
                val agentArgs = entry.value as String
                val agentClass = Class.forName(agentClassName, false, cl)
                val premainMethod = agentClass.getMethod("premain", String::class.java, Instrumentation::class.java)
                premainMethod.invoke(null, agentArgs, instrumentation)
            }
        }
    }

    @JvmStatic
    fun agentmain(agentArguments : String, instrumentation: Instrumentation) = premain(agentArguments, instrumentation)
}
