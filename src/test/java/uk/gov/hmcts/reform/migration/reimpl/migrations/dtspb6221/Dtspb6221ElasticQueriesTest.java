package uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb6221;

import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.migration.reimpl.service.ElasticSearchQueryUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class Dtspb6221ElasticQueriesTest {

    private ElasticSearchQueryUtils elasticSearchQueryUtils;
    private Dtspb6221ElasticQueries elasticQueries;

    @BeforeEach
    void setUp() {
        elasticSearchQueryUtils = mock(ElasticSearchQueryUtils.class);

        elasticQueries = new Dtspb6221ElasticQueries(
            elasticSearchQueryUtils
        );

        when(elasticSearchQueryUtils.addSize(any(JSONObject.class), eq(100)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        when(elasticSearchQueryUtils.addSearchAfter(
            any(JSONObject.class),
            eq(Optional.empty())
        )).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void shouldBuildMigrationQuery() {
        JSONObject query =
            elasticQueries.getMigrationQuery(
                100,
                Optional.empty()
            );

        String queryString = query.toString();

        assertThat(queryString).contains("London")
            .contains("Manchester")
            .contains("Birmingham")
            .contains("Bristol")

            .contains("BOCaseMatchingReissue")

            .contains("BOCaseStoppedReissue")

            .contains("BOExaminingReissue")

            .contains("BOGrantIssued")

            .contains("BOGrantIssuedRegistrarEscalation")

            .contains("BOPostGrantIssued");
    }

    @Test
    void shouldBuildRollbackQuery() {
        JSONObject query =
            elasticQueries.getRollbackQuery(
                100,
                Optional.empty()
            );

        String queryString = query.toString();

        assertThat(queryString)
            .contains("registryLocation.keyword")

            .contains("ctsc")

            .contains("reference.keyword");
    }
}
