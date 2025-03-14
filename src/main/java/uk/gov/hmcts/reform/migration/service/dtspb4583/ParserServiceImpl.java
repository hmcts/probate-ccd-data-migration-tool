package uk.gov.hmcts.reform.migration.service.dtspb4583;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.Reader;

@Service
class ParserServiceImpl implements ParserService {
    @Override
    public Iterable<CSVRecord> parse(Reader reader) throws IOException {
        return CSVFormat.POSTGRESQL_CSV.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .get()
            .parse(reader);
    }
}
