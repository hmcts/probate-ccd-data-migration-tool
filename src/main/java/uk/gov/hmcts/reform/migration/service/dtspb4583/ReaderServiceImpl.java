package uk.gov.hmcts.reform.migration.service.dtspb4583;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

@Service
class ReaderServiceImpl implements ReaderService {
    @Override
    public Reader getFileReader(String fileName) throws IOException {
        final ClassPathResource dtspbCsv = new ClassPathResource(fileName);
        final InputStream inputStream = dtspbCsv.getInputStream();
        return new InputStreamReader(inputStream);
    }
}
