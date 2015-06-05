package musiverification;

import ddp.api.*;
import net.openhft.chronicle.engine.*;
import net.openhft.chronicle.engine.api.*;
import net.openhft.chronicle.engine.api.map.*;
import org.junit.*;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import java.util.stream.*;

public class SubscriptionModelPerformanceTests
{
    //TODO DS 50 updates / second when we have a large number of maps
    //TODO DS test having the server side on another machine
    private static final int _noOfPuts = 50;
    private static final int _noOfRunsToAverage = 1_000;
    private static final long _secondInNanos = 1_000_000_000;
    private static String _testStringFilePath = "Vols" + File.separator + "USDVolValEnvOIS-BO.xml";
    private static String _twoMbTestString;
    private static int _twoMbTestStringLength;
    private static Map<String, String> _testMap;
    private static String _mapName = "PerfTestMap";

    @BeforeClass
    public static void setUpBeforeClass() throws Exception
    {
        _twoMbTestString = TestUtils.loadSystemResourceFileToString(_testStringFilePath);
        _twoMbTestStringLength = _twoMbTestString.length();
    }

    @Before
    public void setUp() throws Exception
    {
        Chassis.resetChassis();

        //TODO DS this should use an underlying Chronicle map!
        _testMap = Chassis.acquireMap(_mapName, String.class, String.class);
        _testMap.clear();
    }

    /**
     * Test that listening to events for a given key can handle 50 updates per second of 2 MB string values.
     *
     * @throws IOException
     * @throws URISyntaxException
     */
    @Test
    public void testSubscriptionMapEventOnKeyPerformance()
    {
        String key = TestUtils.getKey(_mapName, 0);

        //Create subscriber and register
        TestChronicleKeyEventSubscriber keyEventSubscriber = new TestChronicleKeyEventSubscriber(_twoMbTestStringLength);

        Chassis.registerSubscriber(_mapName + "/" + key + "?bootstrap=false", String.class, keyEventSubscriber);

        //Perform test a number of times to allow the JVM to warm up, but verify runtime against average
        TestUtils.runMultipleTimesAndVerifyAvgRuntime(() -> {
            IntStream.range(0, _noOfPuts).forEach(i ->
            {
                _testMap.put(key, _twoMbTestString);
            });
        }, _noOfRunsToAverage, _secondInNanos);

        //Test that the correct number of events was triggered on event listener
        Assert.assertEquals(_noOfPuts * _noOfRunsToAverage, keyEventSubscriber.getNoOfEvents().get());
    }

    /**
     * Test that listening to events for a given map can handle 50 updates per second of 2 MB string values and are
     * triggering events which contain both the key and value (topic).
     *
     * @throws IOException
     * @throws URISyntaxException
     */
    @Test
    public void testSubscriptionMapEventOnTopicPerformance()
    {
        String key = TestUtils.getKey(_mapName, 0);

        //Create subscriber and register
        TestChronicleTopicSubscriber topicSubscriber = new TestChronicleTopicSubscriber(key, _twoMbTestStringLength);

        Chassis.registerTopicSubscriber(_mapName, String.class, String.class, topicSubscriber);

        //Perform test a number of times to allow the JVM to warm up, but verify runtime against average
        TestUtils.runMultipleTimesAndVerifyAvgRuntime(() -> {
            IntStream.range(0, _noOfPuts).forEach(i ->
            {
                _testMap.put(key, _twoMbTestString);
            });
        }, _noOfRunsToAverage, _secondInNanos);

        //Test that the correct number of events was triggered on event listener
        Assert.assertEquals(_noOfPuts * _noOfRunsToAverage, topicSubscriber.getNoOfEvents().get());
    }

