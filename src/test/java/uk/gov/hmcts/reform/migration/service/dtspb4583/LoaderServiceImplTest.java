package uk.gov.hmcts.reform.migration.service.dtspb4583;

import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.migration.model.Dtspb4583Dates;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LoaderServiceImplTest {
    ReaderService readerMock;
    ParserService parserMock;

    LoaderService loaderService;

    @BeforeEach
    void setUp() {
        readerMock = mock(ReaderService.class);
        parserMock = mock(ParserService.class);

        LoaderServiceImpl impl = new LoaderServiceImpl(parserMock, readerMock);
        loaderService = impl;
    }

    @Test
    void testRealLoad() {
        final var parser = new ParserServiceImpl();
        final var reader = new ReaderServiceImpl();
        final LoaderService real = new LoaderServiceImpl(parser, reader);

        final Map<Long, Dtspb4583Dates> data = real.load();

        assertEquals(5, data.size());
    }

    @Test
    void testEmptyLoad() throws IOException {
        Reader returnReader = mock(Reader.class);
        List<CSVRecord> iterableMock = new ArrayList<>();

        when(readerMock.getFileReader(any())).thenReturn(returnReader);
        when(parserMock.parse(any())).thenReturn(iterableMock);

        final Map<Long, Dtspb4583Dates> data = loaderService.load();

        assertEquals(0, data.size());
    }

    @Test
    void testImmutableReturn() throws IOException {
        Reader returnReader = mock(Reader.class);
        List<CSVRecord> iterableMock = new ArrayList<>();

        when(readerMock.getFileReader(any())).thenReturn(returnReader);
        when(parserMock.parse(any())).thenReturn(iterableMock);

        final Map<Long, Dtspb4583Dates> data = loaderService.load();

        assertThrows(UnsupportedOperationException.class, () -> data.put(1L, null));
        assertThrows(UnsupportedOperationException.class, () -> data.remove(1L));
    }
}
