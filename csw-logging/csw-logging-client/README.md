Logging Service
===============

Logging service helps components log messages in file and standard output.

The high level features provided by this library are :
 *    - Supports component specific log levels, ex. HCD1 can choose to log at `info` level and HCD2 can choose to log at `debug` level
 *    - Supports dynamically changing component log levels
 *    - Asynchronous thread safe logging
 *    - Structured logging
 *    - Supports overriding default logging properties per component viz
 *    - Intercepting logs from pekko/slf4j
 *    - Supports JSON logging

The codebase in this module is based on [persist-logging library](https://github.com/nestorpersist/logging).
We appreciate efforts put in by authors of the persist-logging library which made our development fast and easy.

If you want to get started with logging service, refer to the [examples](https://tmtsoftware.github.io/csw/services/logging.html).

You can refer to Scala documentation [here](https://tmtsoftware.github.io/csw/api/scala/csw/logging/index.html).

You can refer to Java documentation [here](https://tmtsoftware.github.io/csw/api/java/?/index.html).
