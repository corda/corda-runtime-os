# Debugging integration tests using jdb

To debug integration tests with jdb:

1. Locate and run both the `clean` and `integrationTest` tasks:

   ![gradle](./images/debugging-gradle.png)

2. Edit the `integrationTest` configuration to run the `clean` task before launch. Select `Modify options` then `Before Launch`, then hit '+" then select `Run gradle task`. Append `-D-runjdb=5005` to the run line:

   ![config1](./images/debugging-config1.png)

3. Create a "Remote JVM Debug" configuration, ensuring the port matches the one used
previously, i.e. `5005`

   ![jvmdbg](./images/debugging-config2.png)

4. Run the `integrationTest` task. 
   
   Once it starts, it pauses and waits.

5 Run the `Remote JVM Debug` task to connect. 

   You can now debug into the integration test.

For step 5 you may alternatively use the "Attack debugger" tooltip in IntelliJ

![Screen Shot 2022-07-21 at 15 13 42](https://user-images.githubusercontent.com/266672/180235889-d23153ee-52ed-4376-ac18-32265daf6e33.png)


# Debugging integration tests with Postgres

To debug a set of integration tests with Postgres:

1. Locate the `integrationTest` that you intend to run in Intellij.

2. Run `integrationTest` to create the run/debug configuration.

3. Run the Docker postgres image in a console:

   ```shell
   docker run --rm --name test-instance -e POSTGRES_PASSWORD=password -p 5432:5432 
   postgres
   ```

4. In your Intellij run/debug configuration add `-PpostgresPort=5432`  (**Note:** not `-D`):

   ![integration-test-postgres](https://user-images.githubusercontent.com/1275154/154064168-30e567ea-dd1e-49cb-ba7e-2c40164b3344.png)
