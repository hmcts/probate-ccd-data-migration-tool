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
import uk.gov.hmcts.reform.migration.repository.ElasticSearchRepository;
import uk.gov.hmcts.reform.migration.repository.IdamRepository;
import uk.gov.hmcts.reform.migration.service.DataMigrationService;

import java.text.MessageFormat;
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
public class CaseMigrationProcessor {
    static final String EVENT_ID = "boHistoryCorrection";
    static final String EVENT_SUMMARY = "Data correction - Correction to applicationSubmittedDate";
    static final String EVENT_DESCRIPTION = "Data correction - Correction to applicationSubmittedDate";
    public static final String LOG_STRING = "-----------------------------------------";

    @Autowired
    private CoreCaseDataService coreCaseDataService;

    @Autowired
    private DataMigrationService<Map<String, Object>> dataMigrationService;

    @Autowired
    private ElasticSearchRepository elasticSearchRepository;

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

    public void process(String caseType) throws InterruptedException {
        try {
            validateCaseType(caseType);
            log.info("Data migration of cases started for case type: {}", caseType);
            log.info("Data migration of cases started for defaultThreadLimit: {} defaultQuerySize : {}",
                defaultThreadLimit, defaultQuerySize);
            String userToken = idamRepository.generateUserToken();

            SearchResult searchResult = elasticSearchRepository.fetchFirstPage(userToken, caseType, defaultQuerySize);
            if (searchResult != null && searchResult.getTotal() > 0) {
                ExecutorService executorService = Executors.newFixedThreadPool(defaultThreadLimit);

                List<CaseDetails> searchResultCases = searchResult.getCases();
                searchResultCases
                    .stream()
                    .forEach(submitMigration(userToken, caseType, executorService));
                String searchAfterValue = searchResultCases.get(searchResultCases.size() - 1).getId().toString();

                log.info("Data migration of cases started for searchAfterValue : {}", searchAfterValue);

                boolean keepSearching;
                do {
                    SearchResult subsequentSearchResult = elasticSearchRepository.fetchNextPage(userToken,
                        caseType,
                        searchAfterValue,
                        defaultQuerySize);

                    log.info("Data migration of cases started for searchAfterValue : {}", searchAfterValue);

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
            showMigrationSummary();
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


    public void migrateCases(String caseType) {
        validateCaseType(caseType);
        log.info("Data migration of cases started for case type: {}", caseType);
        String userToken = idamRepository.generateUserToken();
        List<CaseDetails> listOfCaseDetails = elasticSearchRepository.findCaseByCaseType(userToken, caseType);
        listOfCaseDetails.stream()
            .limit(caseProcessLimit)
            .forEach(caseDetails -> updateCase(userToken, caseType, caseDetails));
        showMigrationSummary();
    }

    public void migrateCaseReferenceList(String caseType, String caseReferences) {
        String userToken = idamRepository.generateUserToken();
        List<String> caseReferenceList = Arrays.asList(caseReferences.split(",", -1));
        ;
        for (String caseReference : caseReferenceList) {
            log.info("Data migration of cases started for case reference: {}", caseReference);
            Optional<CaseDetails> caseDetailsOptional =
                elasticSearchRepository.findCaseByCaseId(userToken, caseReference);
            if (caseDetailsOptional.isPresent()) {
                CaseDetails caseDetails = caseDetailsOptional.get();
                updateCase(userToken, caseType, caseDetails);
            }
        }
        showMigrationSummary();

    }

    private void showMigrationSummary() {
        log.info(
            """
                PROBATE
                Data migration completed
                {}
                Total number of processed cases:
                {}
                {}
                Total successful cases:
                {}
                {}
                Total failed cases:
                {}
                {}
                """,
            LOG_STRING,
            migratedCases + getFailedCases().size(),
            LOG_STRING,
            migratedCases,
            LOG_STRING,
            getFailedCases().size(),
            LOG_STRING
        );

        if (migratedCases == 0) {
            log.info("Migrated cases: NONE ");
        } else {
            log.info("Migrated cases: {} ", migratedCases);
        }

        if (getFailedCases().isEmpty()) {
            log.info("Failed cases: NONE ");
        } else {
            log.info("Failed cases: {} ", getFailedCases());
        }
        log.info("Data migration of cases completed");
        failedCases = new ArrayList<>();
        migratedCases = 0;

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
        int count = 0;
        if (dataMigrationService.accepts().test(caseDetails)) {
            Long id = caseDetails.getId();
            log.info("Updating case {} {} {}", id, count++, caseDetails.getState());
            try {
                CaseDetails updateCaseDetails = coreCaseDataService.update(
                    authorisation,
                    EVENT_ID,
                    EVENT_SUMMARY,
                    EVENT_DESCRIPTION,
                    caseType,
                    caseDetails
                );
                if (updateCaseDetails != null) {
                    log.info("Case {} successfully updated", id);
                    migratedCases++;
                } else {
                    log.error("Case {} update failed", id);
                    failedCases.add(id);
                }

            } catch (Exception e) {
                log.error(
                    MessageFormat.format("Case {0} update failed due to : {1}", id, e.getMessage()),
                    e);
                failedCases.add(id);
            }
        } else {
            log.info("Case {} does not meet criteria for migration", caseDetails.getId());
        }
    }
}
