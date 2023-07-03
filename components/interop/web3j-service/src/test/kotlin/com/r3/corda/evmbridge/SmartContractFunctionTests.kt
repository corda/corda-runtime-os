package com.r3.corda.evmbridge

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.r3.corda.evmbridge.web3j.ContractFunction
import com.r3.corda.evmbridge.web3j.GenericContract
import com.r3.corda.evmbridge.web3j.SmartContract
import java.math.BigInteger
import net.corda.interop.web3j.DelegatingTxSignService
import net.corda.interop.web3j.internal.DelegatingWeb3JService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.web3j.abi.datatypes.Bool
import org.web3j.abi.datatypes.Utf8String
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.crypto.Credentials
import org.web3j.protocol.Web3j
import org.web3j.protocol.Web3jService
import org.web3j.protocol.http.HttpService
import org.web3j.tx.RawTransactionManager
import org.web3j.tx.gas.DefaultGasProvider

class SmartContractFunctionTests {
    private val objectMapper = ObjectMapper().registerKotlinModule()

    // Change for each Ganache run
    private val aliceAddress = "0x02643ed6819E63d022434b5a6de6E01C73Efe571"
    private val alicePrivateKey = "0xe5141a38133e1fd3d5e7ffac7641a139bdff4fc47d6a6b6ea6682959e489f51d"
    private val bobAddress = "0xABEA411aAE92270aA3a1B2CA2C58B30fc791d3Bd"
    private val bobPrivateKey = "0xf9c386a8e7867f80c2008936001bf55753f53913e41f19e3b71eb4f7dbb6e831"
    private val contractAddress = "0x03A63Eb089076836d0e70B930657ea84775C3eEe"
    // DUMMY, NOT EVEN USED
    private val byteCode = "60806040523480156200001157600080fd5b5060405162000b7038038062000b7083398101604081905262000034916200011f565b600362000042838262000218565b50600462000051828262000218565b505050620002e4565b634e487b7160e01b600052604160045260246000fd5b600082601f8301126200008257600080fd5b81516001600160401b03808211156200009f576200009f6200005a565b604051601f8301601f19908116603f01168101908282118183101715620000ca57620000ca6200005a565b81604052838152602092508683858801011115620000e757600080fd5b600091505b838210156200010b5785820183015181830184015290820190620000ec565b600093810190920192909252949350505050565b600080604083850312156200013357600080fd5b82516001600160401b03808211156200014b57600080fd5b620001598683870162000070565b935060208501519150808211156200017057600080fd5b506200017f8582860162000070565b9150509250929050565b600181811c908216806200019e57607f821691505b602082108103620001bf57634e487b7160e01b600052602260045260246000fd5b50919050565b601f8211156200021357600081815260208120601f850160051c81016020861015620001ee5750805b601f850160051c820191505b818110156200020f57828155600101620001fa565b5050505b505050565b81516001600160401b038111156200023457620002346200005a565b6200024c8162000245845462000189565b84620001c5565b602080601f8311600181146200028457600084156200026b5750858301515b600019600386901b1c1916600185901b1785556200020f565b600085815260208120601f198616915b82811015620002b55788860151825594840194600190910190840162000294565b5085821015620002d45787850151600019600388901b60f8161c191681555b5050505050600190811b01905550565b61087c80620002f46000396000f3fe608060405234801561001057600080fd5b50600436106100a95760003560e01c80633950935111610071578063395093511461012357806370a082311461013657806395d89b411461015f578063a457c2d714610167578063a9059cbb1461017a578063dd62ed3e1461018d57600080fd5b806306fdde03146100ae578063095ea7b3146100cc57806318160ddd146100ef57806323b872dd14610101578063313ce56714610114575b600080fd5b6100b66101a0565b6040516100c391906106c6565b60405180910390f35b6100df6100da366004610730565b610232565b60405190151581526020016100c3565b6002545b6040519081526020016100c3565b6100df61010f36600461075a565b61024c565b604051601281526020016100c3565b6100df610131366004610730565b610270565b6100f3610144366004610796565b6001600160a01b031660009081526020819052604090205490565b6100b6610292565b6100df610175366004610730565b6102a1565b6100df610188366004610730565b610321565b6100f361019b3660046107b8565b61032f565b6060600380546101af906107eb565b80601f01602080910402602001604051908101604052809291908181526020018280546101db906107eb565b80156102285780601f106101fd57610100808354040283529160200191610228565b820191906000526020600020905b81548152906001019060200180831161020b57829003601f168201915b5050505050905090565b60003361024081858561035a565b60019150505b92915050565b60003361025a85828561047e565b6102658585856104f8565b506001949350505050565b600033610240818585610283838361032f565b61028d9190610825565b61035a565b6060600480546101af906107eb565b600033816102af828661032f565b9050838110156103145760405162461bcd60e51b815260206004820152602560248201527f45524332303a2064656372656173656420616c6c6f77616e63652062656c6f77604482015264207a65726f60d81b60648201526084015b60405180910390fd5b610265828686840361035a565b6000336102408185856104f8565b6001600160a01b03918216600090815260016020908152604080832093909416825291909152205490565b6001600160a01b0383166103bc5760405162461bcd60e51b8152602060048201526024808201527f45524332303a20617070726f76652066726f6d20746865207a65726f206164646044820152637265737360e01b606482015260840161030b565b6001600160a01b03821661041d5760405162461bcd60e51b815260206004820152602260248201527f45524332303a20617070726f766520746f20746865207a65726f206164647265604482015261737360f01b606482015260840161030b565b6001600160a01b0383811660008181526001602090815260408083209487168084529482529182902085905590518481527f8c5be1e5ebec7d5bd14f71427d1e84f3dd0314c0f7b2291e5b200ac8c7c3b925910160405180910390a3505050565b600061048a848461032f565b905060001981146104f257818110156104e55760405162461bcd60e51b815260206004820152601d60248201527f45524332303a20696e73756666696369656e7420616c6c6f77616e6365000000604482015260640161030b565b6104f2848484840361035a565b50505050565b6001600160a01b03831661055c5760405162461bcd60e51b815260206004820152602560248201527f45524332303a207472616e736665722066726f6d20746865207a65726f206164604482015264647265737360d81b606482015260840161030b565b6001600160a01b0382166105be5760405162461bcd60e51b815260206004820152602360248201527f45524332303a207472616e7366657220746f20746865207a65726f206164647260448201526265737360e81b606482015260840161030b565b6001600160a01b038316600090815260208190526040902054818110156106365760405162461bcd60e51b815260206004820152602660248201527f45524332303a207472616e7366657220616d6f756e7420657863656564732062604482015265616c616e636560d01b606482015260840161030b565b6001600160a01b0380851660009081526020819052604080822085850390559185168152908120805484929061066d908490610825565b92505081905550826001600160a01b0316846001600160a01b03167fddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef846040516106b991815260200190565b60405180910390a36104f2565b600060208083528351808285015260005b818110156106f3578581018301518582016040015282016106d7565b506000604082860101526040601f19601f8301168501019250505092915050565b80356001600160a01b038116811461072b57600080fd5b919050565b6000806040838503121561074357600080fd5b61074c83610714565b946020939093013593505050565b60008060006060848603121561076f57600080fd5b61077884610714565b925061078660208501610714565b9150604084013590509250925092565b6000602082840312156107a857600080fd5b6107b182610714565b9392505050565b600080604083850312156107cb57600080fd5b6107d483610714565b91506107e260208401610714565b90509250929050565b600181811c908216806107ff57607f821691505b60208210810361081f57634e487b7160e01b600052602260045260246000fd5b50919050565b8082018082111561024657634e487b7160e01b600052601160045260246000fdfea2646970667358221220893f65019d45330f3a53df6b9ff731ba8d6925c137e71527450588aedcdb1cad64736f6c63430008100033"

