
# Pre-install Plugin for the Corda CLI

Preinstall checks for corda. To run the plugin using the Corda CLI Plugin Host, first compile the plugin like so:

	./gradlew clean :tools:plugins:preinstall:build

Then copy the build files from `corda-runtime-os` to `corda-cli-plugin-host`:

	cp ./corda-runtime-os/tools/plugins/preinstall/build/libs/preinstall-cli-plugin-*.jar ./corda-cli-plugin-host/build/plugins/ 

Finally, you can run the plugin from the cli plugin host with:

	./gradlew run --args="preinstall <subcommand> [options] <path>"

## check-limits
Check the resource limits have been assigned correctly.
> **preinstall check-limits [-dv] \<path\>**

      <path>        The yaml file containing resource limit overrides for the
                    corda install
	  -d, --debug   Show information about limit calculation for debugging
                    purposes
	  -v, --verbose Display additional information when checking resources

## check-postgres
Check that the PostgreSQL DB is up and that the credentials work.
> **preinstall check-postgres [-dv] [-n=\<namespace\>] \<path\>**

      <path>        The yaml file containing either the username and password
                    value, or valueFrom.secretKeyRef.key fields for Postgres
	  -d, --debug   Show extra information while connecting to Postgres for debugging
                    purposes
	  -n, --namespace=<namespace>
                    The namespace in which to look for Postgres secrets if there are any
      -u, --url=<url>         The kubernetes cluster URL (if the preinstall is being called from 
                              outside the cluster)
	  -v, --verbose Display additional information when connecting to Postgres

## check-kafka
Check that Kafka is up and that the credentials work.

>**preinstall check-kafka [-dv] [-f=\<truststoreLocation\>]
[-n=\<namespace\>] [-r=\<replicaCount\>]
[-t=\<timeout\>] \<path\>**

      <path>                  The yaml file containing the Kafka, SASL, and TLS configurations
	  -d, --debug             Show information about kafka config creation for debugging 
                              purposes
	  -f, --file=<truststoreLocation>
                              The file location of the truststore if TLS is enabled
      -m, --max-idle=<maxIdleMs>
                              The maximum ms a connection can be idle for while testing the 
                              kafka connection - defaults to 5000
	  -n, --namespace=<namespace>
                              The namespace in which to look for the Kafka secrets if TLS or 
                              SASL is enabled
	  -t, --timeout=<timeout> The timeout in milliseconds for testing the kafka
                              connection - defaults to 3000
      -u, --url=<url>         The kubernetes cluster URL (if the preinstall is being called from 
                              outside the cluster)
	  -v, --verbose           Display additional information when connecting to Kafka

## run-all
Runs all preinstall checks.
> **preinstall run-all [-dv] [-f=\<truststoreLocation\>]
[-n=\<namespace\>] [-r=\<replicaCount\>]
[-t=\<timeout\>] \<path\>**

      <path>                  The yaml file containing all configurations
	  -d, --debug             Show information for debugging purposes
	  -f, --file=<truststoreLocation>
                              The file location of the truststore for Kafka
      -m, --max-idle=<maxIdleMs>
                              The maximum ms a connection can be idle for while testing the 
                              kafka connection - defaults to 5000
	  -n, --namespace=<namespace>
                              The namespace in which to look for both the Postgres and Kafka secrets
	  -t, --timeout=<timeout> The timeout in milliseconds for testing the kafka
                              connection - defaults to 3000
      -u, --url=<url>         The kubernetes cluster URL (if the preinstall is being called from 
                              outside the cluster)
	  -v, --verbose           Display additional information about the configuration provided
