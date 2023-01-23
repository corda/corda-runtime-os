package net.corda.httprpc.tools.annotations.validation

import net.corda.httprpc.RestResource

/**
 * This class is responsible for applying validations to an interface or list of interfaces that are expected to be exposed via HTTP RPC.
 * Interfaces that pass this validation successfully should be able to generate their HTTP RPC model with no errors.
 */
object RestInterfaceValidator {
    /**
     * Validates an interface
     *
     * @param rpcOpsInterface An interface class extending [RestResource]
     * @return A validation result, containing a list of the errors. The validation was successful if the error list is empty.
     */
    @JvmStatic
    fun <T : RestResource> validate(rpcOpsInterface: Class<T>): RestValidationResult =
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
        ).fold(RestValidationResult()) { total, next -> total + next.validate() }

    /**
     * Validates an interface
     *
     * @param restResourceInterfaces A list of interface classes extending [RestResource][net.corda.core.messaging.RestResource]
     * @return A validation result, containing a list of the errors. The validation was successful if the error list is empty.
     */
    @JvmStatic
    fun validate(restResourceInterfaces: List<Class<out RestResource>>): RestValidationResult =
        listOf(
            ResourceNameConflictValidator(restResourceInterfaces)
        ).fold(RestValidationResult()) { total, nextValidation ->
            total + nextValidation.validate()
        } + restResourceInterfaces.fold(RestValidationResult()) { total, nextClass -> total + validate(nextClass) }
}

/**
 * The result of the validation process. The validation was successful only if the error list is empty.
 *
 * @property errors
 */
data class RestValidationResult(
    val errors: List<String> = emptyList()
) {
    operator fun plus(other: RestValidationResult): RestValidationResult {
        return RestValidationResult(errors + other.errors)
    }
}

internal interface RestValidator {
    fun validate(): RestValidationResult
}
