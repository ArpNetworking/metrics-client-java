/**
 * Copyright 2015 Groupon.com
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

import com.arpnetworking.metrics.ComplexCompoundUnit;
import com.arpnetworking.metrics.Quantity;
import com.arpnetworking.metrics.Sink;
import com.arpnetworking.metrics.Units;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jackson.JacksonUtils;
import com.github.fge.jackson.JsonLoader;
import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import com.github.fge.jsonschema.core.report.ProcessingReport;
import com.github.fge.jsonschema.main.JsonSchemaFactory;
import com.github.fge.jsonschema.main.JsonValidator;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.UUID;

/**
 * Tests for <code>TsdMetrics</code>.
 *
 * @author Ville Koskela (vkoskela at groupon dot com)
 */
public class TsdLogSinkTest {

    @Test
    public void testObjectMapperIOException() throws IOException {
        final org.slf4j.Logger logger = createSlf4jLoggerMock();
        final ObjectMapper objectMapper = Mockito.spy(new ObjectMapper());
        final Sink sink = new TsdLogSink(
                new TsdLogSink.Builder()
                        .setDirectory(createDirectory("./target/TsdLogSinkTest"))
                        .setName("testObjectMapperIOException-Query"),
                objectMapper,
                logger);

        Mockito.doThrow(new JsonMappingException("JsonMappingException")).when(objectMapper).writeValueAsString(Mockito.any());
        recordEmpty(sink);
        Mockito.verify(logger).warn(
                Mockito.argThat(Matchers.any(String.class)),
                Mockito.argThat(Matchers.any(Throwable.class)));
    }

    @Test
    public void testEmptySerialization() throws IOException, InterruptedException {
        final File actualFile = new File("./target/TsdLogSinkTest/testEmptySerialization-Query.log");
        Files.deleteIfExists(actualFile.toPath());
        final Sink sink = new TsdLogSink.Builder()
                .setDirectory(createDirectory("./target/TsdLogSinkTest"))
                .setName("testEmptySerialization-Query")
                .setImmediateFlush(Boolean.TRUE)
                .build();

        sink.record(new TsdEvent(
                ANNOTATIONS,
                TEST_EMPTY_SERIALIZATION_TIMERS,
                TEST_EMPTY_SERIALIZATION_COUNTERS,
                TEST_EMPTY_SERIALIZATION_GAUGES));

        // TODO(vkoskela): Add protected option to disable async [MAI-181].
        Thread.sleep(100);

        final String actualOriginalJson = fileToString(actualFile);
        assertMatchesJsonSchema(actualOriginalJson);
        final String actualComparableJson = actualOriginalJson
                .replaceAll("\"_host\":\"[^\"]*\"", "\"_host\":\"<HOST>\"")
                .replaceAll("\"_id\":\"[^\"]*\"", "\"_id\":\"<ID>\"");
        final JsonNode actual = OBJECT_MAPPER.readTree(actualComparableJson);
        final JsonNode expected = OBJECT_MAPPER.readTree(EXPECTED_EMPTY_METRICS_JSON);

        Assert.assertEquals(
                "expectedJson=" + OBJECT_MAPPER.writeValueAsString(expected)
                        + " vs actualJson=" + OBJECT_MAPPER.writeValueAsString(actual),
                expected,
                actual);
    }

    @Test
    public void testSerialization() throws IOException, InterruptedException {
        final File actualFile = new File("./target/TsdLogSinkTest/testSerialization-Query.log");
        Files.deleteIfExists(actualFile.toPath());
        final Sink sink = new TsdLogSink.Builder()
                .setDirectory(createDirectory("./target/TsdLogSinkTest"))
                .setName("testSerialization-Query")
                .setImmediateFlush(Boolean.TRUE)
                .build();

        final Map<String, String> annotations = new LinkedHashMap<>(ANNOTATIONS);
        annotations.put("foo", "bar");
        sink.record(new TsdEvent(
                annotations,
                TEST_SERIALIZATION_TIMERS,
                TEST_SERIALIZATION_COUNTERS,
                TEST_SERIALIZATION_GAUGES));

        // TODO(vkoskela): Add protected option to disable async [MAI-181].
        Thread.sleep(100);

        final String actualOriginalJson = fileToString(actualFile);
        assertMatchesJsonSchema(actualOriginalJson);
        final String actualComparableJson = actualOriginalJson
                .replaceAll("\"_host\":\"[^\"]*\"", "\"_host\":\"<HOST>\"")
                .replaceAll("\"_id\":\"[^\"]*\"", "\"_id\":\"<ID>\"");
        final JsonNode actual = OBJECT_MAPPER.readTree(actualComparableJson);
        final JsonNode expected = OBJECT_MAPPER.readTree(EXPECTED_METRICS_JSON);

        Assert.assertEquals(
                "expectedJson=" + OBJECT_MAPPER.writeValueAsString(expected)
                        + " vs actualJson=" + OBJECT_MAPPER.writeValueAsString(actual),
                expected,
                actual);
    }

