package net.corda.simulator.runtime.flows

import net.corda.v5.application.flows.FlowContextProperties
import net.corda.v5.application.flows.RPCStartableFlow

/**
 * Converts [FlowContextProperties] to [String]
 */
fun convertFlowContextPropertiesToString(flowContextProperties: FlowContextProperties, numberOfKeys: Int): String {
    val list = 1.rangeTo(numberOfKeys).map {
        "key-$it : ".plus(flowContextProperties.get("key-$it"))
    }
    return list.joinToString(", ")
}

/**
 * Build a response string from a map of [String] to [FlowContextProperties] to return from a [RPCStartableFlow]
 */
fun buildResponseString(keys: List<String>, contextPropertiesMap: Map<String, FlowContextProperties>): String {

    val list = keys.indices.map {
        keys[it] + " : { " + contextPropertiesMap[keys[it]]?.let { it1 ->
            convertFlowContextPropertiesToString(
                it1, 4
            )
        } + " } "
    }

    return list.joinToString(System.lineSeparator())
}