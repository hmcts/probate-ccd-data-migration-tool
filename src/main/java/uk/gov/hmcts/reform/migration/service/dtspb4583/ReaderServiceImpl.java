package uk.gov.hmcts.reform.migration.service.dtspb4583;

import org.springframework.stereotype.Service;
import org.springframework.util.ResourceUtils;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;

@Service
class ReaderServiceImpl implements ReaderService {
    @Override
    public Reader getFileReader(String fileName) throws IOException {
        final File dtspb4853File =
            ResourceUtils.getFile(ResourceUtils.CLASSPATH_URL_PREFIX + "DTSPB-4583.csv");
        return new FileReader(dtspb4853File);
    }
}