    /**
     * Tests the performance of an event listener on the map for Insert events of 2 MB strings.
     * Expect it to handle at least 50 2 MB updates per second.
     */
    @Test
    public void testSubscriptionMapEventListenerInsertPerformance()
    {
        //Create subscriber and register
        TestChronicleMapEventListener mapEventListener = new TestChronicleMapEventListener(_mapName, _twoMbTestStringLength);

        Chassis.registerSubscriber(_mapName, MapEvent.class, e -> e.apply(mapEventListener));

        //Perform test a number of times to allow the JVM to warm up, but verify runtime against average
        TestUtils.runMultipleTimesAndVerifyAvgRuntime(() -> {
            IntStream.range(0, _noOfPuts).forEach(i ->
            {
                _testMap.put(TestUtils.getKey(_mapName, i), _twoMbTestString);
            });

            //Test that the correct number of events were triggered on event listener
            Assert.assertEquals(_noOfPuts, mapEventListener.getNoOfInsertEvents().get());
            Assert.assertEquals(0, mapEventListener.getNoOfRemoveEvents().get());
            Assert.assertEquals(0, mapEventListener.getNoOfUpdateEvents().get());

            _testMap.clear();

            mapEventListener.resetCounters();

        }, _noOfRunsToAverage, _secondInNanos);
    }

    /**
     * Tests the performance of an event listener on the map for Update events of 2 MB strings.
     * Expect it to handle at least 50 2 MB updates per second.
     */
    @Test
    public void testSubscriptionMapEventListenerUpdatePerformance()
    {
        //Put values before testing as we want to ignore the insert events
        Function<Integer, Object> putFunction = a -> _testMap.put(TestUtils.getKey(_mapName, a), _twoMbTestString);

        IntStream.range(0, _noOfPuts).forEach(i ->
        {
            putFunction.apply(i);
        });

        //Create subscriber and register
        TestChronicleMapEventListener mapEventListener = new TestChronicleMapEventListener(_mapName, _twoMbTestStringLength);

        Chassis.registerSubscriber(_mapName + "?bootstrap=false", MapEvent.class, e -> e.apply(mapEventListener));

        //Perform test a number of times to allow the JVM to warm up, but verify runtime against average
        TestUtils.runMultipleTimesAndVerifyAvgRuntime(() -> {
            IntStream.range(0, _noOfPuts).forEach(i ->
            {
                putFunction.apply(i);
            });

            //Test that the correct number of events were triggered on event listener
            Assert.assertEquals(0, mapEventListener.getNoOfInsertEvents().get());
            Assert.assertEquals(0, mapEventListener.getNoOfRemoveEvents().get());
            Assert.assertEquals(_noOfPuts, mapEventListener.getNoOfUpdateEvents().get());

            mapEventListener.resetCounters();

        }, _noOfRunsToAverage, _secondInNanos);
    }

    /**
     * Tests the performance of an event listener on the map for Remove events of 2 MB strings.
     * Expect it to handle at least 50 2 MB updates per second.
     */
    @Test
    public void testSubscriptionMapEventListenerRemovePerformance()
    {
        //Put values before testing as we want to ignore the insert and update events

        //Create subscriber and register
        TestChronicleMapEventListener mapEventListener = new TestChronicleMapEventListener(_mapName, _twoMbTestStringLength);

        Chassis.registerSubscriber(_mapName + "?bootstrap=false", MapEvent.class, e -> e.apply(mapEventListener));

        //Perform test a number of times to allow the JVM to warm up, but verify runtime against average
        long runtimeInNanos = 0;

        for (int i = 0; i < _noOfRunsToAverage; i++)
        {
            //Put values before testing as we want to ignore the insert and update events
            IntStream.range(0, _noOfPuts).forEach(c ->
            {
                _testMap.put(TestUtils.getKey(_mapName, c), _twoMbTestString);
            });

            mapEventListener.resetCounters();

            long startTime = System.nanoTime();

            IntStream.range(0, _noOfPuts).forEach(c ->
            {
                _testMap.remove(TestUtils.getKey(_mapName, c));
            });

            runtimeInNanos += System.nanoTime() - startTime;

            //Test that the correct number of events were triggered on event listener
            Assert.assertEquals(0, mapEventListener.getNoOfInsertEvents().get());
            Assert.assertEquals(_noOfPuts, mapEventListener.getNoOfRemoveEvents().get());
            Assert.assertEquals(0, mapEventListener.getNoOfUpdateEvents().get());
        }

        Assert.assertTrue((runtimeInNanos / (_noOfPuts * _noOfRunsToAverage)) <= _secondInNanos);
    }

