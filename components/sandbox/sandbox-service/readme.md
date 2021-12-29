# Sandbox Service

## Running the Integration Tests

You need to run the `clean` task and then `testOSGi` task.

In Intellij, "Edit Configurations" and then add the following to the Run VM Options
of the `testOSGi` task.  You may find it helpful to add the `clean` task to the
"Before launch" section.

```shell
-D-runjdb=5005
```

Alternatively, you can specify this on the command line when running the `testOSGi` 
gradle task.

In Intellij, create a "Remote JVM Debug" task, and also set the port to `5005`.

Finally, run the `testOSGi` task, wait for it to build and await connections to socket 
`5005`, then run the "Remote JVM Debug" task.
