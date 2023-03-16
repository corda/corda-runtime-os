* Use CryptoRepository from SoftCryptoService, moving DB setup to softhsm level (with Dickon)
* Port SigningKeyStore to CryptoRepository (with Ryan)
* Port HSMStore to CryptoRepository
* DB Schema changes to support rotation of wrapping keys in persistence layer
* DB Schema changes to support rotation of signing keys in persistence layer
* Implement wrapping key rotation query
* Implement signing key rotation query
* Config schema change for controlling key rotation (addition only)
* E2E test that checks signature can be done after key rotation, with virtualised time
* Move components/crypto/persistence to libs/crypto

