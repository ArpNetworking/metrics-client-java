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

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.encoder.Encoder;
import com.arpnetworking.logback.StenoEncoder;
import com.arpnetworking.logback.StenoMarker;
import com.arpnetworking.metrics.CompoundUnit;
import com.arpnetworking.metrics.Event;
import com.arpnetworking.metrics.Quantity;
import com.arpnetworking.metrics.Unit;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Implementation of <code>Sink</code> for the query log file with Steno
 * wrapped events.
 *
 * @author Ville Koskela (vkoskela at groupon dot com)
 */
public class StenoLogSink extends BaseFileSink {

    /**
     * {@inheritDoc}
     */
    @Override
    public void record(final Event event) {

        // CHECKSTYLE.OFF: IllegalInstantiation - No Guava dependency here.
        final Map<String, String> filteredAnnotations = new HashMap<>(event.getAnnotations());
        // CHECKSTYLE.ON: IllegalInstantiation
        filteredAnnotations.remove("_host");
        filteredAnnotations.remove("_id");

        try {
            getMetricsLogger().info(
                    StenoMarker.OBJECT_JSON_MARKER,
                    "aint.metrics",
                    _objectMapper.writeValueAsString(new TsdEvent(
                            filteredAnnotations,
                            event.getTimerSamples(),
                            event.getCounterSamples(),
                            event.getGaugeSamples())));
        } catch (final IOException e) {
            _logger.warn("Exception recording event", e);
        }
    }

    private static Encoder<ILoggingEvent> createEncoder(final boolean immediateFlush) {
        final StenoEncoder encoder = new StenoEncoder();
        encoder.setInjectContextClass(false);
        encoder.setInjectContextFile(false);
        encoder.setInjectContextHost(true);
        encoder.setInjectContextLine(false);
        encoder.setInjectContextLogger(false);
        encoder.setInjectContextMethod(false);
        encoder.setInjectContextProcess(true);
        encoder.setInjectContextThread(true);
        encoder.setRedactEnabled(true);
        encoder.setRedactNull(true);
        encoder.setImmediateFlush(immediateFlush);
        return encoder;
    }

    /**
     * Protected constructor.
     *
     * @param builder Instance of <code>Builder</code>.
     */
    protected StenoLogSink(final Builder builder) {
        this(builder, OBJECT_MAPPER, LOGGER);
    }

    // NOTE: Package private for testing
    /* package private */ StenoLogSink(final Builder builder, final ObjectMapper objectMapper, final org.slf4j.Logger logger) {
        super(builder, createEncoder(builder._immediateFlush.booleanValue()));
        _objectMapper = objectMapper;
        _logger = logger;
    }

    private final ObjectMapper _objectMapper;
    private final org.slf4j.Logger _logger;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(StenoLogSink.class);

    static {
        final SimpleModule simpleModule = new SimpleModule("StenoLogSink");
        simpleModule.addSerializer(Event.class, EventSerializer.newInstance());
        simpleModule.addSerializer(Quantity.class, QuantitySerializer.newInstance());
        OBJECT_MAPPER.setSerializationInclusion(JsonInclude.Include.ALWAYS);
        OBJECT_MAPPER.registerModule(simpleModule);
    }

    private static final class EventSerializer extends JsonSerializer<Event> {

        public static JsonSerializer<Event> newInstance() {
            return new EventSerializer();
        }

        @Override
        public void serialize(
                final Event event,
                final JsonGenerator jsonGenerator,
                final SerializerProvider provider)
                throws IOException {

            jsonGenerator.writeStartObject();
            jsonGenerator.writeStringField("version", VERSION);
            jsonGenerator.writeObjectField("annotations", event.getAnnotations());
            if (!event.getCounterSamples().isEmpty()) {
                jsonGenerator.writeObjectFieldStart("counters");
                serializeSamples(event.getCounterSamples(), jsonGenerator);
                jsonGenerator.writeEndObject();
            }
            if (!event.getGaugeSamples().isEmpty()) {
                jsonGenerator.writeObjectFieldStart("gauges");
                serializeSamples(event.getGaugeSamples(), jsonGenerator);
                jsonGenerator.writeEndObject();
            }
            if (!event.getTimerSamples().isEmpty()) {
                jsonGenerator.writeObjectFieldStart("timers");
                serializeSamples(event.getTimerSamples(), jsonGenerator);
                jsonGenerator.writeEndObject();
            }
            jsonGenerator.writeEndObject();
        }

        private void serializeSamples(
                final Map<String, ? extends Collection<? extends Quantity>> samples,
                final JsonGenerator jsonGenerator)
                throws IOException {
            for (final Map.Entry<String, ? extends Collection<? extends Quantity>> entry : samples.entrySet()) {
                jsonGenerator.writeObjectFieldStart(entry.getKey());
                jsonGenerator.writeObjectField("values", entry.getValue());
                jsonGenerator.writeEndObject();
            }
        }

        private EventSerializer() {}

        private static final String VERSION = "2f";
    }

    private static final class QuantitySerializer extends JsonSerializer<Quantity> {

        public static JsonSerializer<Quantity> newInstance() {
            return new QuantitySerializer();
        }

        @Override
        public void serialize(
                final Quantity valueWithUnit,
                final JsonGenerator jsonGenerator,
                final SerializerProvider provider)
                throws IOException {
            jsonGenerator.writeStartObject();
            jsonGenerator.writeObjectField("value", valueWithUnit.getValue());
            final Unit unit = valueWithUnit.getUnit();
            if (unit != null) {
                @Nullable final Unit simplifiedUnit = new TsdCompoundUnit.Builder()
                        .addNumeratorUnit(unit)
                        .build();

                if (simplifiedUnit instanceof CompoundUnit) {
                    final CompoundUnit compoundUnit = (CompoundUnit) simplifiedUnit;
                    if (!compoundUnit.getNumeratorUnits().isEmpty()) {
                        writeUnits(jsonGenerator, compoundUnit.getNumeratorUnits(), "numeratorUnits");
                    }
                    if (!compoundUnit.getDenominatorUnits().isEmpty()) {
                        writeUnits(jsonGenerator, compoundUnit.getDenominatorUnits(), "denominatorUnits");
                    }
                } else if (simplifiedUnit != null) {
                    writeUnits(jsonGenerator, Collections.singletonList(simplifiedUnit), "numeratorUnits");
                }

            }
            jsonGenerator.writeEndObject();
        }

        private void writeUnits(
                final JsonGenerator jsonGenerator,
                final List<Unit> units,
                final String name) throws IOException {
            jsonGenerator.writeArrayFieldStart(name);
            for (final Unit unit : units) {
                jsonGenerator.writeString(unit.getName());
            }
            jsonGenerator.writeEndArray();
        }
    }

    /**
     * Builder for <code>StenoLogSink</code>.
     *
     * This class is thread safe.
     *
     * @author Ville Koskela (vkoskela at groupon dot com)
     */
    public static class Builder extends BaseFileSink.Builder<StenoLogSink, Builder> {

        /**
         * {@inheritDoc}
         */
        @Override
        protected StenoLogSink createSink() {
            return new StenoLogSink(this);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected Builder self() {
            return this;
        }
    }
}
