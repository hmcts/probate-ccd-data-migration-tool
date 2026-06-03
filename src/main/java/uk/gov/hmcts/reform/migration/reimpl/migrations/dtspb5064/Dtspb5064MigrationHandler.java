package uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5064;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.ccd.client.CoreCaseDataApi;
import uk.gov.hmcts.reform.ccd.client.model.CaseDataContent;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.ccd.client.model.Event;
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;
import uk.gov.hmcts.reform.idam.client.models.UserDetails;
import uk.gov.hmcts.reform.migration.reimpl.config.ReimplConfig;
import uk.gov.hmcts.reform.migration.reimpl.dto.CaseSummary;
import uk.gov.hmcts.reform.migration.reimpl.dto.CaseType;
import uk.gov.hmcts.reform.migration.reimpl.dto.MigrationEvent;
import uk.gov.hmcts.reform.migration.reimpl.dto.S2sToken;
import uk.gov.hmcts.reform.migration.reimpl.dto.UserToken;
import uk.gov.hmcts.reform.migration.reimpl.service.ElasticSearchHandler;
import uk.gov.hmcts.reform.migration.reimpl.service.MigrationHandler;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Component
@Slf4j
public class Dtspb5064MigrationHandler implements MigrationHandler {
    private final CoreCaseDataApi coreCaseDataApi;
    private final ElasticSearchHandler elasticSearchHandler;

    private final ReimplConfig reimplConfig;
    private final Dtspb5064ElasticQueries elasticQueries;

    static final String CAVEAT = "Caveat";
    static final String JURISDICTION = "PROBATE";
    static final String STATE = "state";
    static final String CAVEAT_NOT_MATCHED = "CaveatNotMatched";
    static final String CAVEAT_MATCHING = "CaveatMatching";

    static final String MIGRATION_SUMMARY =
        "DTSPB-5064 - Migrate cases in 'Caveat Not Matched' state to 'Caveat Resolution'";
    static final String MIGRATION_DESCRIPTION =
        "Move cases in 'Caveat Not Matched' state to 'Caveat Resolution'";

    public Dtspb5064MigrationHandler(
        final CoreCaseDataApi coreCaseDataApi,
        final ElasticSearchHandler elasticSearchHandler,
        final ReimplConfig reimplConfig,
        final Dtspb5064ElasticQueries elasticQueries) {
        this.coreCaseDataApi = Objects.requireNonNull(coreCaseDataApi);
        this.elasticSearchHandler = Objects.requireNonNull(elasticSearchHandler);

        this.reimplConfig = Objects.requireNonNull(reimplConfig);
        this.elasticQueries = Objects.requireNonNull(elasticQueries);
    }

    @Override
    public Set<CaseSummary> getCandidateCases(
        final UserToken userToken,
        final S2sToken s2sToken) {
        final Set<CaseSummary> candidateCases = new HashSet<>();

        final Set<CaseSummary> caveatCandidates = elasticSearchHandler.searchCases(
            "DTSPB-5064",
            userToken,
            s2sToken,
            CaseType.CAVEAT,
            fR -> elasticQueries.getCaveatMigrationQuery(reimplConfig.getQuerySize(), fR));
        candidateCases.addAll(caveatCandidates);

        return Set.copyOf(candidateCases);
    }

    @Override
    public MigrationEvent startEventForCase(
        final CaseSummary caseSummary,
        final UserToken userToken,
        final S2sToken s2sToken) {

        final MigrationEventDetails eventDetails = new MigrationEventDetails(CAVEAT, "boHistoryCorrection");

        final UserDetails userDetails = userToken.userDetails();

        log.info("DTSPB-5064 start event for {} case {}",
            eventDetails.caseType(),
            caseSummary.reference());
        final StartEventResponse startEventResponse = coreCaseDataApi.startEventForCaseWorker(
            userToken.getBearerToken(),
            s2sToken.s2sToken(),
            userDetails.getId(),
            JURISDICTION,
            eventDetails.caseType(),
            caseSummary.reference().toString(),
            eventDetails.eventId());

        return new MigrationEvent(
            caseSummary,
            startEventResponse,
            userToken,
            s2sToken);
    }

    @Override
    public boolean shouldMigrateCase(
        final MigrationEvent migrationEvent) {
        final CaseSummary caseSummary = migrationEvent.caseSummary();
        final CaseDetails caseDetails = migrationEvent.startEventResponse().getCaseDetails();
        if (caseDetails == null) {
            log.error("DTSPB-5064: No case details present in startEventResponse for {} case {}",
                caseSummary.type(),
                caseSummary.reference());
            throw new Dtspb5064MigrationException(
                "No case details present in startEventResponse for " + caseSummary.reference());
        }

        final Map<String, Object> caseData = caseDetails.getData();
        if (caseData == null) {
            log.error("DTSPB-5064: No case data present in startEventResponse for {} case {}",
                caseSummary.type(),
                caseSummary.reference());
            throw new Dtspb5064MigrationException(
                "No case data present in startEventResponse for " + caseSummary.reference());
        }

        final boolean hasCaveatNotMatched = isInCaveatNotMatchedState(caseData);
        if (!hasCaveatNotMatched) {
            log.info("DTSPB-5064: {} case {} is in a different state than CaveatNotMatched so no migration needed",
                caseSummary.type(),
                caseSummary.reference());
        }
        return hasCaveatNotMatched;
    }

    @Override
    public boolean migrate(
        final MigrationEvent migrationEvent) {
        final CaseSummary caseSummary = migrationEvent.caseSummary();
        final StartEventResponse startEventResponse = migrationEvent.startEventResponse();

        final CaseDetails caseDetails = startEventResponse.getCaseDetails();

        final Map<String, Object> migratedData = caseDetails.getData();

        migratedData.put(STATE, CAVEAT_MATCHING);

        final Event event = Event.builder()
            .id(startEventResponse.getEventId())
            .summary(MIGRATION_SUMMARY)
            .description(MIGRATION_DESCRIPTION)
            .build();

        final CaseDataContent caseDataContent = CaseDataContent.builder()
            .eventToken(startEventResponse.getToken())
            .event(event)
            .data(migratedData)
            .build();

        if (reimplConfig.isDryRun()) {
            log.info("DTSPB-5064: DRY RUN - returning without submission for {} case {}",
                caseSummary.type(),
                caseSummary.reference());
            return true;
        }
        // We use the authentication provided in the MigrationEvent to ensure
        // that we don't start events with one set of authentication tokens
        // and then submit them with another.
        final CaseDetails result = coreCaseDataApi.submitEventForCaseWorker(
            migrationEvent.userToken().getBearerToken(),
            migrationEvent.s2sToken().s2sToken(),
            migrationEvent.userToken().userDetails().getId(),
            caseDetails.getJurisdiction(),
            caseDetails.getCaseTypeId(),
            caseDetails.getId().toString(),
            true,
            caseDataContent);

        if (result == null) {
            log.error("DTSPB-5064: event submission returned null for {} case {}",
                caseSummary.type(),
                caseSummary.reference());
            return false;
        }
        log.info("DTSPB-5064: event submission complete for {} case {}",
            caseSummary.type(),
            caseSummary.reference());
        return true;
    }

    private boolean isInCaveatNotMatchedState(Map<String, Object> caseData) {
        return caseData.containsKey(STATE)
            && caseData.get(STATE).equals(CAVEAT_NOT_MATCHED);
    }

    private record MigrationEventDetails(String caseType, String eventId) {}

    class Dtspb5064MigrationException extends RuntimeException {
        public Dtspb5064MigrationException(final String message) {
            super(message);
        }
    }
}
