package net.corda.osgi.framework

import org.w3c.dom.Node
import org.w3c.dom.NodeList
import java.lang.instrument.Instrumentation
import javax.xml.parsers.DocumentBuilderFactory

object JavaAgentLauncher {

    private fun NodeList.asSequence() : Sequence<Node> {
        var index = 0
        return generateSequence {
            if (index < length) item(index++) as Node
            else null
        }
    }

    @JvmStatic
    fun premain(@Suppress("UNUSED_PARAMETER") agentArguments : String, instrumentation: Instrumentation) {
        val cl = JavaAgentLauncher.javaClass.classLoader
        val docBuilderFectory = DocumentBuilderFactory.newInstance()
        cl.getResources("META-INF/javaAgents.xml").asSequence().forEach { url ->
            val docBuilder = docBuilderFectory.newDocumentBuilder()
            val doc = url.openStream().use(docBuilder::parse)
            doc.childNodes.asSequence().flatMap { javaAgents ->
                if(javaAgents.nodeName != "javaAgents") {
                    throw IllegalStateException("Expected node with name 'javaAgents', got ${javaAgents.nodeName} instead")
                }
                javaAgents.childNodes.asSequence()
            }.filter{
                it.nodeType == Node.ELEMENT_NODE
            }.onEach {
                if(it.nodeName != "javaAgent") {
                    throw IllegalStateException("Expected node with name 'javaAgent', got ${it.nodeName} instead")
                }
            }.forEach { javaAgentNode ->
                val agentClassName = javaAgentNode.attributes.getNamedItem("class").nodeValue
                val agentArgs = javaAgentNode.textContent
                val agentClass = cl.loadClass(agentClassName)
                val premainMethod = agentClass.getMethod("premain", String::class.java, Instrumentation::class.java)
                premainMethod.invoke(null, agentArgs, instrumentation)
            }
        }
    }

    @JvmStatic
    fun agentmain(agentArguments : String, instrumentation: Instrumentation) = premain(agentArguments, instrumentation)
}