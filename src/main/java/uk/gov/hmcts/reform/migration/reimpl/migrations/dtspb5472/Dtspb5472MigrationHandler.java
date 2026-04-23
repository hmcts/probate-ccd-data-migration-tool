package uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5472;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.ccd.client.CoreCaseDataApi;
import uk.gov.hmcts.reform.ccd.client.model.CaseDataContent;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.ccd.client.model.Event;
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;
import uk.gov.hmcts.reform.idam.client.models.UserDetails;
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
public class Dtspb5472MigrationHandler implements MigrationHandler {
    private final CoreCaseDataApi coreCaseDataApi;
    private final ElasticSearchHandler elasticSearchHandler;

    private final Dtspb5472Config config;
    private final Dtspb5472ElasticQueries elasticQueries;

    static final String GRANT_OF_REPRESENTATION = "GrantOfRepresentation";
    static final String JURISDICTION = "PROBATE";
    static final String PA_RELATIONSHIP_TO_DECEASED = "primaryApplicantRelationshipToDeceased";
    static final String SOL_RELATIONSHIP_TO_DECEASED = "solsApplicantRelationshipToDeceased";
    static final String PA_ADOPTED_CHILD = "adoptedChild";
    static final String PA_CHILD = "child";
    static final String SOL_ADOPTED_CHILD = "ChildAdopted";
    static final String SOL_CHILD = "Child";
    static final String PA_ADOPTED_IN = "primaryApplicantAdoptedIn";
    static final String YES = "Yes";


    static final String MIGRATION_SUMMARY = "DTSPB-5472 - Migrate applicant's relationship to deceased";
    static final String MIGRATION_DESCRIPTION = "Remove Adopted Child option";

    public Dtspb5472MigrationHandler(
            final CoreCaseDataApi coreCaseDataApi,
            final ElasticSearchHandler elasticSearchHandler,
            final Dtspb5472Config config,
            final Dtspb5472ElasticQueries elasticQueries) {
        this.coreCaseDataApi = Objects.requireNonNull(coreCaseDataApi);
        this.elasticSearchHandler = Objects.requireNonNull(elasticSearchHandler);

        this.config = Objects.requireNonNull(config);
        this.elasticQueries = Objects.requireNonNull(elasticQueries);
    }

    @Override
    public Set<CaseSummary> getCandidateCases(
            final UserToken userToken,
            final S2sToken s2sToken) {

        final Set<CaseSummary> gorCandidates = elasticSearchHandler.searchCases(
                "DTSPB-5472",
                userToken,
                s2sToken,
                CaseType.GRANT_OF_REPRESENTATION,
                fR -> elasticQueries.getGorMigrationQuery(config.getQuerySize(), fR));
        final Set<CaseSummary> candidateCases = new HashSet<>(gorCandidates);

        return Set.copyOf(candidateCases);
    }

    @Override
    public MigrationEvent startEventForCase(
            final CaseSummary caseSummary,
            final UserToken userToken,
            final S2sToken s2sToken) {

        final MigrationEventDetails eventDetails =  new MigrationEventDetails(
            GRANT_OF_REPRESENTATION,
            "boHistoryCorrection");

        final UserDetails userDetails = userToken.userDetails();

        log.info("DTSPB-5472 start event for {} case {}",
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
            log.error("DTSPB-5472: No case details present in startEventResponse for {} case {}",
                    caseSummary.type(),
                    caseSummary.reference());
            throw new Dtspb5472MigrationException(
                    "No case details present in startEventResponse for " + caseSummary.reference());
        }

        final Map<String, Object> caseData = caseDetails.getData();
        if (caseData == null) {
            log.error("DTSPB-5472: No case data present in startEventResponse for {} case {}",
                    caseSummary.type(),
                    caseSummary.reference());
            throw new Dtspb5472MigrationException(
                    "No case data present in startEventResponse for " + caseSummary.reference());
        }

        final boolean hasAdoptedChild = hasPaAdoptedChild(caseData) || hasSolAdoptedChild(caseData);
        if (!hasAdoptedChild) {
            log.info("DTSPB-5472: case {} don't have adopted child option so no migration needed",
                    caseSummary.reference());
        }
        return hasAdoptedChild;
    }

    @Override
    public boolean migrate(
            final MigrationEvent migrationEvent) {
        final CaseSummary caseSummary = migrationEvent.caseSummary();
        final StartEventResponse startEventResponse = migrationEvent.startEventResponse();

        final CaseDetails caseDetails = startEventResponse.getCaseDetails();

        final Map<String, Object> migratedData = caseDetails.getData();

        if (hasPaAdoptedChild(migratedData)) {
            migratedData.put(PA_RELATIONSHIP_TO_DECEASED, PA_CHILD);
        }
        if (hasSolAdoptedChild(migratedData)) {
            migratedData.put(SOL_RELATIONSHIP_TO_DECEASED, SOL_CHILD);
        }
        migratedData.put(PA_ADOPTED_IN, YES);

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

        if (config.isDryRun()) {
            log.info("DTSPB-5472: DRY RUN - returning without submission for {} case {}",
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
            log.error("DTSPB-5472: event submission returned null for {} case {}",
                    caseSummary.type(),
                    caseSummary.reference());
            return false;
        }
        log.info("DTSPB-5472: event submission complete for {} case {}",
                caseSummary.type(),
                caseSummary.reference());
        return true;
    }

    private record MigrationEventDetails(String caseType, String eventId) {}

    class Dtspb5472MigrationException extends RuntimeException {
        public Dtspb5472MigrationException(final String message) {
            super(message);
        }
    }

    private boolean hasPaAdoptedChild(Map<String, Object> caseData) {
        return caseData.containsKey(PA_RELATIONSHIP_TO_DECEASED)
            && caseData.get(PA_RELATIONSHIP_TO_DECEASED).equals(PA_ADOPTED_CHILD);
    }

    private boolean hasSolAdoptedChild(Map<String, Object> caseData) {
        return caseData.containsKey(SOL_RELATIONSHIP_TO_DECEASED)
            && caseData.get(SOL_RELATIONSHIP_TO_DECEASED).equals(SOL_ADOPTED_CHILD);
    }
}
