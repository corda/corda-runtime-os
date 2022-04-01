package net.corda.p2p.test.stub.crypto.processor

import java.lang.RuntimeException
import java.security.Key

abstract class CryptoProcessorException(message: String) : RuntimeException(message)

class CouldNotFindPrivateKey : CryptoProcessorException("Could not find private key")

class UnsupportedAlgorithm(key: Key) : CryptoProcessorException("Unsupported algorithm ${key.algorithm}")

class CouldNotReadKey(pem: String) : CryptoProcessorException("Could not read key pair from $pem")
