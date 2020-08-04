MetricsJava Client
==================

<a href="https://raw.githubusercontent.com/InscopeMetrics/metrics-client-java/master/LICENSE">
    <img src="https://img.shields.io/hexpm/l/plug.svg"
         alt="License: Apache 2">
</a>
<a href="https://travis-ci.org/InscopeMetrics/metrics-client-java/">
    <img src="https://travis-ci.org/InscopeMetrics/metrics-client-java.png?branch=master"
         alt="Travis Build">
</a>
<a href="http://search.maven.org/#search%7Cga%7C1%7Cg%3A%io.inscopemetrics.client%22%20a%3A%22metrics-client%22">
    <img src="https://img.shields.io/maven-central/v/io.inscopemetrics.client/metrics-client.svg"
         alt="Maven Artifact">
</a>
<a href="http://www.javadoc.io/doc/io.inscopemetrics.client/metrics-client">
    <img src="http://www.javadoc.io/badge/io.inscopemetrics.client/metrics-client.svg"
         alt="Javadocs">
</a>

Client implementation for publishing metrics from Java applications.


Instrumenting Your Application
------------------------------

### Add Dependency

Determine the latest version of the Java client in [Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22io.inscopemetrics.client%22%20a%3A%22metrics-client%22).

#### Maven

Add a dependency to your pom:

```xml
<dependency>
    <groupId>io.inscopemetrics.client</groupId>
    <artifactId>metrics-client</artifactId>
    <version>VERSION</version>
</dependency>
```

The Maven Central repository is included by default.

#### Gradle

Add a dependency to your build.gradle:

    compile group: 'io.inscopemetrics.client', name: 'metrics-client', version: 'VERSION'

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
    "io.inscopemetrics.client" % "metrics-client" % "VERSION"
)
```

The Maven Central repository is included by default.

### MetricsFactory

Your application should instantiate a single instance of MetricsFactory. For example:

```java
final Map<String, String> defaultDimensions = new HashMap<>();
defaultDimensions.put("service", "MyServiceName");
defaultDimensions.put("cluster", "MyService-US-Prod");
final MetricsFactory metricsFactory = TsdMetricsFactory.newInstance(
        defaultDimensions,
        Collections.singletonMap("host", BackgroundCachingHostResolver.getInstance()));
```

Please note that `BackgroundCachingHostResolver` is from [ArpNetworking Commons](https://github.com/ArpNetworking/commons)
although you can use any [Supplier](https://docs.oracle.com/javase/8/docs/api/java/util/function/Supplier.html) as the value.

Alternatively, you can customize construction using the Builder. For example:

```java
final Map<String, String> defaultDimensions = new HashMap<>();
defaultDimensions.put("service", "MyServiceName");
defaultDimensions.put("cluster", "MyService-US-Prod");
final MetricsFactory metricsFactory = new TsdMetricsFactory.Builder()
        .setDefaultDimensions(defaultDimensions)
        .setDefaultComputedDimensions(
                Collections.singletonMap(
                        "host",
                        BackgroundCachingHostResolver.getInstance()))
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

Other options on the TsdLogSink include _immediateFlush_ and _prudent_. The former is used to flush all writes to disk
immediately while the latter performs file access in a more conservative manner enabling multiple processes to write to
the same file (assuming they all have _prudent_ set to true). Setting either _immediateFlush_ or _prudent_ to _true_
will have non-trivial performance impact.

### Scoped Metrics

The `MetricsFactory` is used to create a `ScopedMetrics` instance for each unit of work. For example:

 ```java
final ScopedMetrics scopedMetrics = metricsFactory.createScopedMetrics();
```

Counters, timers and gauges are recorded against a metrics instance which must be closed at the end of a unit of work.
After the Metrics instance is closed no further measurements may be recorded against that instance.

```java
scopedMetrics.incrementCounter("foo");
scopedMetrics.startTimer("bar");
// Do something that is being timed
scopedMetrics.stopTimer("bar");
scopedMetrics.setGauge("temperature", 21.7);
scopedMetrics.addAnnotation("room", "kitchen");
scopedMetrics.close();
```

### Periodic Metrics

The `MetricsFactory` is also used to create and schedule `PeriodicMetrics`. Unlike `ScopedMetrics`, samples recorded to
`PeriodicMetrics` are flushed on a periodic schedule instead of explicitly by the user. The default execution model
for `PeriodicMetrics` is to execute on the clock edge of the specified interval every interval. For example, if a
`PeriodicMetrics` instance is created with a 5 minute interval at 13:33 then the first execution is at 13:35. The
following execution will be at 13:40. The scheduled execution functionality is provided by `PeriodicMetricsExecutor`.

```java
final PeriodicMetrics periodicMetrics = metricsFactory.schedulePeriodicMetrics(Duration.ofMinutes(5));
periodicMetrics.recordGauge("foo", 1);
periodicMetrics.registerPolledMetric(metrics -> metrics.recordCounter("bar", 1));
```

The execution of polled metric callbacks happens serially within each `PeriodicMetrics` instance. Consequently, 
callbacks should execute quickly, ideally capturing in-memory state. For example, by using Java's [LongAccumuator](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/atomic/LongAccumulator.html).
The execution of independent `PeriodicMetrics` is performed in parallel. Consequently, creating many `PeriodicMetrics`
instances can lead to high execution overhead and create periodic resource starvation on shared clock edges. If you
require more fined grained control over execution, consider the alternative below.  

Alternatively, clients may create both `ThreadSafePeriodicMetrics` and `LockFreePeriodicMetrics` directly from using
their respective builder implementations. These can either be scheduled against a user created `PeriodicMetricsExecutor`
instance or against any alternative user provided scheduled execution mechanism. In particular, `LockFreePeriodicMetrics` 
instances are intended for scheduling by single-thread of execution frameworks like Akka and Vert.x. 