    private static Map<String, List<Quantity>> createQuantityMap(final Object... arguments) {
        // CHECKSTYLE.OFF: IllegalInstantiation - No Guava
        final Map<String, List<Quantity>> map = new HashMap<>();
        // CHECKSTYLE.ON: IllegalInstantiation
        List<Quantity> samples = new ArrayList<>();
        for (final Object argument : arguments) {
            if (argument instanceof String) {
                samples = new ArrayList<>();
                map.put((String) argument, samples);
            } else if (argument instanceof Quantity) {
                assert samples != null : "first argument must be metric name";
                samples.add((Quantity) argument);
            } else {
                assert false : "unsupported argument type: " + argument.getClass();
            }
        }
        return map;
    }

    private void recordEmpty(final Sink sink) {
        sink.record(new TsdEvent(
                Collections.<String, String>emptyMap(),
                Collections.<String, List<Quantity>>emptyMap(),
                Collections.<String, List<Quantity>>emptyMap(),
                Collections.<String, List<Quantity>>emptyMap()));
    }

    private org.slf4j.Logger createSlf4jLoggerMock() {
        return Mockito.mock(org.slf4j.Logger.class);
    }

    private void assertMatchesJsonSchema(final String json) {
        try {
            final JsonNode jsonNode = JsonLoader.fromString(json);
            final ProcessingReport report = VALIDATOR.validate(STENO_SCHEMA, jsonNode);
            Assert.assertTrue(report.toString(), report.isSuccess());
        } catch (final IOException | ProcessingException e) {
            Assert.fail("Failed with exception: " + e);
        }
    }

    private String fileToString(final File file) {
        try {
            return new Scanner(file, "UTF-8").useDelimiter("\\Z").next();
        } catch (final IOException ioe) {
            throw new RuntimeException(ioe);
        }
    }

    private static File createDirectory(final String path) throws IOException {
        final File directory = new File(path);
        Files.createDirectories(directory.toPath());
        return directory;
    }

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final JsonValidator VALIDATOR = JsonSchemaFactory.byDefault().getValidator();
    private static final JsonNode STENO_SCHEMA;

    private static final Map<String, String> ANNOTATIONS = new LinkedHashMap<>();
    private static final Map<String, List<Quantity>> TEST_EMPTY_SERIALIZATION_TIMERS = createQuantityMap();
    private static final Map<String, List<Quantity>> TEST_EMPTY_SERIALIZATION_COUNTERS = createQuantityMap();
    private static final Map<String, List<Quantity>> TEST_EMPTY_SERIALIZATION_GAUGES = createQuantityMap();

    private static final String EXPECTED_EMPTY_METRICS_JSON = "{"
            + "  \"version\":\"2f\","
            + "  \"annotations\":{"
            + "    \"_id\":\"<ID>\","
            + "    \"_start\":\"1997-07-16T19:20:30Z\","
            + "    \"_end\":\"1997-07-16T19:20:31Z\","
            + "    \"_service\":\"MyService\","
            + "    \"_cluster\":\"MyCluster\","
            + "    \"_host\":\"<HOST>\""
            + "  }"
            + "}";

