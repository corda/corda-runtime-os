
# Pre-install Command for the Corda CLI

## check-limits
Check the resource limits have been assigned correctly.
> **preinstall check-limits \<path\>**

      <path>        The yaml file containing resource limit overrides for the
                    Corda install

## check-postgres
Check that the PostgreSQL DB is up and that the credentials work.
> **preinstall check-postgres [-n=\<namespace\>] \<path\>**

      <path>        The yaml file containing the username and password values for 
                    PostgreSQL - either as values, or as secret references
	  -n, --namespace=<namespace>
                    The namespace in which to look for PostgreSQL secrets if there are any

## check-kafka
Check that Kafka is up and that the credentials work.

>**preinstall check-kafka [-n=\<namespace\>] [-t=\<timeout\>] \<path\>**

      <path>                  The yaml file containing the Kafka, SASL, and TLS configurations
	  -n, --namespace=<namespace>
                              The namespace in which to look for the Kafka secrets if TLS or 
                              SASL is enabled
	  -t, --timeout=<timeout> The timeout in milliseconds for testing the Kafka
                              connection - defaults to 3000

## run-all
Runs all preinstall checks.
> **preinstall run-all [-n=\<namespace\>] [-t=\<timeout\>] \<path\>**

      <path>                  The yaml file containing all configurations
	  -n, --namespace=<namespace>
                              The namespace in which to look for both the PostgreSQL and Kafka secrets
	  -t, --timeout=<timeout> The timeout in milliseconds for testing the Kafka
                              connection - defaults to 3000