```java
final ThreadSafePeriodicMetrics periodicMetrics = new ThreadSafePeriodicMetrics.Builder()
        .setMetricsFactory(metricsFactory)
        .build();
final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
executor.scheduleAtFixedRate(periodicMetrics, 0, 1, TimeUnit.MINUTES);
```

### Injection

Passing the `MetricsFactory` instance around your code is far from ideal. We strongly recommend a combination of two
techniques to keep metrics from polluting your interfaces. First, use a dependency injection framework like Spring or
Guice to create your `TsdMetricsFactory` instance and inject it into the parts of your code that initiate units of work.

Next, the unit of work entry points can leverage thread local storage to distribute the `ScopedMetrics` instance for each
unit of work transparently throughout your codebase. Log4J calls this a Mapped Diagnostics Context (MDC) and it is also
available in LogBack although you will have to create a static thread-safe store (read `ConcurrentMap`) of `ScopedMetrics`
instances since the LogBack implementation is limited to Strings.

One important note, if your unit of work leverages additional worker threads you need to pass the `ScopedMetrics` 
instance from the parent thread's MDC into the child thread's MDC. Further, if you use a framework like Akka or Vert.x
that manages your threadpool for you, then you will either need to confine a `ScopedMetrics` instance to a particular
execution context or else integrate it with the framework. This package provides a lock free implementation for such 
frameworks (see: `MetricsFactory.createLockFreeScopedMetrics()`).

### Counters

Surprisingly, counters are probably more difficult to use and interpret than timers and gauges. In the simplest case
you can just starting counting, for example, iterations of a loop:

```java
for (String s : Arrays.asList("a", "b", "c", "d", "e")) {
    metrics.incrementCounter("strings");
    // Do something in a loop
}
```

However, what happens if `listOfString` is empty? Then no sample is recorded. To always record a sample the counter should
be reset before the loop is executed:

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

The above code will produce a number of samples equal to the size of `listOfListOfStrings` (including no samples if
`listOfListOfStrings` is empty). If you move the `resetCounter` call outside the outer loop the code always produces a
single sample (even if `listOfListOfStrings` is empty). There is a significant difference between counting the total
number of strings and the number of strings per list; especially, when computing and analyzing statistics such as
percentiles.

Finally, if the loop is being executed concurrently for the same unit of work, that is for the same `ScopedMetrics`
instance, then you should use a Counter object:

```java
final Counter counter = metrics.createCounter("strings");
for (String s : Arrays.asList("a", "b", "c", "d", "e")) {
    counter.increment();
    // Do something in a loop
}
```

The `Counter` object example extends in a similar way for nested loops. Note that `Counter` objects are only available
on `ScopedMetrics` instances and not to `PeriodicMetrics` callbacks since the measurement in the latter case must
complete within the lifetime of the callback.

### Timers

Timers are very easy to use. The only note worth making is that when using timers - either procedural or via objects -
do not forget to stop/close the timer! If you fail to do this the client will log a warning and suppress any
unstopped/unclosed samples.

The timer object allows a timer sample to be detached from the Metrics instance. One common usage is in
request/response filters (e.g. for Jersey and RestEasy) where the timer is started on the inbound filter and
stopped/closed on the outbound filter. Note that `Timer` objects are only available on `ScopedMetrics` instances and not
to `PeriodicMetrics` callbacks since the measurement in the latter case must complete within the lifetime of the callback.

The timer instance is also very useful in a concurrent system when executing and thus measuring the same event multiple
times concurrently for the same unit of work. The one caveat is to ensure the timer objects are stopped/closed before
the Metrics instance is closed. Failing to do so will log a warning and suppress any samples stopped/closed after the
Metrics instance is closed.

### Gauges

Gauges are the simplest metric to record. Samples for a gauge represent spot measurements. For example, the length of a
queue or the number of active threads in a thread pool. Gauges are often used in separate units of work to measure the
state of system resources, for example the row count in a database table. However, gauges are also useful in existing
units of work, for example recording the memory in use at the beginning and end of each service request.

### Dimensions

Dimensions are used to "slice and dice" the resulting statistics. Static and dynamic dimensions specified on the 
`MetricsFactory` apply to all `ScopedMetrics` and `PeriodicMetrics` created from that `MetricsFactory`. The values of
dynamic dimensions are evaluated at the creation time of `ScopedMetrics` or every period for `PeriodicMetrics`. 

### Closeable

The Metrics interface as well as the Timer and Counter interfaces extend [Closeable](http://docs.oracle.com/javase/7/docs/api/java/io/Closeable.html)
which allows you to use Java 7's try-with-resources pattern. For example:

```java
try (Metrics metrics = metricsFactory.create()) {
    try (Timer timer = metrics.createTimer("timer")) {
        // Time unsafe operation (e.g. this may throw)
        Thread.sleep(1000);
    }
}
```

The timer instance created and started in the try statement is automatically closed (e.g. stopped) when the try is
exited either by completion of the block or by throwing an exception.

Building
--------

Prerequisites:
* _None_

Building:

    metrics-client-java> ./jdk-wrapper.sh ./mvnw verify

To use the local version you must first install it locally:

    metrics-client-java> ./jdk-wrapper.sh ./mvnw install

You can determine the version of the local build from the pom file. Using the local version is intended only for testing or development.

You may also need to add the local repository to your build in order to pick-up the local version:

* Maven - Included by default.
* Gradle - Add *mavenLocal()* to *build.gradle* in the *repositories* block.
* SBT - Add *resolvers += Resolver.mavenLocal* into *project/plugins.sbt*.

License
-------

Published under Apache Software License 2.0, see LICENSE

&copy; Inscope Metrics Inc., 2020
