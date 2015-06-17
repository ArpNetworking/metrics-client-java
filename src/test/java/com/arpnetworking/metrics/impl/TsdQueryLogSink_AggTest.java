/**
 * Copyright 2014 Groupon.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.arpnetworking.metrics.impl;

import com.google.common.collect.ImmutableMap;

import com.arpnetworking.metrics.Quantity;
import com.arpnetworking.metrics.Sink;

import org.json.JSONException;
import org.junit.Test;
import org.skyscreamer.jsonassert.JSONCompare;
import org.skyscreamer.jsonassert.JSONCompareMode;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

/**
 * User: rascheman
 * Date: 6/16/15
 * Time: 10:06 AM
 */
public class TsdQueryLogSink_AggTest extends TsdQueryLogSinkTest {
    private static final Map<String, String> ANNOTATIONS = ImmutableMap.of("initTimestamp", "1997-07-16T19:20:30Z",
                                                                           "finalTimestamp", "1997-07-16T19:20:31Z");

    private static final Map<String, List<Quantity>> SERIALIZATION_COUNTERS = createQuantityMap(
        "counter^^A",       TsdQuantity.newInstance(1L, null),
        "counter^^B",       TsdQuantity.newInstance(2L, null),
        "signalLast^",      TsdQuantity.newInstance(3L, null),  // supported but not productive
        "^signalFirst",     TsdQuantity.newInstance(4L, null),  // ignored
        "signal^Spl^it",    TsdQuantity.newInstance(5L, null),  // possible future multiple level aggregation
        "signalNone",       TsdQuantity.newInstance(6L, null)); // ignored

    private static final String EXPECTED_AGGREGATED_JSON =
          "{"
        +   "\"time\":\"<TIME>\","
        +   "\"name\":\"aint.metrics\","
        +   "\"level\":\"info\","
        +   "\"data\":{"
        +     "\"version\":\"2e\","
        +     "\"annotations\":{"
        +       "\"initTimestamp\":\"1997-07-16T19:20:30Z\","
        +       "\"finalTimestamp\":\"1997-07-16T19:20:31Z\""
        +     "},"
        +     "\"counters\":{"
        +       "\"counterA\":{\"values\":[{\"value\":1}]},"
        +       "\"counterB\":{\"values\":[{\"value\":2}]},"
        +       "\"counterXX\":{\"values\":[{\"value\":1},{\"value\":2}]},"
        +       "\"signalLastX\":{\"values\":[{\"value\":3}]},"
        +       "\"signalLast\":{\"values\":[{\"value\":3}]},"
        +       "\"signalFirst\":{\"values\":[{\"value\":4}]},"
        +       "\"signalSplit\":{\"values\":[{\"value\":5}]},"
        +       "\"signalXSplX\":{\"values\":[{\"value\":5}]},"
        +       "\"signalNone\":{\"values\":[{\"value\":6}]}"
        +     "}"
        +   "},"
        +   "\"context\":{"
        +     "\"host\":\"<HOST>\","
        +     "\"processId\":\"<PROCESSID>\","
        +     "\"threadId\":\"<THREADID>\""
        +   "},"
        +   "\"id\":\"<ID>\","
        +   "\"version\":\"0\""
        + "}";

    @Test
    public void testMagicSerialization() throws IOException, InterruptedException, JSONException {
        final File actualFile = new File("./target/TsdQueryLogSinkTest/testSerialization-Query.log");
        Files.deleteIfExists(actualFile.toPath());

        final Sink sink = new TsdQueryLogSink_Agg.Builder_Agg()
            .setSignalReplacement("X")
            .setPath("./target/TsdQueryLogSinkTest")
            .setName("testSerialization-Query")
            .setImmediateFlush(Boolean.TRUE)
            .build();

        sink.record(
            ANNOTATIONS,
            createQuantityMap(),
            SERIALIZATION_COUNTERS,
            createQuantityMap());

        // TODO(vkoskela): Add protected option to disable async [MAI-181].
        Thread.sleep(100);

        final String actualOriginalJson = fileToString(actualFile);
        assertMatchesJsonSchema(actualOriginalJson);

        final String actualComparableJson = actualOriginalJson
            .replaceAll("\"time\":\"[^\"]*\"", "\"time\":\"<TIME>\"")
            .replaceAll("\"host\":\"[^\"]*\"", "\"host\":\"<HOST>\"")
            .replaceAll("\"processId\":\"[^\"]*\"", "\"processId\":\"<PROCESSID>\"")
            .replaceAll("\"threadId\":\"[^\"]*\"", "\"threadId\":\"<THREADID>\"")
            .replaceAll("\"id\":\"[^\"]*\"", "\"id\":\"<ID>\"");

        JSONCompare.compareJSON(EXPECTED_AGGREGATED_JSON, actualComparableJson, JSONCompareMode.NON_EXTENSIBLE);
    }
}
