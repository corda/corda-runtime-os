package com.r3.corda.evmbridge.web3j

import java.math.BigInteger
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Bool
import org.web3j.abi.datatypes.DynamicStruct
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.Type
import org.web3j.abi.datatypes.Utf8String
import org.web3j.abi.datatypes.generated.Uint256

data class ContractFunction(
    val methodName: String,
    val transaction: Boolean,
    val inputs: List<SmartContract.Input>,
    val outputs: List<ContractFunctionOutput>
)

data class ContractFunctionInput(
    val name: String,
    val type: String,
    val value: Any?
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

@Suppress("UNCHECKED_CAST")
fun generateInputs(inputs: List<SmartContract.Input>): List<Type<*>> {
    return inputs.map {
        when(it.type) {
            "uint256" -> Uint256(it.value as Long)
            "bool" -> Bool(it.value as Boolean)
            "address" -> Address(it.value as String)
            "string" -> Utf8String(it.value as String)
            "tuple" -> {
                DynamicStruct(generateInputs(it.value as List<SmartContract.Input>))
            }
            else -> throw IllegalArgumentException("Invalid or unsupported EVM data type ${it.type}: $it")
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
            "tuple" -> {
                object : TypeReference<DynamicStruct>() {} //(generateInputs(it.value as List<SmartContract.Input>))
            }
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