    private static final Map<String, List<Quantity>> TEST_SERIALIZATION_TIMERS = createQuantityMap(
            "timerA",
            "timerB",
            TsdQuantity.newInstance(Long.valueOf(1L), null),
            "timerC",
            TsdQuantity.newInstance(Long.valueOf(2L), Units.MILLISECOND),
            "timerD",
            TsdQuantity.newInstance(Long.valueOf(3L), Units.SECOND),
            TsdQuantity.newInstance(Long.valueOf(4L), Units.SECOND),
            "timerE",
            TsdQuantity.newInstance(Long.valueOf(5L), Units.DAY),
            TsdQuantity.newInstance(Long.valueOf(6L), Units.SECOND),
            "timerF",
            TsdQuantity.newInstance(Long.valueOf(7L), Units.DAY),
            TsdQuantity.newInstance(Long.valueOf(8L), null),
            "timerG",
            TsdQuantity.newInstance(Long.valueOf(9L), null),
            TsdQuantity.newInstance(Long.valueOf(10L), null),
            "timerH",
            TsdQuantity.newInstance(Long.valueOf(11L), Units.DAY),
            TsdQuantity.newInstance(Long.valueOf(12L), Units.BYTE),
            "timerI",
            TsdQuantity.newInstance(Double.valueOf(1.12), null),
            "timerJ",
            TsdQuantity.newInstance(Double.valueOf(2.12), Units.MILLISECOND),
            "timerK",
            TsdQuantity.newInstance(Double.valueOf(3.12), Units.SECOND),
            TsdQuantity.newInstance(Double.valueOf(4.12), Units.SECOND),
            "timerL",
            TsdQuantity.newInstance(Double.valueOf(5.12), Units.DAY),
            TsdQuantity.newInstance(Double.valueOf(6.12), Units.SECOND),
            "timerM",
            TsdQuantity.newInstance(Double.valueOf(7.12), Units.DAY),
            TsdQuantity.newInstance(Double.valueOf(8.12), null),
            "timerN",
            TsdQuantity.newInstance(Double.valueOf(9.12), null),
            TsdQuantity.newInstance(Double.valueOf(10.12), null),
            "timerO",
            TsdQuantity.newInstance(Double.valueOf(11.12), Units.DAY),
            TsdQuantity.newInstance(Double.valueOf(12.12), Units.BYTE),
            "timerP1",
            TsdQuantity.newInstance(
                    Integer.valueOf(3),
                    new TsdCompoundUnit.Builder()
                            .addNumeratorUnit(Units.KILOBYTE)
                            .addDenominatorUnit(Units.MILLISECOND)
                            .build()),
            "timerP2",
            TsdQuantity.newInstance(
                    Integer.valueOf(3),
                    new TsdCompoundUnit.Builder()
                            .addDenominatorUnit(Units.MILLISECOND)
                            .build()),
            "timerP3",
            TsdQuantity.newInstance(
                    Integer.valueOf(3),
                    new TsdCompoundUnit.Builder()
                            .addNumeratorUnit(Units.KILOBYTE)
                            .build()),
            "timerP4",
            TsdQuantity.newInstance(
                    Integer.valueOf(3),
                    new TsdUnit.Builder()
                            .setScale(BaseScale.KILO)
                            .build()),
            "timerP5",
            TsdQuantity.newInstance(
                    Integer.valueOf(3),
                    new TsdCompoundUnit.Builder()
                            .addNumeratorUnit(Units.BYTE)
                            .addNumeratorUnit(Units.SECOND)
                            .build()),
            "timerP6",
            TsdQuantity.newInstance(
                    Integer.valueOf(3),
                    new ComplexCompoundUnit(
                            "CustomByteOverByte",
                            Arrays.asList(Units.BYTE),
                            Arrays.asList(Units.BYTE))));

