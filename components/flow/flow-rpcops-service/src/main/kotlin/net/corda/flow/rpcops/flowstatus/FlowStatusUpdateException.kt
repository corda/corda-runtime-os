package net.corda.flow.rpcops.flowstatus

class FlowStatusUpdateException(message: String, errors: List<String>) : Exception("$message\n$errors.")