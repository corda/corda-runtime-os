
# Pre-install Plugin for the Corda CLI

Preinstall checks for corda. To run the plugin using the Corda CLI Plugin Host, first compile the plugin like so:

	./gradlew clean :tools:plugins:preinstall:build

Then copy the build files from `corda-runtime-os` to `corda-cli-plugin-host`:

	cp ./corda-runtime-os/tools/plugins/preinstall/build/libs/preinstall-cli-plugin-*.jar ./corda-cli-plugin-host/build/plugins/ 

Finally, you can run the plugin from the cli plugin host with:

	./gradlew run --args="preinstall <subcommand> [options] <path>"

## check-limits
Check the resource limits have been assigned correctly.
> **preinstall check-limits \<path\>**

      <path>        The yaml file containing resource limit overrides for the
                    Corda install

## check-postgres
Check that the PostgreSQL DB is up and that the credentials work.
> **preinstall check-postgres [-n=\<namespace\>] \<path\>**

      <path>        The yaml file containing either the username and password
                    value, or valueFrom.secretKeyRef.key fields for Postgres
	  -n, --namespace=<namespace>
                    The namespace in which to look for Postgres secrets if there are any

## check-kafka
Check that Kafka is up and that the credentials work.

>**preinstall check-kafka [-f=\<truststoreLocation\>] [-n=\<namespace\>] 
[-t=\<timeout\>] \<path\>**

      <path>                  The yaml file containing the Kafka, SASL, and TLS configurations
	  -f, --file=<truststoreLocation>
                              The file location of the truststore if TLS is enabled
	  -n, --namespace=<namespace>
                              The namespace in which to look for the Kafka secrets if TLS or 
                              SASL is enabled
	  -t, --timeout=<timeout> The timeout in milliseconds for testing the kafka
                              connection - defaults to 3000

## run-all
Runs all preinstall checks.
> **preinstall run-all [-f=\<truststoreLocation\>] [-n=\<namespace\>] 
[-t=\<timeout\>] \<path\>**

      <path>                  The yaml file containing all configurations
	  -f, --file=<truststoreLocation>
                              The file location of the truststore for Kafka
	  -n, --namespace=<namespace>
                              The namespace in which to look for both the Postgres and Kafka secrets
	  -t, --timeout=<timeout> The timeout in milliseconds for testing the Kafka
                              connection - defaults to 3000
