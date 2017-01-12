MetricsJava Client
==================

<a href="https://raw.githubusercontent.com/ArpNetworking/metrics-client-java/master/LICENSE">
    <img src="https://img.shields.io/hexpm/l/plug.svg"
         alt="License: Apache 2">
</a>
<a href="https://travis-ci.org/ArpNetworking/metrics-client-java/">
    <img src="https://travis-ci.org/ArpNetworking/metrics-client-java.png?branch=master"
         alt="Travis Build">
</a>
<a href="http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.arpnetworking.metrics%22%20a%3A%22metrics-client%22">
    <img src="https://img.shields.io/maven-central/v/com.arpnetworking.metrics/metrics-client.svg"
         alt="Maven Artifact">
</a>
<a href="http://www.javadoc.io/doc/com.arpnetworking.metrics/metrics-client">
    <img src="http://www.javadoc.io/badge/com.arpnetworking.metrics/metrics-client.svg" 
         alt="Javadocs">
</a>

Client implementation for publishing metrics from Java applications.


Instrumenting Your Application
------------------------------

### Add Dependency

Determine the latest version of the Java client in [Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.arpnetworking.metrics%22%20a%3A%22metrics-client%22).

#### Maven

Add a dependency to your pom:

```xml
<dependency>
    <groupId>com.arpnetworking.metrics</groupId>
    <artifactId>metrics-client</artifactId>
    <version>VERSION</version>
</dependency>
```

The Maven Central repository is included by default.

#### Gradle

Add a dependency to your build.gradle:

    compile group: 'com.arpnetworking.metrics', name: 'metrics-client', version: 'VERSION'

Add the Maven Central Repository into your *build.gradle*:

```groovy
repositories {
    mavenCentral()
}
```

#### SBT

Add a dependency to your project/Build.scala:

```scala
val appDependencies = Seq(
    "com.arpnetworking.metrics" % "metrics-client" % "VERSION"
)
```

The Maven Central repository is included by default.

#### Sink

