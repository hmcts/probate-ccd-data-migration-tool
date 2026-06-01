package uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5586;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Component
@Slf4j
public class Dtspb5586MigrationHandler implements MigrationHandler {
    private final CoreCaseDataApi coreCaseDataApi;
    private final ElasticSearchHandler elasticSearchHandler;

    private final ReimplConfig reimplConfig;
    private final Dtspb5586ElasticQueries elasticQueries;

    static final String GRANT_OF_REPRESENTATION = "GrantOfRepresentation";
    static final String JURISDICTION = "PROBATE";

    static final String MIGRATION_SUMMARY = "DTSPB-5586 - Migrating Handoff Reasons";
    static String MIGRATION_DESCRIPTION = "";

    public Dtspb5586MigrationHandler(
            final CoreCaseDataApi coreCaseDataApi,
            final ElasticSearchHandler elasticSearchHandler,
            final ReimplConfig reimplConfig,
            final Dtspb5586ElasticQueries elasticQueries) {
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

        final Set<CaseSummary> gorCandidates = elasticSearchHandler.searchCases(
                "DTSPB-5586",
                userToken,
                s2sToken,
                CaseType.GRANT_OF_REPRESENTATION,
                fR -> elasticQueries.getGorMigrationQuery(reimplConfig.getQuerySize(), fR));
        candidateCases.addAll(gorCandidates);


        return Set.copyOf(candidateCases);
    }

    @Override
    public MigrationEvent startEventForCase(
            final CaseSummary caseSummary,
            final UserToken userToken,
            final S2sToken s2sToken) {

        final MigrationEventDetails eventDetails = switch (caseSummary.type()) {
            case GRANT_OF_REPRESENTATION -> new MigrationEventDetails(
                    GRANT_OF_REPRESENTATION,
                    "boHistoryCorrection");
            case CAVEAT -> throw new Dtspb5586MigrationException("Unexpected CAVEAT");
        };

        final UserDetails userDetails = userToken.userDetails();

        log.info("DTSPB-5586 start event for {} case {}",
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
            log.error("DTSPB-5586: No case details present in startEventResponse for {} case {}",
                    caseSummary.type(),
                    caseSummary.reference());
            throw new Dtspb5586MigrationException(
                    "No case details present in startEventResponse for " + caseSummary.reference());
        }

        final Map<String, Object> caseData = caseDetails.getData();
        if (caseData == null) {
            log.error("DTSPB-5586: No case data present in startEventResponse for {} case {}",
                    caseSummary.type(),
                    caseSummary.reference());
            throw new Dtspb5586MigrationException(
                    "No case data present in startEventResponse for " + caseSummary.reference());
        }

        Object boHandofflistObj = caseData.get("boHandoffReasonList");
        if (!(boHandofflistObj instanceof List)) {
            log.info("DTSPB-5586: handoff reason list is not a list");
            return false;
        }
        @SuppressWarnings("unchecked")
        List<Object> boHandoffReasonList = (List<Object>) boHandofflistObj;

        for (Object boHandoffReasonObj : boHandoffReasonList) {
            if (!(boHandoffReasonObj instanceof Map)) {
                log.info("DTSPB-5586: handoff reason list entry is not a map");
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<Object, Object> boHandoffReasonMap = (Map<Object, Object>) boHandoffReasonObj;
            Object boHandoffReasonMapValue = boHandoffReasonMap.get("value");
            if (!(boHandoffReasonMapValue instanceof Map)) {
                log.info("DTSPB-5586: handoff reason mapped value is not a map");
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<Object, Object> boHandoffReasonMapped = (Map<Object, Object>) boHandoffReasonMapValue;
            Object boHandoffReasonMappedValue = boHandoffReasonMapped.get("caseHandoffReason");
            if (boHandoffReasonMappedValue == null) {
                log.info("DTSPB-5586: handoff reason mapped value is null");
            }
            if (List.of("AdmonWill", "ExtendedIntestacy").contains(boHandoffReasonMappedValue)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean migrate(
            final MigrationEvent migrationEvent) {
        final CaseSummary caseSummary = migrationEvent.caseSummary();
        final StartEventResponse startEventResponse = migrationEvent.startEventResponse();

        final CaseDetails caseDetails = startEventResponse.getCaseDetails();

        final Map<String, Object> migratedData = caseDetails.getData();

        Object boHandofflistObj = migratedData.get("boHandoffReasonList");
        if (!(boHandofflistObj instanceof List)) {
            log.info("DTSPB-5586: handoff reason list is not a list");
            return false;
        }

        @SuppressWarnings("unchecked")
        List<Object> boHandoffReasonList = (List<Object>) boHandofflistObj;

        ObjectMapper objectMapper = new ObjectMapper();
        try {
            MIGRATION_DESCRIPTION = "DTSPB-5586?" + objectMapper.writeValueAsString(boHandoffReasonList);
        } catch (JsonProcessingException e) {
            throw new Dtspb5586MigrationException("Could not write into migrationData");
        }

        boHandoffReasonList.removeIf(boHandoffReasonObj -> {
            if (!(boHandoffReasonObj instanceof Map)) {
                log.info("DTSPB-5586: handoff reason list entry is not a map");
                return false;
            }

            @SuppressWarnings("unchecked")
            Map<Object, Object> boHandoffReasonMap = (Map<Object, Object>) boHandoffReasonObj;

            Object boHandoffReasonMapValue = boHandoffReasonMap.get("value");
            if (!(boHandoffReasonMapValue instanceof Map)) {
                log.info("DTSPB-5586: handoff reason mapped value is not a map");
                return false;
            }

            @SuppressWarnings("unchecked")
            Map<Object, Object> boHandoffReasonMapped =
                (Map<Object, Object>) boHandoffReasonMapValue;

            Object boHandoffReasonMappedValue =
                boHandoffReasonMapped.get("caseHandoffReason");

            if (boHandoffReasonMappedValue == null) {
                log.info("DTSPB-5586: handoff reason mapped value is null");
                return false;
            }
            return List.of("AdmonWill", "ExtendedIntestacy").contains(boHandoffReasonMappedValue);
        });


        if (boHandoffReasonList.isEmpty()) {
            migratedData.put("caseHandedOffToLegacySite", "No");
        } else {
            migratedData.put("caseHandedOffToLegacySite", "Yes");
        }

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
            log.info("DTSPB-5586: DRY RUN - returning without submission for {} case {}",
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
            log.error("DTSPB-5586: event submission returned null for {} case {}",
                    caseSummary.type(),
                    caseSummary.reference());
            return false;
        }
        log.info("DTSPB-5586: event submission complete for {} case {}",
                caseSummary.type(),
                caseSummary.reference());
        return true;
    }

    private record MigrationEventDetails(String caseType, String eventId) {}

    class Dtspb5586MigrationException extends RuntimeException {
        public Dtspb5586MigrationException(final String message) {
            super(message);
        }
    }
}