    @Test
    fun callSmartContractFunction() {
        val web3j = initWeb3jConnection(initWeb3jService("http://127.0.0.1:8545"))
        // instantiate generic contract
        val contract = GenericContract(byteCode, contractAddress, web3j, Credentials.create(alicePrivateKey))

        var fn = readFunction("name.json")
        val fnCall = contract.callFunction(fn)
        val nameValue = fnCall.send()
        assertEquals("Token", nameValue)


        fn = readFunction("multipleReturn.json")
        val fnCallMultiple = contract.callFunction(fn)
        val ret = fnCallMultiple.send()
        assert(ret is List<*>)
        with(ret as List<*>) {
            assertEquals(Bool(true), this[0])
            assertEquals(Uint256(10), this[1])
            assertEquals(Utf8String("caca"), this[2])
        }

        fn = readFunction("balance.json")
        val bobInitialBalance = contract.callFunction(fn).send()
        println(bobInitialBalance)

        fn = readFunction("transferTo.json")
        val remoteTransaction = contract.callFunction(fn)
        val txReceipt = remoteTransaction.send()
        println(txReceipt)

        fn = readFunction("balance.json")
        val bobCurrentBalance = contract.callFunction(fn).send()
        println(bobCurrentBalance)

        assertEquals((bobInitialBalance as BigInteger).plus(BigInteger.valueOf(100)), bobCurrentBalance as BigInteger)
    }

