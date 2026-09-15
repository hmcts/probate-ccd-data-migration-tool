package uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5112;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class Dtspb5112MigrationSupportTest {

    @Test
    void shouldAddMigrationIdAsCallbackMetadata() {
        Dtspb5112MigrationSupport support = new Dtspb5112MigrationSupport(mock(), mock());
        Map<String, Object> data = new HashMap<>();

        support.addMigrationCallbackMetadata(data, Dtspb5112Constants.S3_ID);

        JSONObject metadata = new JSONObject(
            data.get(Dtspb5112Constants.MIGRATION_CALLBACK_METADATA).toString()
        );
        assertThat(metadata.getString("migrationId")).isEqualTo(Dtspb5112Constants.S3_ID);
    }
}
