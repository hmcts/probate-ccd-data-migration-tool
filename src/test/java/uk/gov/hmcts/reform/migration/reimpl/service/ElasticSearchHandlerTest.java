package uk.gov.hmcts.reform.migration.reimpl.service;

import org.hamcrest.Matchers;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import uk.gov.hmcts.reform.ccd.client.CoreCaseDataApi;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.ccd.client.model.SearchResult;
import uk.gov.hmcts.reform.migration.reimpl.dto.CaseSummary;
import uk.gov.hmcts.reform.migration.reimpl.dto.CaseType;
import uk.gov.hmcts.reform.migration.reimpl.dto.S2sToken;
import uk.gov.hmcts.reform.migration.reimpl.dto.UserToken;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ElasticSearchHandlerTest {
    @Mock
    CoreCaseDataApi coreCaseDataApiMock;

    AutoCloseable closeableMocks;

    ElasticSearchHandler elasticSearchHandler;

    @BeforeEach
    void setUp() {
        closeableMocks = MockitoAnnotations.openMocks(this);
        elasticSearchHandler = new ElasticSearchHandler(coreCaseDataApiMock);
    }

    @AfterEach
    void tearDown() {
        try {
            closeableMocks.close();
        } catch (Exception e) {
            // nothing to do
        }
    }

    @Test
    void testNullCaseSearch() {
        final String migrationId = "NULL_CASE_SEARCH";
        final String query = "NULL_CASE_SEARCH_QUERY";

        final JSONObject queryJson = mock();
        when(queryJson.toString())
                .thenReturn(query);

        final Function<Optional<Long>, JSONObject> querySource = mock();
        when(querySource.apply(any()))
                .thenReturn(queryJson);

        final UserToken userToken = mock();
        final S2sToken s2sToken = mock();
        final CaseType caseType = CaseType.CAVEAT;

        final SearchResult initialSearchResult = null;

        when(coreCaseDataApiMock.searchCases(any(), any(), any(), any()))
                .thenReturn(initialSearchResult);

        final Set<CaseSummary> actual = elasticSearchHandler.searchCases(
                migrationId,
                userToken,
                s2sToken,
                caseType,
                querySource);

        assertThat(actual, Matchers.empty());
    }

    @Test
    void testNoLoopNullCaseSearch() {
        final String migrationId = "NO_LOOP_NULL_CASE_SEARCH";
        final String query = "NO_LOOP_NULL_CASE_SEARCH_QUERY";

        final JSONObject queryJson = mock();
        when(queryJson.toString())
            .thenReturn(query);

        final Function<Optional<Long>, JSONObject> querySource = mock();
        when(querySource.apply(any()))
            .thenReturn(queryJson);

        final UserToken userToken = mock();
        final S2sToken s2sToken = mock();
        final CaseType caseType = CaseType.CAVEAT;

        final List<CaseDetails> initialCases = caseDetailsMocks(1L, 1L);
        final SearchResult initialSearchResult = mock();
        when(initialSearchResult.getTotal())
                .thenReturn(1);
        when(initialSearchResult.getCases())
                .thenReturn(initialCases);

        final SearchResult secondSearchResult = null;

        when(coreCaseDataApiMock.searchCases(any(), any(), any(), any()))
            .thenReturn(initialSearchResult, secondSearchResult);

        final Set<CaseSummary> actual = elasticSearchHandler.searchCases(
            migrationId,
            userToken,
            s2sToken,
            caseType,
            querySource);

        assertThat(actual, Matchers.hasSize(1));
    }

    @Test
    void testNoLoopEmptyCaseSearch() {
        final String migrationId = "NO_LOOP_EMPTY_CASE_SEARCH";
        final String query = "NO_LOOP_EMPTY_CASE_SEARCH_QUERY";

        final JSONObject queryJson = mock();
        when(queryJson.toString())
            .thenReturn(query);

        final Function<Optional<Long>, JSONObject> querySource = mock();
        when(querySource.apply(any()))
            .thenReturn(queryJson);

        final UserToken userToken = mock();
        final S2sToken s2sToken = mock();
        final CaseType caseType = CaseType.CAVEAT;

        final List<CaseDetails> initialCases = caseDetailsMocks(1L, 1L);
        final SearchResult initialSearchResult = mock();
        when(initialSearchResult.getTotal())
                .thenReturn(1);
        when(initialSearchResult.getCases())
                .thenReturn(initialCases);

        final SearchResult secondSearchResult = mock();
        when(secondSearchResult.getTotal())
            .thenReturn(1);
        when(secondSearchResult.getCases())
            .thenReturn(List.of());

        when(coreCaseDataApiMock.searchCases(any(), any(), any(), any()))
            .thenReturn(initialSearchResult, secondSearchResult);

        final Set<CaseSummary> actual = elasticSearchHandler.searchCases(
            migrationId,
            userToken,
            s2sToken,
            caseType,
            querySource);

        assertThat(actual, Matchers.hasSize(1));
    }

    @Test
    void testOneLoopEmptyCaseSearch() {
        final String migrationId = "NO_LOOP_EMPTY_CASE_SEARCH";
        final String query = "NO_LOOP_EMPTY_CASE_SEARCH_QUERY";

        final JSONObject queryJson = mock();
        when(queryJson.toString())
            .thenReturn(query);

        final Function<Optional<Long>, JSONObject> querySource = mock();
        when(querySource.apply(any()))
            .thenReturn(queryJson);

        final UserToken userToken = mock();
        final S2sToken s2sToken = mock();
        final CaseType caseType = CaseType.CAVEAT;

        final List<CaseDetails> initialCases = caseDetailsMocks(1L, 1L);
        final SearchResult initialSearchResult = mock();
        when(initialSearchResult.getTotal())
                .thenReturn(1);
        when(initialSearchResult.getCases())
                .thenReturn(initialCases);

        final List<CaseDetails> secondCases = caseDetailsMocks(2L, 1L);
        final SearchResult secondSearchResult = mock();
        when(secondSearchResult.getTotal())
            .thenReturn(2);
        when(secondSearchResult.getCases())
            .thenReturn(secondCases);

        final SearchResult thirdSearchResult = mock();
        when(thirdSearchResult.getTotal())
            .thenReturn(2);
        when(thirdSearchResult.getCases())
            .thenReturn(List.of());

        when(coreCaseDataApiMock.searchCases(any(), any(), any(), any()))
            .thenReturn(initialSearchResult, secondSearchResult, thirdSearchResult);

        final Set<CaseSummary> actual = elasticSearchHandler.searchCases(
            migrationId,
            userToken,
            s2sToken,
            caseType,
            querySource);

        assertThat(actual, Matchers.hasSize(2));
    }

    private static List<CaseDetails> caseDetailsMocks(
            final Long firstId,
            final Long count) {
        final List<CaseDetails> caseDetailsList = new ArrayList<>();
        for (long i = 0; i < count; i++) {
            final long caseRef = firstId + i;
            CaseDetails caseDetails = mock();
            when(caseDetails.getId())
                .thenReturn(caseRef);
            caseDetailsList.add(caseDetails);
        }
        return caseDetailsList;
    }
}
