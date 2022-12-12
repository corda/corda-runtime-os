# Backing store benchmarks

This module contains tests that performs benchmarking of the different operations of backing store
implementations. These are primarily intended as a development tool, to allow developers to perform
a relative comparison of performance between two versions of the code, or with two different
database configurations. The absolute numbers produced by these tests are meaningless, and as such,
__should not__ be considered a true performance test, or a substitute for performance testing using
a full Corda Cluster deployment.

## Running the benchmarks

These tests can be run by executing the Gradle `backingStoreBenchmark` task, without additional
arguments. This will run all benchmarks, using default settings. The tests can be configured using
the following properties:

- `postgresPort`: Setting this will run the tests against a Postgres DB instance, instead of using 
   HSQLDB (the default). It is strongly recommended that you use Postgres DB, as HSQLDB is not a 
   production grade database, and is unlikely to provide realistic results. Additional Postgres
   properties, such as user, password etc. can also be specified. These are the same set of
   properties as used by other database based tests in this repo.
- `bsBenchNumIterations`: This controls how many times each test case is run. This should not affect 
   the performance characteristics of the tests themselves, but a higher value is likely to provide
   more consistent results between runs due to the tests running for longer. __Default:__ 100, 
   which has been selected as a good balance between consistency and execution time when testing
   with Postgres.
- `bsBenchNumOpsPerIteration`: This controls how many operations are executed for each run of a test
  case. Operations can refer to either the number of states or number of transactions used in a
  test, depending on whether the test case is testing states or transactions. The number indicates
  how many states or transactions are passed into a single call to the backing store API. This can
  be used to simulate batching behaviour, as a bigger batch of requests processed by the uniqueness
  checker would correspond to the number of states / transactions passed into each call to the
  backing store API. __Default__: 100

For example, to run the tests with Postgres, 2500 iterations, and 20 operations per iteration:

` ./gradlew :components:uniqueness:backing-store-impl:backingStoreBenchmark -PpostgresPort=5432 -PbsBenchNumIterations=2500 -PbsBenchNumOpsPerIteration=20`

## Output

The results of the benchmarks are output in two ways; firstly, to the INFO log level. This should be
enabled and viewable by default if running within IntelliJ, but you may need to specify the `-i`
argument if running from the console in order to see this output. Results are written to the log at
the end of every test case, as well as a summary of all test cases being provided at the end of all
test cases.

Additionally, results are written to a CSV file, under `build/test-results/backingStoreBenchmark`.
By default, results will be written to a `results.csv` file. Subsequent runs of the tests will
append to this file, so long as the same set of tests are run as that run when the file was
originally generated. This makes it easy to compare multiple test runs. If a different set of tests
are run after this file is generated, for example, if a single test case is run following running
all tests, a warning will be raised to the log file, and a new results file with a timestamp suffix
will be used instead of appending to the `results.csv` file.
