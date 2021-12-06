package net.corda.httprpc.tools.annotations.validation

import net.corda.httprpc.RpcOps

/**
 * This class is responsible for applying validations to an interface or list of interfaces that are expected to be exposed via HTTP RPC.
 * Interfaces that pass this validation successfully should be able to generate their HTTP RPC model with no errors.
 */
object HttpRpcInterfaceValidator {
    /**
     * Validates an interface
     *
     * @param rpcOpsInterface An interface class extending [RpcOps]
     * @return A validation result, containing a list of the errors. The validation was successful if the error list is empty.
     */
    @JvmStatic
    fun <T : RpcOps> validate(rpcOpsInterface: Class<T>): HttpRpcValidationResult =
        listOf(
            ResourceAnnotationValidator(rpcOpsInterface),
            EndpointAnnotationValidator(rpcOpsInterface),
            EndpointNameConflictValidator(rpcOpsInterface),
            ParameterAnnotationValidator(rpcOpsInterface),
            ParameterBodyAnnotationValidator(rpcOpsInterface),
            ParameterNameConflictValidator(rpcOpsInterface),
            ParameterClassTypeValidator(rpcOpsInterface),
            PathParameterInURLPathValidator(rpcOpsInterface),
            URLPathParameterNotDeclaredValidator(rpcOpsInterface),
            DurableStreamsEndPointValidator(rpcOpsInterface),
            DurableStreamsContextParameterValidator(rpcOpsInterface),
            NestedGenericsParameterTypeValidator(rpcOpsInterface)
        ).fold(HttpRpcValidationResult()) { total, next -> total + next.validate() }

    /**
     * Validates an interface
     *
     * @param rpcOpsInterfaces A list of interface classes extending [RpcOps][net.corda.core.messaging.RpcOps]
     * @return A validation result, containing a list of the errors. The validation was successful if the error list is empty.
     */
    @JvmStatic
    fun validate(rpcOpsInterfaces: List<Class<out RpcOps>>): HttpRpcValidationResult =
        listOf(
            ResourceNameConflictValidator(rpcOpsInterfaces)
        ).fold(HttpRpcValidationResult()) { total, nextValidation ->
            total + nextValidation.validate()
        } + rpcOpsInterfaces.fold(HttpRpcValidationResult()) { total, nextClass -> total + validate(nextClass) }
}

/**
 * The result of the validation process. The validation was successful only if the error list is empty.
 *
 * @property errors
 */
data class HttpRpcValidationResult(
    val errors: List<String> = emptyList()
) {
    operator fun plus(other: HttpRpcValidationResult): HttpRpcValidationResult {
        return HttpRpcValidationResult(errors + other.errors)
    }
}

internal interface HttpRpcValidator {
    fun validate(): HttpRpcValidationResult
}
