package net.corda.simulator.exceptions

class ServiceConfigurationException(clazz: Class<*>) : Exception(
    "Could not load service implementation for $clazz. Please make sure you have a runtime dependency on " +
            "the net.corda.simulator:simulator-runtime component."
)