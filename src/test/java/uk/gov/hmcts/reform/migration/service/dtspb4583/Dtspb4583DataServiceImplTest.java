package uk.gov.hmcts.reform.migration.service.dtspb4583;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.migration.model.Dtspb4583Dates;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class Dtspb4583DataServiceImplTest {

    LoaderService loaderMock;

    Dtspb4583DataService dataService;
    Dtspb4583DataServiceImpl dataServiceImpl;

    @BeforeEach
    void setUp() {
        loaderMock = mock(LoaderService.class);

        dataServiceImpl = new Dtspb4583DataServiceImpl(loaderMock);
        dataService = dataServiceImpl;
    }

    @Test
    void testOnlyInitializesOnce() {
        Map<Long, Dtspb4583Dates> firstRet = Map.of(1L, new Dtspb4583Dates("first", "first"));
        Map<Long, Dtspb4583Dates> secondRet = Map.of(1L, new Dtspb4583Dates("second", "second"));

        when(loaderMock.load())
            .thenReturn(firstRet)
            .thenReturn(secondRet);

        final Map<Long, Dtspb4583Dates> firstRes = dataServiceImpl.getAll();
        final Map<Long, Dtspb4583Dates> secondRes = dataServiceImpl.getAll();

        assertSame(firstRet, firstRes);
        assertSame(firstRes, secondRes);
        verify(loaderMock, times(1)).load();
    }

    @Test
    void testOnlyInitializesOnceMultithreaded() throws InterruptedException {
        int numThreads = 3;
        int numTests = 10;

        CountDownLatch latch = new CountDownLatch(numTests);
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);

        Map<Long, Dtspb4583Dates> firstRet = Map.of(1L, new Dtspb4583Dates("first", "first"));
        Map<Long, Dtspb4583Dates> secondRet = Map.of(1L, new Dtspb4583Dates("second", "second"));

        when(loaderMock.load())
            .thenReturn(firstRet)
            .thenReturn(secondRet);

        List<Callable<Map<Long, Dtspb4583Dates>>> tests = new ArrayList<>();
        for (int i = 0; i < numTests; i++) {
            tests.add(() -> {
                Map<Long, Dtspb4583Dates> res = dataServiceImpl.getAll();
                latch.countDown();
                return res;
            });
        }

        executor.invokeAll(tests);

        verify(loaderMock, times(1)).load();
    }

    @Test
    void testGetReferencesStable() {
        final Dtspb4583Dates dummy = new Dtspb4583Dates("", "");
        final Map<Long, Dtspb4583Dates> testData = Map.of(
            Long.MAX_VALUE, dummy,
            1L, dummy,
            2L, dummy,
            3L, dummy,
            Long.MIN_VALUE, dummy
        );

        when(loaderMock.load())
            .thenReturn(testData);
        final String collected = dataService.getReferences();

        assertEquals("-9223372036854775808,1,2,3,9223372036854775807", collected);
    }
}
