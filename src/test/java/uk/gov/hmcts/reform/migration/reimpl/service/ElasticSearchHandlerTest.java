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
import uk.gov.hmcts.reform.migration.reimpl.config.ReimplConfig;
import uk.gov.hmcts.reform.migration.reimpl.dto.CaseSummary;
import uk.gov.hmcts.reform.migration.reimpl.dto.CaseType;
import uk.gov.hmcts.reform.migration.reimpl.dto.S2sToken;
import uk.gov.hmcts.reform.migration.reimpl.dto.UserToken;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ElasticSearchHandlerTest {
    @Mock
    CoreCaseDataApi coreCaseDataApiMock;
    @Mock
    ReimplConfig reimplConfigMock;

    AutoCloseable closeableMocks;

    ElasticSearchHandler elasticSearchHandler;

    @BeforeEach
    void setUp() {
        closeableMocks = MockitoAnnotations.openMocks(this);
        when(reimplConfigMock.getQuerySize()).thenReturn(10);
        elasticSearchHandler = new ElasticSearchHandler(coreCaseDataApiMock, reimplConfigMock);
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
    void testEmptyCaseSearch() {
        final String migrationId = "EMPTY_CASE_SEARCH";
        final String query = "EMPTY_CASE_SEARCH_QUERY";

        final JSONObject queryJson = mock();
        when(queryJson.toString())
                .thenReturn(query);

        final Function<Optional<Long>, JSONObject> querySource = mock();
        when(querySource.apply(any()))
                .thenReturn(queryJson);

        final UserToken userToken = mock();
        final S2sToken s2sToken = mock();
        final CaseType caseType = CaseType.CAVEAT;

        final SearchResult initialSearchResult = mock();
        when(initialSearchResult.getTotal())
                .thenReturn(0);

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

    @Test
    void shouldRejectNegativeMaximumResults() {
        final BiFunction<Integer, Optional<Long>, JSONObject> querySource =
            mock();

        assertThrows(
            IllegalArgumentException.class,
            () -> elasticSearchHandler.searchCases(
                "NEGATIVE_LIMIT",
                mock(UserToken.class),
                mock(S2sToken.class),
                CaseType.CAVEAT,
                OptionalInt.of(-1),
                querySource
            )
        );

        verifyNoInteractions(coreCaseDataApiMock, querySource);
    }

    @Test
    void shouldNotSearchWhenMaximumResultsIsZero() {
        final BiFunction<Integer, Optional<Long>, JSONObject> querySource =
            mock();

        final Set<CaseSummary> result =
            elasticSearchHandler.searchCases(
                "ZERO_LIMIT",
                mock(UserToken.class),
                mock(S2sToken.class),
                CaseType.CAVEAT,
                OptionalInt.of(0),
                querySource
            );

        assertThat(result, Matchers.empty());
        verifyNoInteractions(coreCaseDataApiMock, querySource);
    }

    @Test
    void shouldLimitInitialSearchToMaximumResults() {
        when(reimplConfigMock.getQuerySize()).thenReturn(10);

        final JSONObject queryJson = mock();
        when(queryJson.toString()).thenReturn("LIMITED_QUERY");

        final BiFunction<Integer, Optional<Long>, JSONObject> querySource = mock();
        when(querySource.apply(2, Optional.empty()))
            .thenReturn(queryJson);

        final List<CaseDetails> initialCases = caseDetailsMocks(1L, 2L);

        final SearchResult initialResult = mock();
        when(initialResult.getTotal()).thenReturn(2);
        when(initialResult.getCases()).thenReturn(initialCases);

        when(coreCaseDataApiMock.searchCases(any(), any(), any(), any()))
            .thenReturn(initialResult);

        final UserToken userToken = mock();
        final S2sToken s2sToken = mock();

        final Set<CaseSummary> result = elasticSearchHandler.searchCases(
            "LIMIT_INITIAL_SEARCH",
            userToken,
            s2sToken,
            CaseType.CAVEAT,
            OptionalInt.of(2),
            querySource
        );

        assertThat(result, Matchers.hasSize(2));

        verify(querySource).apply(2, Optional.empty());
        verify(coreCaseDataApiMock, times(1))
            .searchCases(any(), any(), any(), any());
    }

    @Test
    void shouldUseRemainingLimitForNextPage() {
        when(reimplConfigMock.getQuerySize()).thenReturn(2);

        final JSONObject queryJson = mock();
        when(queryJson.toString()).thenReturn("PAGED_QUERY");

        final BiFunction<Integer, Optional<Long>, JSONObject> querySource = mock();
        when(querySource.apply(anyInt(), any())).thenReturn(queryJson);

        final List<CaseDetails> initialCases = caseDetailsMocks(1L, 2L);
        final List<CaseDetails> nextCases = caseDetailsMocks(3L, 1L);

        final SearchResult initialResult = mock();
        when(initialResult.getTotal()).thenReturn(3);
        when(initialResult.getCases()).thenReturn(initialCases);

        final SearchResult nextResult = mock();
        when(nextResult.getCases()).thenReturn(nextCases);

        when(coreCaseDataApiMock.searchCases(any(), any(), any(), any()))
            .thenReturn(initialResult, nextResult);

        final UserToken userToken = mock();
        final S2sToken s2sToken = mock();

        final Set<CaseSummary> result = elasticSearchHandler.searchCases(
            "LIMIT_NEXT_PAGE",
            userToken,
            s2sToken,
            CaseType.CAVEAT,
            OptionalInt.of(3),
            querySource
        );

        assertThat(result, Matchers.hasSize(3));

        verify(querySource).apply(2, Optional.empty());
        verify(querySource).apply(1, Optional.of(2L));

        verify(coreCaseDataApiMock, times(2))
            .searchCases(any(), any(), any(), any());
    }
}
