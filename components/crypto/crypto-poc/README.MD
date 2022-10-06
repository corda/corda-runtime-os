In some places there is need to separate implementation in modules for:
* in-process components (one of which is PlatformCipherSuiteRegistrar and similar)
* crypto worker specific (one of which is PlatformCryptoWorkerCipherSuiteRegistrar and similar)

Note that except the PlatformCipherSuiteRegistrar itself the rest of the in-process components 
can be used inside the crypto worker