    fun readFunction(name: String): ContractFunction {
        val fn = objectMapper.readValue(javaClass.classLoader.getResource(name), ContractFunction::class.java)
        println(fn)
        return fn
    }

    fun initWeb3jConnection(service: Web3jService): Web3j {
        return Web3j.build(service)
    }

    fun initWeb3jService(url: String): Web3jService {
        return HttpService(url)
    }


    @Test
    fun useGeneratedCode() {
        //init delegating shit
        val chainId: Long = 1337 //TODO: figure out where to get this from
//        val delegatedWeb3j = DelegatingWeb3j()
        val delegatingTxSigningService = DelegatingTxSignService()

        //instantiate proper web3j shit
        val web3j = initWeb3jConnection(DelegatingWeb3JService())
        val rawTransactionManager = RawTransactionManager(web3j, delegatingTxSigningService, chainId)
//        val forwarder = EvmForwarder(web3j, rawTransactionManager) // we use these directly but in reality should be using messages and kafka topics to carry the info
        // a bit of a circular dependency but we can untangle
//        val delegatingTransactionManager = DelegatingTransactionManager(delegatedWeb3j, delegatingTxSigningService, chainId, forwarder)

        // this code would be called in a flow worker
        val testContract = com.r3.corda.evmbridge.web3j.Test.load(contractAddress, web3j, rawTransactionManager, DefaultGasProvider())
        val remoteCall = testContract.tuples(listOf(com.r3.corda.evmbridge.web3j.Test.Struct2(com.r3.corda.evmbridge.web3j.Test.Struct1(BigInteger.valueOf(100), "test"))))
        val res = remoteCall.send()

        println(res)
        assertEquals(res, true)
    }

