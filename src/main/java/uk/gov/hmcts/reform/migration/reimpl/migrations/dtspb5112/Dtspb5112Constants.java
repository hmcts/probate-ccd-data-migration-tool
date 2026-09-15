package uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5112;

import java.util.Set;

final class Dtspb5112Constants {
    static final String JURISDICTION = "PROBATE";
    static final String CASE_TYPE = "Caveat";

    static final String PA_APP_CREATED = "PAAppCreated";
    static final String CAVEAT_RAISED = "CaveatRaised";
    static final String CAVEAT_MATCHING = "CaveatMatching";
    static final String AWAITING_CAVEAT_RESOLUTION = "AwaitingCaveatResolution";
    static final String WARNING_VALIDATION = "WarningValidation";
    static final String AWAITING_WARNING_RESPONSE = "AwaitingWarningResponse";
    static final String CAVEAT_CLOSED = "CaveatClosed";

    static final String MIGRATION_EVENT = "boHistoryCorrection";
    static final String ROLLBACK_EVENT = "boCorrection";

    static final Set<String> LIVE_STATES = Set.of(
        CAVEAT_RAISED,
        CAVEAT_MATCHING,
        AWAITING_CAVEAT_RESOLUTION,
        WARNING_VALIDATION,
        AWAITING_WARNING_RESPONSE
    );

    static final String EXPIRY_DATE = "expiryDate";
    static final String MIGRATION_CALLBACK_METADATA = "migrationCallbackMetadata";

    static final String S1_ID = "DTSPB-5112-pa-app-created";
    static final String S2_ID = "DTSPB-5112-set-expiry";
    static final String S3_ID = "DTSPB-5112-close-newly-expired";
    static final String S4_ID = "DTSPB-5112-close-existing-expired";

    static final String S1_ROLLBACK_ID = "DTSPB-5112-pa-app-created-rollback";
    static final String S2_ROLLBACK_ID = "DTSPB-5112-set-expiry-rollback";
    static final String S3_ROLLBACK_ID = "DTSPB-5112-close-newly-expired-rollback";
    static final String S4_ROLLBACK_ID = "DTSPB-5112-close-existing-expired-rollback";

    private Dtspb5112Constants() {
    }
}
