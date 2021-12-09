# Virtual Node CLI

Experimental, likely to be deleted, but here to test the `VirtualNodeInfo` service
and the CPI Info service.  Simply publishes static data to Kafka.


## IMPORTANT

Run the `appJar` gradle task to build a `jar` with a `main` (OSGi) entry point.

Use the `jar` in `build/bin` NOT `libs`

## Running

Start docker compose to run an instance of Kafka

```shell
cd applications/experimental/virtual-node-cli/deploy
docker-compose up
```

Use a "JAR Application" task in Intellij, set the target to the be the jar in `build/bin`,
and "Before Launch" run the `appJar` gradle task

Use the args:

```
--instanceId 1 --topic /full/path/to/topics.conf --config /full/path/to/app.conf
```

Don't forget to run `docker-compose down` when you're done.
