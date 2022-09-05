package net.corda.cordapptestutils.exceptions

class ServiceConfigurationException(clazz: Class<*>) : Exception(
    "Could not load service implementation for $clazz. Please make sure you have a runtime dependency on " +
            "the cordapp-test-utils:test-utils component."
)