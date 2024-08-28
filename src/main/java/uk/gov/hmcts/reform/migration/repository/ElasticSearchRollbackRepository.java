package uk.gov.hmcts.reform.migration.repository;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;
import uk.gov.hmcts.reform.ccd.client.CoreCaseDataApi;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.ccd.client.model.SearchResult;
import uk.gov.hmcts.reform.migration.query.ElasticSearchRollbackQuery;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
@Slf4j
public class ElasticSearchRollbackRepository {

    private final CoreCaseDataApi coreCaseDataApi;

    private final AuthTokenGenerator authTokenGenerator;
    private final String startDatetime;

    private final String endDatetime;

    private final int querySize;

    private final int caseProcessLimit;

    @Autowired
    public ElasticSearchRollbackRepository(CoreCaseDataApi coreCaseDataApi,
                                   AuthTokenGenerator authTokenGenerator,
                                   @Value("${migration.rollback.start.datetime}") String startDatetime,
                                   @Value("${migration.rollback.end.datetime}") String  endDatetime,
                                   @Value("${case-migration.elasticsearch.querySize}") int querySize,
                                   @Value("${case-migration.processing.limit}") int caseProcessLimit) {
        this.coreCaseDataApi = coreCaseDataApi;
        this.authTokenGenerator = authTokenGenerator;
        this.startDatetime = startDatetime;
        this.endDatetime = endDatetime;
        this.querySize = querySize;
        this.caseProcessLimit = caseProcessLimit;
    }

    public Optional<CaseDetails> findCaseByCaseId(String authorisation, String caseId) {
        try {
            return Optional.ofNullable(coreCaseDataApi.getCase(authorisation, authTokenGenerator.generate(), caseId));
        } catch (Exception ex) {
            log.error("Case {} not found due to: {}", caseId, ex.getMessage());
        }
        return Optional.empty();
    }

    public List<CaseDetails> findCaseByCaseType(String userToken, String caseType) {
        ElasticSearchRollbackQuery elasticSearchQuery = ElasticSearchRollbackQuery.builder()
            .initialSearch(true)
            .startDateTime(startDatetime)
            .endDateTime(endDatetime)
            .size(querySize)
            .build();

        log.info("Processing the Rollback Case Migration rollback search for case type {}.", caseType);
        String authToken = authTokenGenerator.generate();
        SearchResult searchResult = coreCaseDataApi.searchCases(userToken,
                                                                authToken,
                                                                caseType, elasticSearchQuery.getQuery()
        );

        List<CaseDetails> caseDetails = new ArrayList<>();

        if (searchResult != null && searchResult.getTotal() > 0) {
            List<CaseDetails> searchResultCases = searchResult.getCases();
            caseDetails.addAll(searchResultCases);
            String searchAfterValue = searchResultCases.get(searchResultCases.size() - 1).getId().toString();

            boolean keepSearching;
            do {
                ElasticSearchRollbackQuery subsequentElasticSearchQuery = ElasticSearchRollbackQuery.builder()
                    .initialSearch(false)
                    .size(querySize)
                    .startDateTime(startDatetime)
                    .endDateTime(endDatetime)
                    .searchAfterValue(searchAfterValue)
                    .build();

                SearchResult subsequentSearchResult =
                    coreCaseDataApi.searchCases(userToken,
                                                authToken,
                                                caseType, subsequentElasticSearchQuery.getQuery()
                    );

                keepSearching = false;
                if (subsequentSearchResult != null) {
                    caseDetails.addAll(subsequentSearchResult.getCases());
                    keepSearching = subsequentSearchResult.getCases().size() > 0;
                    if (keepSearching) {
                        searchAfterValue = caseDetails.get(caseDetails.size() - 1).getId().toString();
                    }
                }
            } while (keepSearching);
        }
        log.info("The Rollback Rollback Case Migration has processed caseDetails {}.", caseDetails.size());
        return caseDetails;
    }


    public SearchResult fetchFirstPage(String userToken, String caseType, int querySize) {
        ElasticSearchRollbackQuery elasticSearchQuery = ElasticSearchRollbackQuery.builder()
            .initialSearch(true)
            .size(querySize)
            .startDateTime(startDatetime)
            .endDateTime(endDatetime)
            .build();
        log.info("Fetching the Rollback case details from elastic search for case type {}.", caseType);
        String authToken = authTokenGenerator.generate();
        return coreCaseDataApi.searchCases(userToken,
                                           authToken,
                                           caseType, elasticSearchQuery.getQuery()
        );
    }

    public SearchResult fetchNextPage(String userToken,
                                      String caseType,
                                      String searchAfterValue,
                                      int querySize) {

        String authToken = authTokenGenerator.generate();

        ElasticSearchRollbackQuery subsequentElasticSearchQuery = ElasticSearchRollbackQuery.builder()
            .initialSearch(false)
            .startDateTime(startDatetime)
            .endDateTime(endDatetime)
            .size(querySize)
            .searchAfterValue(searchAfterValue)
            .build();

        SearchResult subsequentSearchResult =
            coreCaseDataApi.searchCases(userToken,
                                        authToken,
                                        caseType, subsequentElasticSearchQuery.getQuery()
            );
        return subsequentSearchResult;
    }
}
