package uk.gov.hmcts.reform.migration.service.dtspb4583;

import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.io.Reader;

interface ParserService {
    Iterable<CSVRecord> parse(Reader reader) throws IOException;
}
