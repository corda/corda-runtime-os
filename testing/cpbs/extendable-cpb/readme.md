# CPB for testing

Extend this "generic" CPB for more testing.  The intention is to extend this CPB
with more functionality, rather than creating more and more CPBs.

If you _modify_ this CPB, ensure that any `integrationTests` (and/or OSGi tests)
continue to compile _and their tests continue to test the original scenarios_.

> *WARNING* : some `@Entity` classes that we serialize using AMQP may have
> types that we don't currently support
> 
