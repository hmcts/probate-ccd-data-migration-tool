package uk.gov.hmcts.reform.migration;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.ccd.client.model.SearchResult;
import uk.gov.hmcts.reform.domain.exception.CaseMigrationException;
import uk.gov.hmcts.reform.domain.exception.MigrationLimitReachedException;
import uk.gov.hmcts.reform.migration.ccd.CoreCaseDataService;
import uk.gov.hmcts.reform.migration.repository.ElasticSearchRollbackRepository;
import uk.gov.hmcts.reform.migration.repository.IdamRepository;
import uk.gov.hmcts.reform.migration.service.DataMigrationService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Slf4j
@Component
public class CaseMigrationRollbackProcessor {
    private static final String EVENT_ID = "boCorrection";
    private static final String EVENT_SUMMARY = "Data migration - Rollback channelChoice";
    private static final String EVENT_DESCRIPTION = "Data migration - Rollback channelChoice";
    public static final String LOG_STRING = "-----------------------------------------";

    @Autowired
    private CoreCaseDataService coreCaseDataService;

    @Autowired
    private DataMigrationService<Map<String, Object>> dataMigrationService;

    @Autowired
    private ElasticSearchRollbackRepository elasticSearchRollbackRepository;

    @Autowired
    private IdamRepository idamRepository;

    private int migratedCases;

    @Getter
    private List<Long> failedCases = new ArrayList<>();

    @Value("${case-migration.processing.limit}")
    private int caseProcessLimit;

    @Value("${default.thread.limit}")
    private int defaultThreadLimit;

    @Value("${default.query.size}")
    private int defaultQuerySize;

    @Value("${migration.rollback.start.datetime}")
    private String migrationrollbackStartDatetime;

    @Value("${migration.rollback.end.datetime}")
    private String migrationrollbackEndDatetime;

    public void processRollback(String caseType) throws InterruptedException {
        try {
            validateCaseType(caseType);
            log.info("Data migration rollback of cases started for case type: {}", caseType);
            log.info("Data migration rollback of cases started for defaultThreadLimit: {} defaultQuerySize : {}",
                     defaultThreadLimit, defaultQuerySize);
            String userToken =  idamRepository.generateUserToken();

            SearchResult searchResult = elasticSearchRollbackRepository.fetchFirstPage(userToken, caseType,
                defaultQuerySize);
            if (searchResult != null && searchResult.getTotal() > 0) {
                ExecutorService executorService = Executors.newFixedThreadPool(defaultThreadLimit);

                List<CaseDetails> searchResultCases = searchResult.getCases();
                searchResultCases
                    .stream()
                    .forEach(submitMigration(userToken, caseType, executorService));
                String searchAfterValue = searchResultCases.get(searchResultCases.size() - 1).getId().toString();

                log.info("Data migration rollback of cases started for searchAfterValue : {}",searchAfterValue);

                boolean keepSearching;
                do {
                    SearchResult subsequentSearchResult = elasticSearchRollbackRepository.fetchNextPage(userToken,
                                                                        caseType,
                                                                        searchAfterValue,
                                                                        defaultQuerySize);

                    log.info("Data migration of cases started for searchAfterValue : {}",searchAfterValue);

                    keepSearching = false;
                    if (subsequentSearchResult != null) {
                        List<CaseDetails> subsequentSearchResultCases = subsequentSearchResult.getCases();
                        subsequentSearchResultCases
                            .stream()
                            .forEach(submitMigration(userToken, caseType, executorService));
                        keepSearching = subsequentSearchResultCases.size() > 0;
                        if (keepSearching) {
                            searchAfterValue = subsequentSearchResultCases
                                .get(subsequentSearchResultCases.size() - 1).getId().toString();
                        }
                    }
                } while (keepSearching);

                executorService.shutdown();
                executorService.awaitTermination(Integer.MAX_VALUE, TimeUnit.DAYS);
            }
            showRollbackSummary();
        } catch (MigrationLimitReachedException ex) {
            throw ex;
        }
    }

    private Consumer<CaseDetails> submitMigration(String userToken, String caseType, ExecutorService executorService) {
        return caseDetails -> {
            log.info("Submitting task for migration of case  {}.", caseDetails.getId());
            executorService.submit(() -> updateCase(userToken, caseType, caseDetails));
        };
    }


    public void rollbackCases(String caseType) {
        validateCaseType(caseType);
        log.info("Data migration of cases started for case type: {}", caseType);
        String userToken =  idamRepository.generateUserToken();
        List<CaseDetails> listOfCaseDetails = elasticSearchRollbackRepository.findCaseByCaseType(userToken, caseType);
        listOfCaseDetails.stream()
            .limit(caseProcessLimit)
            .forEach(caseDetails -> updateCase(userToken, caseType, caseDetails));
        showRollbackSummary();
    }

    public void rollbackCaseReferenceList(String caseType, String caseReferences) {
        String userToken =  idamRepository.generateUserToken();
        List<String> caseReferenceList =  Arrays.asList(caseReferences.split(",", -1));;
        for (String caseReference: caseReferenceList) {
            log.info("Data migration of cases started for case reference: {}", caseReference);
            Optional<CaseDetails> caseDetailsOptional =
                elasticSearchRollbackRepository.findCaseByCaseId(userToken, caseReference);
            if (caseDetailsOptional.isPresent()) {
                CaseDetails caseDetails =  caseDetailsOptional.get();
                updateCase(userToken, caseType, caseDetails);
            }
        }
        showRollbackSummary();

    }

    private void showRollbackSummary() {
        log.info(
            """
                PROBATE
                Data migration rollback completed
                {}
                Total number of rollback processed cases:
                {}
                Total number of rollback migrations performed:
                {}
                {}
                """,
            LOG_STRING,
            migratedCases + getFailedCases().size(),
            migratedCases,
            LOG_STRING
        );

        if (migratedCases < 0) {
            log.info("Rollback cases: NONE ");
        } else {
            log.info("Rollback cases: {} ", migratedCases);
        }

        if (getFailedCases().isEmpty()) {
            log.info("Failed Rollback cases: NONE ");
        } else {
            log.info("Failed Rollback cases: {} ", getFailedCases());
        }
        log.info("Data migration Rollback of cases completed");
    }

    private void validateCaseType(String caseType) {
        if (!StringUtils.hasText(caseType)) {
            throw new CaseMigrationException("Provide case type for the migration");
        }

        if (caseType.split(",").length > 1) {
            throw new CaseMigrationException("Only One case type at a time is allowed for the migration");
        }
    }


    private void updateCase(String authorisation, String caseType, CaseDetails caseDetails) {
        if (dataMigrationService.accepts().test(caseDetails)) {
            Long id = caseDetails.getId();
            log.info("Rollback case {}", id);
            try {
                CaseDetails updateCaseDetails = coreCaseDataService.rollback(
                    authorisation,
                    EVENT_ID,
                    EVENT_SUMMARY,
                    EVENT_DESCRIPTION,
                    caseType,
                    caseDetails
                );

                if (updateCaseDetails != null) {
                    log.info("Case {} successfully rollback", id);
                    migratedCases++;
                }
            } catch (Exception e) {
                log.error("Case {} rollback failed due to : {}", id, e.getMessage());
                failedCases.add(id);
            }
        } else {
            log.info("Case {} does not meet criteria for rollback", caseDetails.getId());
        }
    }
}