    private static final Map<String, List<Quantity>> TEST_SERIALIZATION_COUNTERS = createQuantityMap(
            "counterA",
            "counterB",
            TsdQuantity.newInstance(Long.valueOf(11L), null),
            "counterC",
            TsdQuantity.newInstance(Long.valueOf(12L), Units.MILLISECOND),
            "counterD",
            TsdQuantity.newInstance(Long.valueOf(13L), Units.SECOND),
            TsdQuantity.newInstance(Long.valueOf(14L), Units.SECOND),
            "counterE",
            TsdQuantity.newInstance(Long.valueOf(15L), Units.DAY),
            TsdQuantity.newInstance(Long.valueOf(16L), Units.SECOND),
            "counterF",
            TsdQuantity.newInstance(Long.valueOf(17L), Units.DAY),
            TsdQuantity.newInstance(Long.valueOf(18L), null),
            "counterG",
            TsdQuantity.newInstance(Long.valueOf(19L), null),
            TsdQuantity.newInstance(Long.valueOf(110L), null),
            "counterH",
            TsdQuantity.newInstance(Long.valueOf(111L), Units.DAY),
            TsdQuantity.newInstance(Long.valueOf(112L), Units.BYTE),
            "counterI",
            TsdQuantity.newInstance(Double.valueOf(11.12), null),
            "counterJ",
            TsdQuantity.newInstance(Double.valueOf(12.12), Units.MILLISECOND),
            "counterK",
            TsdQuantity.newInstance(Double.valueOf(13.12), Units.SECOND),
            TsdQuantity.newInstance(Double.valueOf(14.12), Units.SECOND),
            "counterL",
            TsdQuantity.newInstance(Double.valueOf(15.12), Units.DAY),
            TsdQuantity.newInstance(Double.valueOf(16.12), Units.SECOND),
            "counterM",
            TsdQuantity.newInstance(Double.valueOf(17.12), Units.DAY),
            TsdQuantity.newInstance(Double.valueOf(18.12), null),
            "counterN",
            TsdQuantity.newInstance(Double.valueOf(19.12), null),
            TsdQuantity.newInstance(Double.valueOf(110.12), null),
            "counterO",
            TsdQuantity.newInstance(Double.valueOf(111.12), Units.DAY),
            TsdQuantity.newInstance(Double.valueOf(112.12), Units.BYTE),
            "counterP1",
            TsdQuantity.newInstance(
                    Integer.valueOf(3),
                    new TsdCompoundUnit.Builder()
                            .addNumeratorUnit(Units.KILOBYTE)
                            .addDenominatorUnit(Units.MILLISECOND)
                            .build()),
            "counterP2",
            TsdQuantity.newInstance(
                    Integer.valueOf(3),
                    new TsdCompoundUnit.Builder()
                            .addDenominatorUnit(Units.MILLISECOND)
                            .build()),
            "counterP3",
            TsdQuantity.newInstance(
                    Integer.valueOf(3),
                    new TsdCompoundUnit.Builder()
                            .addNumeratorUnit(Units.KILOBYTE)
                            .build()),
            "counterP4",
            TsdQuantity.newInstance(
                    Integer.valueOf(3),
                    new TsdUnit.Builder()
                            .setScale(BaseScale.KILO)
                            .build()),
            "counterP5",
            TsdQuantity.newInstance(
                    Integer.valueOf(3),
                    new TsdCompoundUnit.Builder()
                            .addNumeratorUnit(Units.BYTE)
                            .addNumeratorUnit(Units.SECOND)
                            .build()));