    /**
     * Checks that all updates triggered are for the key specified in the constructor and increments the number of
     * updates.
     */
    class TestChronicleKeyEventSubscriber implements KeySubscriber<String>
    {
        private int _stringLength;
        private AtomicInteger _noOfEvents = new AtomicInteger(0);

        public TestChronicleKeyEventSubscriber(int stringLength)
        {
            _stringLength = stringLength;
        }

        public AtomicInteger getNoOfEvents()
        {
            return _noOfEvents;
        }

        @Override
        public void onMessage(String newValue)
        {
            Assert.assertEquals(_stringLength, newValue.length());
            _noOfEvents.incrementAndGet();
        }
    }

    /**
     * Topic subscriber checking for each message that it is for the right key (in constructor) and the expected size
     * value.
     * Increments event counter which can be checked at the end of the test.
     */
    class TestChronicleTopicSubscriber implements TopicSubscriber<String, String>
    {
        private String _keyName;
        private int _stringLength;
        private AtomicInteger _noOfEvents = new AtomicInteger(0);

        public TestChronicleTopicSubscriber(String keyName, int stringLength)
        {
            _keyName = keyName;
            _stringLength = stringLength;
        }

        /**
         * Test that the topic/key is the one specified in constructor and the message is the expected size.
         *
         * @throws InvalidSubscriberException
         */
        @Override
        public void onMessage(String topic, String message) throws InvalidSubscriberException
        {
            Assert.assertEquals(_keyName, topic);
            Assert.assertEquals(_stringLength, message.length());

            _noOfEvents.incrementAndGet();
        }

        public AtomicInteger getNoOfEvents()
        {
            return _noOfEvents;
        }
    }

    /**
     * Map event listener for performance testing. Checks that the key is the one expected and the size of the value is
     * as expected.
     * Increments event specific counters that can be used to check agains the expected number of events.
     */
    class TestChronicleMapEventListener implements MapEventListener<String, String>
    {
        private AtomicInteger _noOfInsertEvents = new AtomicInteger(0);
        private AtomicInteger _noOfUpdateEvents = new AtomicInteger(0);
        private AtomicInteger _noOfRemoveEvents = new AtomicInteger(0);

        private String _mapName;
        private int _stringLength;

        public TestChronicleMapEventListener(String mapName, int stringLength)
        {
            _mapName = mapName;
            _stringLength = stringLength;
        }

        @Override
        public void update(String key, String oldValue, String newValue)
        {
            testKeyAndValue(key, newValue, _noOfUpdateEvents);
        }

        @Override
        public void insert(String key, String value)
        {
            testKeyAndValue(key, value, _noOfInsertEvents);
        }

        @Override
        public void remove(String key, String value)
        {
            testKeyAndValue(key, value, _noOfRemoveEvents);
        }

        public AtomicInteger getNoOfInsertEvents()
        {
            return _noOfInsertEvents;
        }

        public AtomicInteger getNoOfUpdateEvents()
        {
            return _noOfUpdateEvents;
        }

        public AtomicInteger getNoOfRemoveEvents()
        {
            return _noOfRemoveEvents;
        }

        public void resetCounters()
        {
            _noOfInsertEvents = new AtomicInteger(0);
            _noOfUpdateEvents = new AtomicInteger(0);
            _noOfRemoveEvents = new AtomicInteger(0);
        }

        private void testKeyAndValue(String key, String value, AtomicInteger counterToIncrement)
        {
            int counter = counterToIncrement.getAndIncrement();
            Assert.assertEquals(TestUtils.getKey(_mapName, counter), key);
            Assert.assertEquals(_stringLength, value.length());
        }
    }
}