Inscope Metrics Build Resources
===============================

1.0.0 - TBD
------------------------
* Http sink request timeouts.
* Http sink request retries with exponential backoff.

1.0.0-RC2 - TBD
------------------------
* Includes `FileSink` from [com.arpnetworking.metrics.extras:file-sink-extra](https://github.com/ArpNetworking/metrics-file-sink-extra)
* Splits serializers and sinks into distinct interfaces and implementations.
* Converts `HttpSink` periodic self-instrumentation to use `ThreadSafePeriodicMetrics`.
* Switches all repetitive logging to `RateLimitedLogger` from [com.arpnetworking.commons:commons](https://github.com/ArpNetworking/commons).
* All builder implementations are extensible.
* Removes `TsdQuantity`.

1.0.0-RC1 - August 7, 2020
------------------------
* First release candidate released to `io.inscopemetrics.client`.
* Includes `HttpSink` from [com.arpnetworking.metrics.extras:apache-http-sink-extra](https://github.com/ArpNetworking/metrics-apache-http-sink-extra).
* Includes `ThreadSafePeriodicMetrics` from [com.arpnetworking.metrics.extras:incubator-extras](https://github.com/ArpNetworking/metrics-incubator-extra).
* Includes `PeriodicJvmMetrics` from [com.arpnetworking.metrics.extras:jvm-extra](https://github.com/ArpNetworking/metrics-jvm-extra).
* Adds `LockFreePeriodicMetrics` and `LockFreeScopedMetrics`.
* Reworks `HttpSink` endpoint configuration.
* End to end shutdown (via `close()`) of metrics stack from `MetricsFactory`.