    private static final Map<String, List<Quantity>> TEST_SERIALIZATION_GAUGES = createQuantityMap(
            "gaugeA",
            "gaugeB",
            TsdQuantity.newInstance(Long.valueOf(21L), null),
            "gaugeC",
            TsdQuantity.newInstance(Long.valueOf(22L), Units.MILLISECOND),
            "gaugeD",
            TsdQuantity.newInstance(Long.valueOf(23L), Units.SECOND),
            TsdQuantity.newInstance(Long.valueOf(24L), Units.SECOND),
            "gaugeE",
            TsdQuantity.newInstance(Long.valueOf(25L), Units.DAY),
            TsdQuantity.newInstance(Long.valueOf(26L), Units.SECOND),
            "gaugeF",
            TsdQuantity.newInstance(Long.valueOf(27L), Units.DAY),
            TsdQuantity.newInstance(Long.valueOf(28L), null),
            "gaugeG",
            TsdQuantity.newInstance(Long.valueOf(29L), null),
            TsdQuantity.newInstance(Long.valueOf(210L), null),
            "gaugeH",
            TsdQuantity.newInstance(Long.valueOf(211L), Units.DAY),
            TsdQuantity.newInstance(Long.valueOf(212L), Units.BYTE),
            "gaugeI",
            TsdQuantity.newInstance(Double.valueOf(21.12), null),
            "gaugeJ",
            TsdQuantity.newInstance(Double.valueOf(22.12), Units.MILLISECOND),
            "gaugeK",
            TsdQuantity.newInstance(Double.valueOf(23.12), Units.SECOND),
            TsdQuantity.newInstance(Double.valueOf(24.12), Units.SECOND),
            "gaugeL",
            TsdQuantity.newInstance(Double.valueOf(25.12), Units.DAY),
            TsdQuantity.newInstance(Double.valueOf(26.12), Units.SECOND),
            "gaugeM",
            TsdQuantity.newInstance(Double.valueOf(27.12), Units.DAY),
            TsdQuantity.newInstance(Double.valueOf(28.12), null),
            "gaugeN",
            TsdQuantity.newInstance(Double.valueOf(29.12), null),
            TsdQuantity.newInstance(Double.valueOf(210.12), null),
            "gaugeO",
            TsdQuantity.newInstance(Double.valueOf(211.12), Units.DAY),
            TsdQuantity.newInstance(Double.valueOf(212.12), Units.BYTE),
            "gaugeP1",
            TsdQuantity.newInstance(
                    Integer.valueOf(3),
                    new TsdCompoundUnit.Builder()
                            .addNumeratorUnit(Units.KILOBYTE)
                            .addDenominatorUnit(Units.MILLISECOND)
                            .build()),
            "gaugeP2",
            TsdQuantity.newInstance(
                    Integer.valueOf(3),
                    new TsdCompoundUnit.Builder()
                            .addDenominatorUnit(Units.MILLISECOND)
                            .build()),
            "gaugeP3",
            TsdQuantity.newInstance(
                    Integer.valueOf(3),
                    new TsdCompoundUnit.Builder()
                            .addNumeratorUnit(Units.KILOBYTE)
                            .build()),
            "gaugeP4",
            TsdQuantity.newInstance(
                    Integer.valueOf(3),
                    new TsdUnit.Builder()
                            .setScale(BaseScale.KILO)
                            .build()),
            "gaugeP5",
            TsdQuantity.newInstance(
                    Integer.valueOf(3),
                    new TsdCompoundUnit.Builder()
                            .addNumeratorUnit(Units.BYTE)
                            .addNumeratorUnit(Units.SECOND)
                            .build()),
            "gaugeP6",
            TsdQuantity.newInstance(
                    Integer.valueOf(3),
                    new ComplexCompoundUnit(
                            "CustomByteOverByte",
                            Arrays.asList(Units.BYTE),
                            Arrays.asList(Units.BYTE))));

