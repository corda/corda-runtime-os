package net.corda.p2p.deployment

fun getAndCheckEnv(variableName: String) =
    System.getenv(variableName)
        ?: throw DeploymentException("Please set the $variableName environment variable")