Add a dependency on one or more of the sink implementations such as [metrics-apache-http-sink-extra](https://github.com/ArpNetworking/metrics-apache-http-sink-extra)
or [metrics-file-sink-extra](https://github.com/ArpNetworking/metrics-file-sink-extra). If found the *ApacheHttpSink* is
used by default (with default settings) or if not found then the *FileSink* is used by default (with default settings). You
may also specify the sinks to use to the *MetricsFactory.Builder*. If you get warnings in your log "No default sink found." 
it means that you don't have one of the default sinks available and need to specify one or more to the builder.

#### Vertx

Users of Vertx need to depend on the vertx-extra package instead of the metrics-client package.  The vertx-extra provides
the necessary wrappers around the standard Java metrics client to work with the shared data model in Vertx.  Special thanks
to Gil Markham for contributing this work.  For more information please see [metrics-vertx-extra/README.md](https://github.com/ArpNetworking/metrics-vertx-extra).

### MetricsFactory

Your application should instantiate a single instance of MetricsFactory.  For example:

```java
final MetricsFactory metricsFactory = TsdMetricsFactory.newInstance(
    "MyServiceName",            // The name of the service
    "MyService-US-Prod");       // The name of the cluster or instance
```

Alternatively, you can customize construction using the Builder.  For example:

```java
final MetricsFactory metricsFactory = new TsdMetricsFactory.Builder()
    .setServiceName("MyServiceName")
    .setClusterName("MyService-US-Prod")
    .setHostName("my-service-app1.us-east")
    .setSinks(Collections.singletonList(
        new TsdLogSink.Builder()
            .setDirectory(new File("/var/logs"))
            .setName("myservice-query")
            .setExtension(".json")
            .setMaxHistory(168)
            .setCompress(false)
            .build()))
    .build();
```

Other options on the TsdLogSink include _immediateFlush_ and _prudent_.  The former is used to flush all writes to disk immediately while the latter performs file access in a more conservative manner enabling multiple processes to write to the same file (assuming they all have _prudent_ set to true).  Setting either _immediateFlush_ or _prudent_ to _true_ will have non-trivial performance impact.  All these options also apply to the _StenoLogSink_.

### Metrics

The MetricsFactory is used to create a Metrics instance for each unit of work.  For example:

 ```java
final Metrics metrics = metricsFactory.create();
```

Counters, timers and gauges are recorded against a metrics instance which must be closed at the end of a unit of work.  After the Metrics instance is closed no further measurements may be recorded against that instance.

```java
metrics.incrementCounter("foo");
metrics.startTimer("bar");
// Do something that is being timed
metrics.stopTimer("bar");
metrics.setGauge("temperature", 21.7);
metrics.addAnnotation("room", "kitchen");
metrics.close();
```

### Injection

Passing the MetricsFactory instance around your code is far from ideal.  We strongly recommend a combination of two techniques to keep metrics from polluting your interfaces.  First, use a dependency injection framework like Spring or Guice to create your TsdMetricsFactory instance and inject it into the parts of your code that initiate units of work.

Next, the unit of work entry points can leverage thread local storage to distribute the Metrics instance for each unit of work transparently throughout your codebase.  Log4J calls this a Mapped Diagnostics Context (MDC) and it is also available in LogBack although you will have to create a static thread-safe store (read ConcurrentMap) of Metrics instances since the LogBack implementation is limited to Strings.

One important note, if your unit of work leverages additional worker threads you need to pass the Metrics instance from the parent thread's MDC into the child thread's MDC.

### Counters

Surprisingly, counters are probably more difficult to use and interpret than timers and gauges.  In the simplest case you can just starting counting, for example, iterations of a loop:

```java
for (String s : Arrays.asList("a", "b", "c", "d", "e")) {
    metrics.incrementCounter("strings");
    // Do something in a loop
}
```

However, what happens if listOfString is empty? Then no sample is recorded. To always record a sample the counter should be reset before the loop is executed:

```java
metrics.resetCounter("strings");
for (String s : Arrays.asList("a", "b", "c", "d", "e")) {
    metrics.incrementCounter("strings");
    // Do something in a loop
}
```

Next, if the loop is executed multiple times:

```java
for (List<String> listOfString : Arrays.asList(
        Arrays.asList("a", "b", "c"),
        Arrays.asList("d", "e"),
        Arrays.asList("f"))) {
    metrics.resetCounter("strings");
    for (String s : listOfString) {
        metrics.incrementCounter("s");
        // Do something in a nested loop
    }
}
```

The above code will produce a number of samples equal to the size of listOfListOfStrings (including no samples if listOfListOfStrings is empty).  If you move the resetCounter call outside the outer loop the code always produces a single sample (even if listOfListOfStrings is empty).  There is a significant difference between counting the total number of strings and the number of strings per list; especially, when computing and analyzing statistics such as percentiles.

Finally, if the loop is being executed concurrently for the same unit of work, that is for the same Metrics instance, then you could use a Counter object:

```java
final Counter counter = metrics.createCounter("strings");
for (String s : Arrays.asList("a", "b", "c", "d", "e")) {
    counter.increment();
    // Do something in a loop
}
```

The Counter object example extends in a similar way for nested loops.

### Timers

Timers are very easy to use. The only note worth making is that when using timers - either procedural or via objects - do not forget to stop/close the timer!  If you fail to do this the client will log a warning and suppress any unstopped/unclosed samples.

The timer object allows a timer sample to be detached from the Metrics instance.  One common usage is in request/response filters (e.g. for Jersey and RestEasy) where the timer is started on the inbound filter and stopped/closed on the outbound filter.

The timer instance is also very useful in a concurrent system when executing and thus measuring the same event multiple times concurrently for the same unit of work.  The one caveat is to ensure the timer objects are stopped/closed before the Metrics instance is closed.  Failing to do so will log a warning and suppress any samples stopped/closed after the Metrics instance is closed.

### Gauges

Gauges are the simplest metric to record.  Samples for a gauge represent spot measurements. For example, the length of a queue or the number of active threads in a thread pool.  Gauges are often used in separate units of work to measure the state of system resources, for example the row count in a database table.  However, gauges are also useful in existing units of work, for example recording the memory in use at the beginning and end of each service request.

### Annotations

Annotations can be used to augment the context of the emitted metric. This can be useful for post-mortems or analysis outside the scope of the Metrics project. Currently, the Metrics project makes no use of these annotations.

### Closeable

The Metrics interface as well as the Timer and Counter interfaces extend [Closeable](http://docs.oracle.com/javase/7/docs/api/java/io/Closeable.html) which allows you to use Java 7's try-with-resources pattern.  For example:

```java
try (final Metrics metrics = metricsFactory.create()) {
    try (final Timer timer = metrics.createTimer("timer")) {
        // Time unsafe operation (e.g. this may throw)
        Thread.sleep(1000);
    }
}
```

The timer instance created and started in the try statement is automatically closed (e.g. stopped) when the try is exited either by completion of the block or by throwing an exception.

Building
--------

Prerequisites:
* [JDK8](http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html) (Or Invoke with JDKW)

Building:

    metrics-client-java> ./mvnw verify

To use the local version you must first install it locally:

    metrics-client-java> ./mvnw install

You can determine the version of the local build from the pom file.  Using the local version is intended only for testing or development.

You may also need to add the local repository to your build in order to pick-up the local version:

* Maven - Included by default.
* Gradle - Add *mavenLocal()* to *build.gradle* in the *repositories* block.
* SBT - Add *resolvers += Resolver.mavenLocal* into *project/plugins.sbt*.

License
-------

Published under Apache Software License 2.0, see LICENSE

&copy; Groupon Inc., 2014
