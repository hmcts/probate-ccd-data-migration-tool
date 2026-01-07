package uk.gov.hmcts.reform.migration.reimpl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;
import uk.gov.hmcts.reform.migration.reimpl.config.ReimplConfig;
import uk.gov.hmcts.reform.migration.reimpl.dto.CaseSummary;
import uk.gov.hmcts.reform.migration.reimpl.dto.CaseType;
import uk.gov.hmcts.reform.migration.reimpl.dto.MigrationEvent;
import uk.gov.hmcts.reform.migration.reimpl.dto.S2sToken;
import uk.gov.hmcts.reform.migration.reimpl.dto.UserToken;
import uk.gov.hmcts.reform.migration.reimpl.service.AuthenticationProvider;
import uk.gov.hmcts.reform.migration.reimpl.service.MigrationHandler;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReimplMigrationRunnerTest {
    @Mock
    ReimplConfig reimplConfigMock;
    @Mock
    AuthenticationProvider authenticationProviderMock;
    @Mock
    Map<String, MigrationHandler> migrationHandlersMock;

    AutoCloseable closeableMocks;

    ReimplMigrationRunner reimplMigrationRunner;

    @BeforeEach
    void setUp() {
        closeableMocks = MockitoAnnotations.openMocks(this);
        reimplMigrationRunner = new ReimplMigrationRunner(
                reimplConfigMock,
                authenticationProviderMock,
                migrationHandlersMock);
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
    void ifNoMigrationHandlerThenThrows() {
        when(reimplConfigMock.getMigrationId())
                .thenReturn("NO_MIGRATION_HANDLER");
        assertThrows(IllegalStateException.class, reimplMigrationRunner::runMigrations);
    }

    @Test
    void ifNoCasesFoundThenCompletes() {
        when(reimplConfigMock.getMigrationId())
                .thenReturn("NO_CASES_FOUND");

        final ExecutorService executorServiceMock = mock();
        when(reimplConfigMock.getNewExecutor())
                .thenReturn(executorServiceMock);

        final MigrationHandler migrationHandlerMock = mock();
        when(migrationHandlerMock.getCandidateCases(any(), any()))
                .thenReturn(Set.of());

        when(migrationHandlersMock.get(any()))
                .thenReturn(migrationHandlerMock);

        reimplMigrationRunner.runMigrations();

        verify(executorServiceMock, never()).submit(argThat(submitArg()));
    }

    @Test
    void oneCaseFoundThenCompletes() throws ExecutionException, InterruptedException {
        when(reimplConfigMock.getMigrationId())
            .thenReturn("ONE_CASE_FOUND");

        final ExecutorService executorServiceMock = mock();
        when(reimplConfigMock.getNewExecutor())
            .thenReturn(executorServiceMock);

        final MigrationHandler migrationHandlerMock = mock();
        when(migrationHandlerMock.getCandidateCases(any(), any()))
            .thenReturn(Set.of(new CaseSummary(1L, CaseType.CAVEAT)));

        when(migrationHandlersMock.get(any()))
            .thenReturn(migrationHandlerMock);

        final Future<ReimplMigrationRunner.MigrationState> futureMock = mock();
        when(executorServiceMock.submit(argThat(submitArg())))
                .thenReturn(futureMock);
        when(futureMock.isDone())
                .thenReturn(false, true);
        when(futureMock.get())
                .thenReturn(ReimplMigrationRunner.MigrationState.SUCCESS);

        reimplMigrationRunner.runMigrations();

        verify(executorServiceMock, times(1)).submit(argThat(submitArg()));
    }

    @Test
    void runMigrationFailsToStartEvent() {
        when(reimplConfigMock.getMigrationId())
                .thenReturn("RUN_NO_START_EVENT");

        final CaseSummary caseSummary = new CaseSummary(1L, CaseType.CAVEAT);

        final UserToken userToken = mock();
        when(authenticationProviderMock.getUserToken())
                .thenReturn(userToken);
        final S2sToken s2sToken = mock();
        when(authenticationProviderMock.getS2sToken())
                .thenReturn(s2sToken);

        final StartEventResponse startEventResponse = null;
        final MigrationEvent migrationEvent = mock();
        when(migrationEvent.startEventResponse())
                .thenReturn(startEventResponse);

        final MigrationHandler migrationHandlerMock = mock();
        when(migrationHandlerMock.startEventForCase(caseSummary, userToken, s2sToken))
                .thenReturn(migrationEvent);

        final ReimplMigrationRunner.MigrationState expected = ReimplMigrationRunner.MigrationState.FAILED;

        final ReimplMigrationRunner.MigrationState actual = reimplMigrationRunner.runMigration(
                migrationHandlerMock,
                caseSummary);

        assertAll(
                () -> assertThat(actual, equalTo(expected)),
                () -> verify(migrationHandlerMock, never()).shouldMigrateCase(any()));
    }

    @Test
    void runMigrationShouldNotMigrate() {
        when(reimplConfigMock.getMigrationId())
                .thenReturn("RUN_NO_MIGRATE");

        final CaseSummary caseSummary = new CaseSummary(1L, CaseType.CAVEAT);

        final UserToken userToken = mock();
        when(authenticationProviderMock.getUserToken())
                .thenReturn(userToken);
        final S2sToken s2sToken = mock();
        when(authenticationProviderMock.getS2sToken())
                .thenReturn(s2sToken);

        final MigrationEvent migrationEvent = mock();
        final StartEventResponse startEventResponse = mock();
        when(migrationEvent.startEventResponse())
                .thenReturn(startEventResponse);

        final MigrationHandler migrationHandlerMock = mock();
        when(migrationHandlerMock.startEventForCase(caseSummary, userToken, s2sToken))
                .thenReturn(migrationEvent);
        when(migrationHandlerMock.shouldMigrateCase(migrationEvent))
                .thenReturn(false);

        final ReimplMigrationRunner.MigrationState expected = ReimplMigrationRunner.MigrationState.SKIPPED;

        final ReimplMigrationRunner.MigrationState actual = reimplMigrationRunner.runMigration(
                migrationHandlerMock,
                caseSummary);

        assertAll(
                () -> assertThat(actual, equalTo(expected)),
                () -> verify(migrationHandlerMock, never()).migrate(any()));
    }

    @Test
    void runMigrationMigrationFailed() {
        when(reimplConfigMock.getMigrationId())
                .thenReturn("RUN_NO_MIGRATE");

        final CaseSummary caseSummary = new CaseSummary(1L, CaseType.CAVEAT);

        final UserToken userToken = mock();
        when(authenticationProviderMock.getUserToken())
                .thenReturn(userToken);
        final S2sToken s2sToken = mock();
        when(authenticationProviderMock.getS2sToken())
                .thenReturn(s2sToken);

        final MigrationEvent migrationEvent = mock();
        final StartEventResponse startEventResponse = mock();
        when(migrationEvent.startEventResponse())
                .thenReturn(startEventResponse);

        final MigrationHandler migrationHandlerMock = mock();
        when(migrationHandlerMock.startEventForCase(caseSummary, userToken, s2sToken))
                .thenReturn(migrationEvent);
        when(migrationHandlerMock.shouldMigrateCase(migrationEvent))
                .thenReturn(true);
        when(migrationHandlerMock.migrate(migrationEvent))
                .thenReturn(false);

        final ReimplMigrationRunner.MigrationState expected = ReimplMigrationRunner.MigrationState.FAILED;

        final ReimplMigrationRunner.MigrationState actual = reimplMigrationRunner.runMigration(
                migrationHandlerMock,
                caseSummary);

        assertThat(actual, equalTo(expected));
    }

    @Test
    void runMigrationMigrationSuccess() {
        when(reimplConfigMock.getMigrationId())
                .thenReturn("RUN_NO_MIGRATE");

        final CaseSummary caseSummary = new CaseSummary(1L, CaseType.CAVEAT);

        final UserToken userToken = mock();
        when(authenticationProviderMock.getUserToken())
                .thenReturn(userToken);
        final S2sToken s2sToken = mock();
        when(authenticationProviderMock.getS2sToken())
                .thenReturn(s2sToken);

        final MigrationEvent migrationEvent = mock();
        final StartEventResponse startEventResponse = mock();
        when(migrationEvent.startEventResponse())
                .thenReturn(startEventResponse);

        final MigrationHandler migrationHandlerMock = mock();
        when(migrationHandlerMock.startEventForCase(caseSummary, userToken, s2sToken))
                .thenReturn(migrationEvent);
        when(migrationHandlerMock.shouldMigrateCase(migrationEvent))
                .thenReturn(true);
        when(migrationHandlerMock.migrate(migrationEvent))
                .thenReturn(true);

        final ReimplMigrationRunner.MigrationState expected = ReimplMigrationRunner.MigrationState.SUCCESS;

        final ReimplMigrationRunner.MigrationState actual = reimplMigrationRunner.runMigration(
                migrationHandlerMock,
                caseSummary);

        assertThat(actual, equalTo(expected));
    }

    @Test
    void processTaskUnfinished() {
        final String migrationId = "PROCESS_TASK_UNFINISHED";
        final CaseSummary caseSummary = new CaseSummary(1L, CaseType.CAVEAT);
        final Future<ReimplMigrationRunner.MigrationState> futureMock = mock();
        final ReimplMigrationRunner.MigrationTask migrationTask = new ReimplMigrationRunner.MigrationTask(
                caseSummary,
                futureMock);
        when(futureMock.isDone())
                .thenReturn(false);

        final ReimplMigrationRunner.ReportingInfo reportingInfo = new ReimplMigrationRunner.ReportingInfo(migrationId);

        final ReimplMigrationRunner.TaskStatus actual = reimplMigrationRunner.processTask(
                migrationTask,
                reportingInfo);

        assertAll(
                () -> assertThat(reportingInfo.successfulMigrations(), hasSize(0)),
                () -> assertThat(reportingInfo.failedMigrations(), hasSize(0)),
                () -> assertThat(reportingInfo.skippedMigrations(), hasSize(0)),
                () -> assertThat(reportingInfo.exceptionMigrations(), hasSize(0)),
                () -> assertThat(actual, equalTo(ReimplMigrationRunner.TaskStatus.REQUEUE)));
    }

    @Test
    void processTaskSuccess() throws ExecutionException, InterruptedException {
        final String migrationId = "PROCESS_TASK_SUCCESS";
        final CaseSummary caseSummary = new CaseSummary(1L, CaseType.CAVEAT);
        final Future<ReimplMigrationRunner.MigrationState> futureMock = mock();
        final ReimplMigrationRunner.MigrationTask migrationTask = new ReimplMigrationRunner.MigrationTask(
                caseSummary,
                futureMock);
        when(futureMock.isDone())
                .thenReturn(true);
        when(futureMock.get())
                .thenReturn(ReimplMigrationRunner.MigrationState.SUCCESS);

        final ReimplMigrationRunner.ReportingInfo reportingInfo = new ReimplMigrationRunner.ReportingInfo(migrationId);

        final ReimplMigrationRunner.TaskStatus actual = reimplMigrationRunner.processTask(
                migrationTask,
                reportingInfo);

        assertAll(
                () -> assertThat(reportingInfo.successfulMigrations(), hasSize(1)),
                () -> assertThat(reportingInfo.failedMigrations(), hasSize(0)),
                () -> assertThat(reportingInfo.skippedMigrations(), hasSize(0)),
                () -> assertThat(reportingInfo.exceptionMigrations(), hasSize(0)),
                () -> assertThat(actual, equalTo(ReimplMigrationRunner.TaskStatus.COMPLETE)));
    }

    @Test
    void processTaskFailed() throws ExecutionException, InterruptedException {
        final String migrationId = "PROCESS_TASK_FAILED";
        final CaseSummary caseSummary = new CaseSummary(1L, CaseType.CAVEAT);
        final Future<ReimplMigrationRunner.MigrationState> futureMock = mock();
        final ReimplMigrationRunner.MigrationTask migrationTask = new ReimplMigrationRunner.MigrationTask(
                caseSummary,
                futureMock);
        when(futureMock.isDone())
                .thenReturn(true);
        when(futureMock.get())
                .thenReturn(ReimplMigrationRunner.MigrationState.FAILED);

        final ReimplMigrationRunner.ReportingInfo reportingInfo = new ReimplMigrationRunner.ReportingInfo(migrationId);

        final ReimplMigrationRunner.TaskStatus actual = reimplMigrationRunner.processTask(
                migrationTask,
                reportingInfo);

        assertAll(
                () -> assertThat(reportingInfo.successfulMigrations(), hasSize(0)),
                () -> assertThat(reportingInfo.failedMigrations(), hasSize(1)),
                () -> assertThat(reportingInfo.skippedMigrations(), hasSize(0)),
                () -> assertThat(reportingInfo.exceptionMigrations(), hasSize(0)),
                () -> assertThat(actual, equalTo(ReimplMigrationRunner.TaskStatus.COMPLETE)));
    }

    @Test
    void processTaskSkipped() throws ExecutionException, InterruptedException {
        final String migrationId = "PROCESS_TASK_SKIPPED";
        final CaseSummary caseSummary = new CaseSummary(1L, CaseType.CAVEAT);
        final Future<ReimplMigrationRunner.MigrationState> futureMock = mock();
        final ReimplMigrationRunner.MigrationTask migrationTask = new ReimplMigrationRunner.MigrationTask(
                caseSummary,
                futureMock);
        when(futureMock.isDone())
                .thenReturn(true);
        when(futureMock.get())
                .thenReturn(ReimplMigrationRunner.MigrationState.SKIPPED);

        final ReimplMigrationRunner.ReportingInfo reportingInfo = new ReimplMigrationRunner.ReportingInfo(migrationId);

        final ReimplMigrationRunner.TaskStatus actual = reimplMigrationRunner.processTask(
                migrationTask,
                reportingInfo);

        assertAll(
                () -> assertThat(reportingInfo.successfulMigrations(), hasSize(0)),
                () -> assertThat(reportingInfo.failedMigrations(), hasSize(0)),
                () -> assertThat(reportingInfo.skippedMigrations(), hasSize(1)),
                () -> assertThat(reportingInfo.exceptionMigrations(), hasSize(0)),
                () -> assertThat(actual, equalTo(ReimplMigrationRunner.TaskStatus.COMPLETE)));
    }

    @Test
    void processTaskException() throws ExecutionException, InterruptedException {
        final String migrationId = "PROCESS_TASK_EXCEPTION";
        final CaseSummary caseSummary = new CaseSummary(1L, CaseType.CAVEAT);
        final Future<ReimplMigrationRunner.MigrationState> futureMock = mock();
        final ReimplMigrationRunner.MigrationTask migrationTask = new ReimplMigrationRunner.MigrationTask(
                caseSummary,
                futureMock);
        when(futureMock.isDone())
                .thenReturn(true);

        final ExecutionException executionException = mock();
        when(executionException.getCause()).thenReturn(new Exception());
        when(futureMock.get())
                .thenThrow(executionException);

        final ReimplMigrationRunner.ReportingInfo reportingInfo = new ReimplMigrationRunner.ReportingInfo(migrationId);

        final ReimplMigrationRunner.TaskStatus actual = reimplMigrationRunner.processTask(
                migrationTask,
                reportingInfo);

        assertAll(
                () -> assertThat(reportingInfo.successfulMigrations(), hasSize(0)),
                () -> assertThat(reportingInfo.failedMigrations(), hasSize(0)),
                () -> assertThat(reportingInfo.skippedMigrations(), hasSize(0)),
                () -> assertThat(reportingInfo.exceptionMigrations(), hasSize(1)),
                () -> assertThat(actual, equalTo(ReimplMigrationRunner.TaskStatus.COMPLETE)));
    }

    @Test
    void processTaskInterrupted() throws ExecutionException, InterruptedException {
        final String migrationId = "PROCESS_TASK_INTERRUPED";
        final CaseSummary caseSummary = new CaseSummary(1L, CaseType.CAVEAT);
        final Future<ReimplMigrationRunner.MigrationState> futureMock = mock();
        final ReimplMigrationRunner.MigrationTask migrationTask = new ReimplMigrationRunner.MigrationTask(
                caseSummary,
                futureMock);
        when(futureMock.isDone())
                .thenReturn(true);

        final InterruptedException interruptedException = mock();
        when(futureMock.get())
                .thenThrow(interruptedException);

        final ReimplMigrationRunner.ReportingInfo reportingInfo = new ReimplMigrationRunner.ReportingInfo(migrationId);

        final ReimplMigrationRunner.TaskStatus actual = reimplMigrationRunner.processTask(
                migrationTask,
                reportingInfo);

        assertAll(
                () -> assertThat(reportingInfo.successfulMigrations(), hasSize(0)),
                () -> assertThat(reportingInfo.failedMigrations(), hasSize(0)),
                () -> assertThat(reportingInfo.skippedMigrations(), hasSize(0)),
                () -> assertThat(reportingInfo.exceptionMigrations(), hasSize(0)),
                () -> assertThat(actual, equalTo(ReimplMigrationRunner.TaskStatus.REQUEUE)),
                () -> assertThat(Thread.currentThread().isInterrupted(), equalTo(true)));
    }

    ArgumentMatcher<Callable<ReimplMigrationRunner.MigrationState>> submitArg() {
        return argument -> true;
    }
}
