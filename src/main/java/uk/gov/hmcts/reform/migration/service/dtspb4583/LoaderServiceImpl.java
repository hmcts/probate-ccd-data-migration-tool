package uk.gov.hmcts.reform.migration.service.dtspb4583;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.migration.model.Dtspb4583Dates;

import java.io.IOException;
import java.io.Reader;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
class LoaderServiceImpl implements LoaderService {
    private final ParserService parserService;
    private final ReaderService readerService;


    public LoaderServiceImpl(
        final ParserService parserService,
        final ReaderService readerService
    ) {
        this.parserService = parserService;
        this.readerService = readerService;
    }

    @Override
    public Map<Long, Dtspb4583Dates> load() {
        log.info("Load DTSPB-4583 data");
        final Iterable<CSVRecord> records;
        try (final Reader reader = readerService.getFileReader("test.csv")) {
            log.info("Parse DTSPB-4583 data");
            records = parserService.parse(reader);

            log.info("Parsed DTSPB-4583 data");
            final Map<Long, Dtspb4583Dates> modifiableMap = new HashMap<>();
            for (final CSVRecord rec : records) {
                final String refStr = rec.get("reference");
                final Long ref = Long.parseLong(refStr);
                final String incorrect = rec.get("wrong_sub_date");
                final String correct = rec.get("correct_sub_date");

                final Dtspb4583Dates dates = new Dtspb4583Dates(incorrect, correct);

                modifiableMap.put(ref, dates);
            }
            log.info("Loaded {} entries", modifiableMap.size());
            return Collections.unmodifiableMap(modifiableMap);
        } catch (IOException e) {
            log.error(
                MessageFormat.format(
                    "Unable to initialize data for DTSPB-4583: {0}", e.getMessage()),
                e);
            throw new IllegalStateException("Unable to initialize data for DTSPB-4583", e);
        }
    }
}