    // CHECKSTYLE.OFF: LineLengthCheck - One value per line.
    private static final String EXPECTED_METRICS_JSON = "{"
            + "  \"version\":\"2f\","
            + "  \"annotations\":{"
            + "    \"_id\":\"<ID>\","
            + "    \"_start\":\"1997-07-16T19:20:30Z\","
            + "    \"_end\":\"1997-07-16T19:20:31Z\","
            + "    \"_service\":\"MyService\","
            + "    \"_cluster\":\"MyCluster\","
            + "    \"_host\":\"<HOST>\","
            + "    \"foo\":\"bar\""
            + "  },"
            + "  \"counters\":{"
            + "    \"counterA\":{\"values\":[]},"
            + "    \"counterB\":{\"values\":[{\"value\":11}]},"
            + "    \"counterC\":{\"values\":[{\"value\":12,\"numeratorUnits\":[\"millisecond\"]}]},"
            + "    \"counterD\":{\"values\":[{\"value\":13,\"numeratorUnits\":[\"second\"]},{\"value\":14,\"numeratorUnits\":[\"second\"]}]},"
            + "    \"counterE\":{\"values\":[{\"value\":15,\"numeratorUnits\":[\"day\"]},{\"value\":16,\"numeratorUnits\":[\"second\"]}]},"
            + "    \"counterF\":{\"values\":[{\"value\":17,\"numeratorUnits\":[\"day\"]},{\"value\":18}]},"
            + "    \"counterG\":{\"values\":[{\"value\":19},{\"value\":110}]},"
            + "    \"counterH\":{\"values\":[{\"value\":111,\"numeratorUnits\":[\"day\"]},{\"value\":112,\"numeratorUnits\":[\"byte\"]}]},"
            + "    \"counterI\":{\"values\":[{\"value\":11.12}]},"
            + "    \"counterJ\":{\"values\":[{\"value\":12.12,\"numeratorUnits\":[\"millisecond\"]}]},"
            + "    \"counterK\":{\"values\":[{\"value\":13.12,\"numeratorUnits\":[\"second\"]},{\"value\":14.12,\"numeratorUnits\":[\"second\"]}]},"
            + "    \"counterL\":{\"values\":[{\"value\":15.12,\"numeratorUnits\":[\"day\"]},{\"value\":16.12,\"numeratorUnits\":[\"second\"]}]},"
            + "    \"counterM\":{\"values\":[{\"value\":17.12,\"numeratorUnits\":[\"day\"]},{\"value\":18.12}]},"
            + "    \"counterN\":{\"values\":[{\"value\":19.12},{\"value\":110.12}]},"
            + "    \"counterO\":{\"values\":[{\"value\":111.12,\"numeratorUnits\":[\"day\"]},{\"value\":112.12,\"numeratorUnits\":[\"byte\"]}]},"
            + "    \"counterP1\":{\"values\":[{\"value\":3,\"numeratorUnits\":[\"kilobyte\"],\"denominatorUnits\":[\"millisecond\"]}]},"
            + "    \"counterP2\":{\"values\":[{\"value\":3,\"denominatorUnits\":[\"millisecond\"]}]},"
            + "    \"counterP3\":{\"values\":[{\"value\":3,\"numeratorUnits\":[\"kilobyte\"]}]},"
            + "    \"counterP4\":{\"values\":[{\"value\":3}]},"
            + "    \"counterP5\":{\"values\":[{\"value\":3,\"numeratorUnits\":[\"byte\",\"second\"]}]}"
            + "  },"
            + "    \"gauges\":{"
            + "    \"gaugeA\":{\"values\":[]},"
            + "    \"gaugeB\":{\"values\":[{\"value\":21}]},"
            + "    \"gaugeC\":{\"values\":[{\"value\":22,\"numeratorUnits\":[\"millisecond\"]}]},"
            + "    \"gaugeD\":{\"values\":[{\"value\":23,\"numeratorUnits\":[\"second\"]},{\"value\":24,\"numeratorUnits\":[\"second\"]}]},"
            + "    \"gaugeE\":{\"values\":[{\"value\":25,\"numeratorUnits\":[\"day\"]},{\"value\":26,\"numeratorUnits\":[\"second\"]}]},"
            + "    \"gaugeF\":{\"values\":[{\"value\":27,\"numeratorUnits\":[\"day\"]},{\"value\":28}]},"
            + "    \"gaugeG\":{\"values\":[{\"value\":29},{\"value\":210}]},"
            + "    \"gaugeH\":{\"values\":[{\"value\":211,\"numeratorUnits\":[\"day\"]},{\"value\":212,\"numeratorUnits\":[\"byte\"]}]},"
            + "    \"gaugeI\":{\"values\":[{\"value\":21.12}]},"
            + "    \"gaugeJ\":{\"values\":[{\"value\":22.12,\"numeratorUnits\":[\"millisecond\"]}]},"
            + "    \"gaugeK\":{\"values\":[{\"value\":23.12,\"numeratorUnits\":[\"second\"]},{\"value\":24.12,\"numeratorUnits\":[\"second\"]}]},"
            + "    \"gaugeL\":{\"values\":[{\"value\":25.12,\"numeratorUnits\":[\"day\"]},{\"value\":26.12,\"numeratorUnits\":[\"second\"]}]},"
            + "    \"gaugeM\":{\"values\":[{\"value\":27.12,\"numeratorUnits\":[\"day\"]},{\"value\":28.12}]},"
            + "    \"gaugeN\":{\"values\":[{\"value\":29.12},{\"value\":210.12}]},"
            + "    \"gaugeO\":{\"values\":[{\"value\":211.12,\"numeratorUnits\":[\"day\"]},{\"value\":212.12,\"numeratorUnits\":[\"byte\"]}]},"
            + "    \"gaugeP1\":{\"values\":[{\"value\":3,\"numeratorUnits\":[\"kilobyte\"],\"denominatorUnits\":[\"millisecond\"]}]},"
            + "    \"gaugeP2\":{\"values\":[{\"value\":3,\"denominatorUnits\":[\"millisecond\"]}]},"
            + "    \"gaugeP3\":{\"values\":[{\"value\":3,\"numeratorUnits\":[\"kilobyte\"]}]},"
            + "    \"gaugeP4\":{\"values\":[{\"value\":3}]},"
            + "    \"gaugeP5\":{\"values\":[{\"value\":3,\"numeratorUnits\":[\"byte\",\"second\"]}]},"
            + "    \"gaugeP6\":{\"values\":[{\"value\":3}]}"
            + "  },"
            + "  \"timers\":{"
            + "    \"timerA\":{\"values\":[]},"
            + "    \"timerB\":{\"values\":[{\"value\":1}]},"
            + "    \"timerC\":{\"values\":[{\"value\":2,\"numeratorUnits\":[\"millisecond\"]}]},"
            + "    \"timerD\":{\"values\":[{\"value\":3,\"numeratorUnits\":[\"second\"]},{\"value\":4,\"numeratorUnits\":[\"second\"]}]},"
            + "    \"timerE\":{\"values\":[{\"value\":5,\"numeratorUnits\":[\"day\"]},{\"value\":6,\"numeratorUnits\":[\"second\"]}]},"
            + "    \"timerF\":{\"values\":[{\"value\":7,\"numeratorUnits\":[\"day\"]},{\"value\":8}]},"
            + "    \"timerG\":{\"values\":[{\"value\":9},{\"value\":10}]},"
            + "    \"timerH\":{\"values\":[{\"value\":11,\"numeratorUnits\":[\"day\"]},{\"value\":12,\"numeratorUnits\":[\"byte\"]}]},"
            + "    \"timerI\":{\"values\":[{\"value\":1.12}]},"
            + "    \"timerJ\":{\"values\":[{\"value\":2.12,\"numeratorUnits\":[\"millisecond\"]}]},"
            + "    \"timerK\":{\"values\":[{\"value\":3.12,\"numeratorUnits\":[\"second\"]},{\"value\":4.12,\"numeratorUnits\":[\"second\"]}]},"
            + "    \"timerL\":{\"values\":[{\"value\":5.12,\"numeratorUnits\":[\"day\"]},{\"value\":6.12,\"numeratorUnits\":[\"second\"]}]},"
            + "    \"timerM\":{\"values\":[{\"value\":7.12,\"numeratorUnits\":[\"day\"]},{\"value\":8.12}]},"
            + "    \"timerN\":{\"values\":[{\"value\":9.12},{\"value\":10.12}]},"
            + "    \"timerO\":{\"values\":[{\"value\":11.12,\"numeratorUnits\":[\"day\"]},{\"value\":12.12,\"numeratorUnits\":[\"byte\"]}]},"
            + "    \"timerP1\":{\"values\":[{\"value\":3,\"numeratorUnits\":[\"kilobyte\"],\"denominatorUnits\":[\"millisecond\"]}]},"
            + "    \"timerP2\":{\"values\":[{\"value\":3,\"denominatorUnits\":[\"millisecond\"]}]},"
            + "    \"timerP3\":{\"values\":[{\"value\":3,\"numeratorUnits\":[\"kilobyte\"]}]},"
            + "    \"timerP4\":{\"values\":[{\"value\":3}]},"
            + "    \"timerP5\":{\"values\":[{\"value\":3,\"numeratorUnits\":[\"byte\",\"second\"]}]},"
            + "    \"timerP6\":{\"values\":[{\"value\":3}]}"
            + "  }"
            + "}";
    // CHECKSTYLE.ON: LineLengthCheck

