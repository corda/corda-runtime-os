package com.r3.corda.evmbridge.web3j

import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.*
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.generated.Uint256
import java.math.BigInteger

data class ContractFunction(
    val methodName: String,
    val transaction: Boolean,
    val inputs: List<ContractFunctionInput>,
    val outputs: List<ContractFunctionOutput>
)

data class ContractFunctionInput(
    val name: String,
    val type: String,
    val value: String
)

data class ContractFunctionOutput(
    val type: String
)

fun ContractFunction.generateWeb3JFunction(): Function {
    val functionName = this.methodName
    val inputs = generateInputs(this.inputs)
    val outputs = generateOutputs(this.outputs)
    return Function(functionName, inputs, outputs)
}

fun generateInputs(inputs: List<ContractFunctionInput>): List<Type<*>> {
    return inputs.map {
        when(it.type) {
            "uint256" -> Uint256(it.value.toBigInteger())
            "bool" -> Bool(it.value.toBoolean())
            "address" -> Address(it.value)
            "string" -> Utf8String(it.value)
            else -> throw IllegalArgumentException("Invalid or unsupported EVM data type ${it.type}")
        }
    }
}

fun generateOutputs(outputs: List<ContractFunctionOutput>): List<TypeReference<out Type<*>>> {
    return outputs.map {
        when(it.type) {
            "uint256" -> object : TypeReference<Uint256>() {}
            "bool" -> object : TypeReference<Bool>() {}
            "address" -> object: TypeReference<Address>() {}
            "string" -> object : TypeReference<Utf8String>() {}
            else -> throw IllegalArgumentException("Invalid or unsupported EVM data type ${it.type}")
        }
    }
}

fun getReturnType(outputs: List<ContractFunctionOutput>): List<Class<*>> {
    return outputs.map {
        when(it.type) {
            "uint256" -> BigInteger::class.java
            "bool" -> Boolean::class.java
            "address" -> String::class.java
            "string" -> String::class.java
            else -> throw IllegalArgumentException("Invalid or unsupported EVM data type ${it.type}")
        }
    }
}