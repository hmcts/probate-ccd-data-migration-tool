package uk.gov.hmcts.reform.migration.ccd;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestTemplate;

@Configuration
public class HttpMessageConverterConfiguration {
    @Bean
    public MappingJackson2HttpMessageConverter converter(ObjectMapper mapper) {
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        return new MappingJackson2HttpMessageConverter(mapper);
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