    @Test
    fun `do shit`() {
        val abi = """
            [
              {
                "inputs": [],
                "name": "retrieve",
                "outputs": [
                  {
                    "internalType": "uint256",
                    "name": "",
                    "type": "uint256"
                  }
                ],
                "stateMutability": "view",
                "type": "function"
              },
              {
                "inputs": [
                  {
                    "internalType": "address",
                    "name": "name",
                    "type": "address"
                  }
                ],
                "name": "retrieveStruct",
                "outputs": [
                  {
                    "components": [
                      {
                        "internalType": "int256",
                        "name": "x1",
                        "type": "int256"
                      },
                      {
                        "components": [
                          {
                            "internalType": "int256",
                            "name": "x2",
                            "type": "int256"
                          },
                          {
                            "components": [
                              {
                                "internalType": "int256",
                                "name": "x3",
                                "type": "int256"
                              },
                              {
                                "components": [
                                  {
                                    "internalType": "int256",
                                    "name": "x4",
                                    "type": "int256"
                                  }
                                ],
                                "internalType": "struct Storage.struct4",
                                "name": "struct4",
                                "type": "tuple"
                              }
                            ],
                            "internalType": "struct Storage.struct3",
                            "name": "struct3",
                            "type": "tuple"
                          }
                        ],
                        "internalType": "struct Storage.struct2",
                        "name": "struct2",
                        "type": "tuple"
                      }
                    ],
                    "internalType": "struct Storage.struct1",
                    "name": "",
                    "type": "tuple"
                  }
                ],
                "stateMutability": "view",
                "type": "function"
              },
              {
                "inputs": [
                  {
                    "internalType": "uint256",
                    "name": "num",
                    "type": "uint256"
                  },
                  {
                    "components": [
                      {
                        "internalType": "int256",
                        "name": "x1",
                        "type": "int256"
                      },
                      {
                        "components": [
                          {
                            "internalType": "int256",
                            "name": "x2",
                            "type": "int256"
                          },
                          {
                            "components": [
                              {
                                "internalType": "int256",
                                "name": "x3",
                                "type": "int256"
                              },
                              {
                                "components": [
                                  {
                                    "internalType": "int256",
                                    "name": "x4",
                                    "type": "int256"
                                  }
                                ],
                                "internalType": "struct Storage.struct4",
                                "name": "struct4",
                                "type": "tuple"
                              }
                            ],
                            "internalType": "struct Storage.struct3",
                            "name": "struct3",
                            "type": "tuple"
                          }
                        ],
                        "internalType": "struct Storage.struct2",
                        "name": "struct2",
                        "type": "tuple"
                      }
                    ],
                    "internalType": "struct Storage.struct1",
                    "name": "name",
                    "type": "tuple"
                  }
                ],
                "name": "store",
                "outputs": [],
                "stateMutability": "nonpayable",
                "type": "function"
              }
            ]
        """.trimIndent()

        val fn = jacksonObjectMapper().readValue<List<AbiContractFunction>>(abi)
        println(fn)
    }

    @Test
    fun `read best bits of the abi`() {
        val abiFilenames = javaClass.classLoader.getResourceAsStream("abis")?.bufferedReader()?.readLines() ?: emptyList()
        val abiFiles = abiFilenames.mapNotNull {
            javaClass.classLoader.getResourceAsStream("abis/$it")?.bufferedReader()?.readText()
        }

        val objectMapper = jacksonObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false)

        val contractFunctions = abiFiles.associate {
            val root = objectMapper.readTree(it)
            val contractName = root.findValue("title") ?: root.findValue("contractName")
            val contractFunctions = objectMapper.readValue<List<AbiContractFunction>>(root.findPath("abi").toString())

            contractName.textValue() to contractFunctions
        }

        println("contracts: \n${contractFunctions.keys}.")
        println("contractFunctions: \n$contractFunctions.")

        val contract: SmartContract = EVMContract(contractFunctions["Storage"]!!)
        contract.execute("retrieve")

        contract.execute(
            "store",
            mapOf(
                "num" to 15,
                "name" to listOf(
                    "x1" to 1,
                    "struct2" to listOf(
                        "x2" to 2,
                        "struct3" to listOf(
                            "x3" to 3,
                            "struct4" to listOf(
                                "x4" to 4
                            )
                        )
                    )
                )
            )
        )

        val myToken: SmartContract = EVMContract(contractFunctions["MyToken"]!!)
        myToken.execute(
            "allowance",
            mapOf(
                "owner" to "Alys",
                "spender" to "Bob",
            )
        )
    }
}

//interface SmartContract {
//    fun execute(functionName: String, parameters: List<AbiContractFunctionInput> = emptyList())
//}

class EVMContract(
    private val contractFunctions: List<AbiContractFunction>
): SmartContract {
    override fun execute(functionName: String) = execute(functionName, emptyMap())

    override fun execute(functionName: String, parameters: Map<String, Any>) {
        val function = contractFunctions.find { it.name == functionName }!!.copy()
        val inputs = function.inputs.map {
            if (it.name in parameters) {
                it.copy(value = parameters[it.name])
            } else {
                it
            }
        }
        val output = function.copy(inputs = inputs)

        println("output function: $output")
    }

}

data class AbiContractFunctionInput(
    val name: String,
    val type: String,
    val internalType: String,
    val value: Any?,
    val components: List<AbiContractFunctionInput>?,
)

data class AbiContractFunction(
    val name: String?,
    val inputs: List<AbiContractFunctionInput>,
    val outputs: List<AbiContractFunctionInput>?,
    val type: String,
    val stateMutability: String?,
)


