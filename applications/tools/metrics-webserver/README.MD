## About

The `metrics-webserver` is a very simple application which can be used to read metrics and measurements collected in a 
file and expose them via a REST endpoint to observability and analysis tools such as Prometheus.

It is meant to be used to feed metrics data collected from tests into Prometheus + Grafana.

Webserver endpoint: `/metrics` 

Webserver default port: `7004`

Metrics file default name: `metrics.txt`

Assuming Prometheus is running on the same machine as this webserver which is using default configuration values,
it should be configured to target the following url to read the metrics data `http://localhost:7004/metrics`


## Building the application

### Using IntelliJ

Simply run the `jar` task in the `applications:tools:metrics-webserver` gradle project. This will generate an
executable jar in the `metrics-webserver` module's `build/libs/` directory.

### Using Gradle

From a terminal, run the following command in the `metrics-webserver` module:

```shell
../../../gradlew jar
```

This will generate an
executable jar in the `metrics-webserver` module's `build/libs/` directory.

## Running the application

### Using IntelliJ

Clicking on the green play button next to the `main` function in `MetricsReaderapp.kt` will automatically build
the jar and launch the application with its default parameters for metrics file path and web server port. To change these
values you can either edit the `Runtime configuration` for the execution and add set the parameters `-f` and `-p` or replace
the default values inside the code.

### Using the application jar

From a terminal, run the jar providing the `-f` and `-p` arguments if necessary. Example:

```shell
java -jar metrics-webserver-5.2.0.jar -f metrics.txt -p 3000
```