    private static final String SCHEMA_FILE_NAME = "query-log-schema-2f.json";

    static {
        JsonNode jsonNode;
        try {
            // Attempt to load the cached copy
            jsonNode = JsonLoader.fromPath("./target/" + SCHEMA_FILE_NAME);
        } catch (final IOException e1) {
            try {
                // Download from the source repository
                jsonNode = JsonLoader.fromURL(
                        new URL("https://raw.githubusercontent.com/ArpNetworking/metrics-client-doc/master/schema/" + SCHEMA_FILE_NAME));

                // Cache the schema file
                Files.write(
                        Paths.get("./target/" + SCHEMA_FILE_NAME),
                        JacksonUtils.prettyPrint(jsonNode).getBytes(Charset.forName("UTF-8")));
            } catch (final IOException e2) {
                throw new RuntimeException(e2);
            }
        }
        STENO_SCHEMA = jsonNode;

        ANNOTATIONS.put("_start", "1997-07-16T19:20:30Z");
        ANNOTATIONS.put("_end", "1997-07-16T19:20:31Z");
        ANNOTATIONS.put("_id", UUID.randomUUID().toString());
        ANNOTATIONS.put("_host", "<HOST>");
        ANNOTATIONS.put("_service", "MyService");
        ANNOTATIONS.put("_cluster", "MyCluster");
    }
}
