package com.ibm.kafkastream.mapping.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;

import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.serialization.LongDeserializer;
import org.apache.kafka.common.serialization.LongSerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TestOutputTopic;
import org.apache.kafka.streams.TopologyTestDriver;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.test.TestRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;



public class MappingStreamAppTest {
	
    private TopologyTestDriver testDriver;
    private TestInputTopic<Long, String> inputTopic;
    private TestOutputTopic<String, Long> outputTopic;

    private final Instant recordBaseTime = Instant.parse("2019-06-01T10:00:00Z");
    private final Duration advance1Min = Duration.ofMinutes(1);

    
    @BeforeEach
    public void setup() {
        final StreamsBuilder builder = new StreamsBuilder();
        //Create Actual Stream Processing pipeline
        MappingStreamApp.createStreamKeyIsLongValueIsString(builder);
        testDriver = new TopologyTestDriver(builder.build(), MappingStreamApp.getStreamsConfig());
        inputTopic = testDriver.createInputTopic(MappingStreamApp.INPUT_TOPIC, new LongSerializer(), new StringSerializer(),
                recordBaseTime, advance1Min);
        outputTopic = testDriver.createOutputTopic(MappingStreamApp.OUTPUT_TOPIC, new StringDeserializer(), new LongDeserializer());
    }
    
    @AfterEach
    public void tearDown() {
        try {
            testDriver.close();
        } catch (final RuntimeException e) {
            // https://issues.apache.org/jira/browse/KAFKA-6647 causes exception when executed in Windows, ignoring it
            // Logged stacktrace cannot be avoided
            System.out.println("Ignoring exception, test failing in Windows due this exception:" + e.getLocalizedMessage());
        }
    }
    
    @Test
    public void testOnlyValue() {
        //Feed 9 as key and word "Hello" as value to inputTopic
        inputTopic.pipeInput(9L, "Hello");
        //Read value and validate it, ignore validation of kafka key, timestamp is irrelevant in this case
        assertThat(outputTopic.readValue()).isEqualTo(9L);
        //No more output in topic
        assertThat(outputTopic.isEmpty()).isTrue();
    }
    
    @Test
    public void testReadFromEmptyTopic() {
        inputTopic.pipeInput(9L, "Hello");
        assertThat(outputTopic.readValue()).isEqualTo(9L);
        //Reading from empty topic generate Exception
        assertThatExceptionOfType(NoSuchElementException.class).isThrownBy(() -> {
            outputTopic.readValue();
        }).withMessage("Empty topic: %s", MappingStreamApp.OUTPUT_TOPIC);
    }

    @Test
    public void testKeyValue() {
        //Feed 9 as key and word "Hello" as value to inputTopic
        inputTopic.pipeInput(9L, "Hello");
        //Read KeyValue and validate it, timestamp is irrelevant in this case
        assertThat(outputTopic.readKeyValue()).isEqualTo(new KeyValue<>("Hello", 9L));
        //No more output in topic
        assertThat(outputTopic.isEmpty()).isTrue();
    }

    @Test
    public void testKeyValueTimestamp() {
        final Instant recordTime = Instant.parse("2019-06-01T10:00:00Z");
        //Feed 9 as key and word "Hello" as value to inputTopic with record timestamp
        inputTopic.pipeInput(9L, "Hello", recordTime);
        //Read TestRecord and validate it
        assertThat(outputTopic.readRecord()).isEqualTo(new TestRecord<>("Hello", 9L, recordTime));
        //No more output in topic
        assertThat(outputTopic.isEmpty()).isTrue();
    }

    @Test
    public void testHeadersIgnoringTimestamp() {
        final Headers headers = new RecordHeaders(
                new Header[]{
                        new RecordHeader("foo", "value".getBytes())
                });
        //Feed 9 as key, word "Hello" as value and header to inputTopic with record timestamp filled by processing
        inputTopic.pipeInput(new TestRecord<>(9L, "Hello", headers));
        //Using isEqualToIgnoringNullFields to ignore validating recordtime
        assertThat(outputTopic.readRecord()).isEqualToIgnoringNullFields(new TestRecord<>("Hello", 9L, headers));
        assertThat(outputTopic.isEmpty()).isTrue();
    }

    @Test
    public void testKeyValueList() {
        final List<String> inputList = Arrays.asList("This", "is", "an", "example");
        final List<KeyValue<String, Long>> expected = new LinkedList<>();
        for (final String s : inputList) {
            //Expected list contains original values as keys
            expected.add(new KeyValue<>(s, null));
        }
        //Pipe in value list
        inputTopic.pipeValueList(inputList);
        assertThat(outputTopic.readKeyValuesToList()).hasSameElementsAs(expected);
    }

    @Test
    public void testRecordList() {
        final List<String> inputList = Arrays.asList("This", "is", "an", "example");
        final List<KeyValue<Long, String>> input = new LinkedList<>();
        final List<TestRecord<String, Long>> expected = new LinkedList<>();
        long i = 1;
        Instant recordTime = recordBaseTime;
        for (final String s : inputList) {
            input.add(new KeyValue<>(i, s));
            //Expected entries have key and value swapped and recordTime advancing 1 minute in each
            expected.add(new TestRecord<>(s, i++, recordTime));
            recordTime = recordTime.plus(advance1Min);
            i++;
        }
        //Pipe in KeyValue list
        inputTopic.pipeKeyValueList(input);
        assertThat(outputTopic.readRecordsToList()).hasSameElementsAs(expected);
    }

    @Test
    public void testValueMap() {
        final List<String> inputList = Arrays.asList("a", "b", "c", "a", "b");
        final List<KeyValue<Long, String>> input = new LinkedList<>();
        long i = 1;
        for (final String s : inputList) {
            input.add(new KeyValue<>(i++, s));
        }
        //Pipe in KeyValue list
        inputTopic.pipeKeyValueList(input);
        //map contain the last index of each entry
        assertThat(outputTopic.readKeyValuesToMap()).hasSize(3)
                .containsEntry("a", 4L)
                .containsEntry("b", 5L)
                .containsEntry("c", 3L);
    }

}
