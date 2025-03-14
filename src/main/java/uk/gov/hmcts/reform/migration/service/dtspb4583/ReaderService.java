package uk.gov.hmcts.reform.migration.service.dtspb4583;

import java.io.IOException;
import java.io.Reader;

interface ReaderService {
    Reader getFileReader(String fileName) throws IOException;
}
