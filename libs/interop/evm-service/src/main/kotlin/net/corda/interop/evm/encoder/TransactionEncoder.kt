package net.corda.interop.evm.encoder

import java.math.BigInteger
import net.corda.data.interop.evm.request.Parameter
import org.web3j.abi.TypeEncoder
import org.web3j.abi.datatypes.DynamicArray
import org.web3j.abi.datatypes.DynamicBytes
import org.web3j.abi.datatypes.DynamicStruct
import org.web3j.abi.datatypes.NumericType
import org.web3j.abi.datatypes.StaticArray
import org.web3j.abi.datatypes.Type
import org.web3j.abi.datatypes.Ufixed
import org.web3j.abi.datatypes.Uint
import org.web3j.abi.datatypes.Utf8String
import org.web3j.crypto.Hash
import org.web3j.utils.Numeric


/**
 * TransactionEncoder Class
 *
 * This class facilitates encoding transactions & calls for the evm
 *
 */
class TransactionEncoder {


    /**
     * Starts the encoding process by encoding the function signature and its inputs
     *
     * @param params Takes the Parameter specified in the Corda API.
     * @return The encoded function data that can be sending a transaction
     */
    fun encode(function: String, params: List<Parameter>): String {
        val (contractInput, typeSpecificParams) = params.map { it ->
            // if it.type has []
            if (it.type.contains("[]")) {
                // remove the [] and split by ,
                val type = it.type.replace("[]", "")
                val values = it.value.toString().replace(Regex("[\\[\\]]"), "").split(",")
                Pair(
                    ABIContractInput(it.name, it.type, null),
                    values.map { AbiConverter.getType(type, it) }
                )
            } else {
                Pair(
                    ABIContractInput(it.name, it.type, null),
                    AbiConverter.getType(it.type, it.value.toString())
                )
            }
        }.unzip()
        return encodeFunctionSignature(ABIContractFunction(contractInput, function)) +
                encodeParameters(typeSpecificParams)
    }

    /**
     * Responsible for Encoding the Function signature
     *  @param method is the Abi Contract Function Input
     *  @return The hashed signature of the function
     */
    private fun encodeFunctionSignature(method: ABIContractFunction): String {
        val methodString = jsonInterfaceMethodToString(method)
        return Hash.sha3String(methodString).slice(IntRange(0, 9))
    }

    /**
     * Responsible for Encoding the Function
     *  @param includeTuple whether the input contains a tuple
     *  @param puts are the input parameters of the function
     */
    private fun flattenTypes(includeTuple: Boolean, puts: List<ABIContractInput>): List<*> {
        val types = mutableListOf<String>()
        puts.forEach { param ->
            if (!param.components.isNullOrEmpty()) {
                if (!param.type.startsWith("tuple")) {
                    throw Error("Invalid value given \"${param.type}\". Error: components found but type is not tuple.")
                }
                val arrayBracket = param.type.indexOf('[')
                val suffix = if (arrayBracket >= 0) param.type.substring(arrayBracket) else ""
                val result = flattenTypes(includeTuple, param.components)
                if (includeTuple) {
                    types.add("tuple(${result.joinToString(",")})$suffix")
                } else {
                    types.add("(${result.joinToString(",")})$suffix")
                }
            } else {
                types.add(param.type)
            }
        }
        return types
    }

    /**
     * Responsible for taking an input function and returning the necessary string for encoding the function signature
     *  @param method is the Abi Contract Function Input
     *  @return 'Flattened' signature name with inputs
     */
    private fun jsonInterfaceMethodToString(method: ABIContractFunction): String {
        val types = flattenTypes(false, method.inputs)
        return "${method.name}(${types.joinToString(",")})"
    }


    /**
     * Responsible for encoding the parameters and their values
     *  @param parameters is a list of the function input Parameters defined in Corda API
     *  @return The encoded version of the parameters
     */
    private fun encodeParameters(parameters: List<*>): String {
        val result = StringBuilder()
        var dynamicDataOffset = parameters.size * Type.MAX_BYTE_LENGTH
        val dynamicData = StringBuilder()
        for (parameter in parameters) {
            if (parameter == null) {
                throw Error("nullable parameter")
            }

            if (parameter is List<*>) {

                parameter.map {
                    result.append(TypeEncoder.encode(it as Type<*>))
                }

            } else {
                // if it is bytes we can simply append it
                if (parameter is ByteArray) {
                    // should have length of 64
                    val padding = "0".repeat((32 - parameter.size) * 2)
                    result.append(Numeric.toHexStringNoPrefix(parameter) + padding)
                } else {

                    val encodedValue = TypeEncoder.encode(parameter as Type<*>)

                    if (isDynamic(parameter)) {
                        val encodedDataOffset = encodeNumeric(Uint(BigInteger.valueOf(dynamicDataOffset.toLong())))
                        result.append(encodedDataOffset)
                        dynamicData.append(encodedValue)
                        dynamicDataOffset += encodedValue.length shr 1
                    } else {
                        result.append(encodedValue)
                    }
                }
            }
        }
        result.append(dynamicData)
        return result.toString()
    }

    /**
     * Responsible for testing whether a parameter is Dynamic
     *  @param parameter is Web3j compatible parameter used for encoding
     *  @return Whether the parameter is dynamic
     */
    private fun isDynamic(parameter: Type<*>): Boolean {
        return parameter is DynamicBytes
                || parameter is Utf8String
                || parameter is DynamicArray<*>
                || (parameter is StaticArray<*> && DynamicStruct::class.java.isAssignableFrom(parameter.componentType))
    }

    /**
     * Responsible for encoding Numeric parameters
     *  @param numericType is Web3j compatible numeric type parameter
     *  @return The encoded number
     */
    private fun encodeNumeric(numericType: NumericType): String {
        val rawValue = toByteArray(numericType)
        val paddingValue = getPaddingValue(numericType)
        val paddedRawValue = ByteArray(Type.MAX_BYTE_LENGTH)
        if (paddingValue != 0.toByte()) {
            for (i in paddedRawValue.indices) {
                paddedRawValue[i] = paddingValue
            }
        }
        System.arraycopy(
            rawValue, 0, paddedRawValue, Type.MAX_BYTE_LENGTH - rawValue.size, rawValue.size
        )
        return Numeric.toHexStringNoPrefix(paddedRawValue)
    }

    /**
     * Responsible for converting a numericType to a byteArray
     *  @param numericType is Web3j compatible numeric type parameter
     *  @return The converted byteArray
     */
    private fun toByteArray(numericType: NumericType): ByteArray {
        val value = numericType.value
        if (numericType is Ufixed || numericType is Uint) {
            if (value.bitLength() == Type.MAX_BIT_LENGTH) {
                // As BigInteger is signed, if we have a 256 bit value, the resultant byte array
                // will contain a sign byte in its MSB, which we should ignore for this unsigned
                // integer type.
                val byteArray = ByteArray(Type.MAX_BYTE_LENGTH)
                System.arraycopy(value.toByteArray(), 1, byteArray, 0, Type.MAX_BYTE_LENGTH)
                return byteArray
            }
        }
        return value.toByteArray()
    }

    private fun getPaddingValue(numericType: NumericType): Byte {
        return if (numericType.value.signum() == -1) {
            0xFF.toByte()
        } else {
            0.toByte()
        }
    }
}


data class ABIContractFunction(
    val inputs: List<ABIContractInput>,
    val name: String,
)

data class ABIContractInput(
    val name: String,
    val type: String,
    val components: List<ABIContractInput>? = null,
)




