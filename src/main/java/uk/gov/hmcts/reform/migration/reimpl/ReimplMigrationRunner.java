package uk.gov.hmcts.reform.migration.reimpl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;
import uk.gov.hmcts.reform.migration.repository.IdamRepository;
import uk.gov.hmcts.reform.migration.reimpl.config.ReimplConfig;
import uk.gov.hmcts.reform.migration.reimpl.dto.CaseSummary;
import uk.gov.hmcts.reform.migration.reimpl.dto.S2sToken;
import uk.gov.hmcts.reform.migration.reimpl.dto.UserToken;
import uk.gov.hmcts.reform.migration.reimpl.service.MigrationHandler;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Slf4j
public class ReimplMigrationRunner {

    private final IdamRepository idamRepository;
    private final AuthTokenGenerator authTokenGenerator;

    private final ReimplConfig reimplConfig;

    private final Map<String, MigrationHandler> migrationHandlers;

    public ReimplMigrationRunner(
            final IdamRepository idamRepository,
            final AuthTokenGenerator authTokenGenerator,
            final ReimplConfig reimplConfig,
            final Map<String, MigrationHandler> migrationHandlers) {
        this.idamRepository = Objects.requireNonNull(idamRepository);
        this.authTokenGenerator = Objects.requireNonNull(authTokenGenerator);

        this.reimplConfig = Objects.requireNonNull(reimplConfig);
        this.migrationHandlers = Objects.requireNonNull(migrationHandlers);
    }

    public void runMigrations() {
        final Set<CaseSummary> exceptionMigrations = new HashSet<>();
        final Set<CaseSummary> failedMigrations = new HashSet<>();
        final Set<CaseSummary> skippedMigrations = new HashSet<>();

        final ExecutorService executorService = Executors.newFixedThreadPool(reimplConfig.getDefaultThreadlimit());

        // TODO get this properly
        final String migrationId = reimplConfig.getMigrationId();
        final MigrationHandler migrationHandler = migrationHandlers.get(migrationId);
        if (migrationHandler == null) {
            log.error("{}: No migration handler found", migrationId);
            throw new IllegalStateException("No migration handler found for " + migrationId);
        }
        log.info("{}: Starting migration", migrationId);

        final UserToken userToken = idamRepository.generateUserTokenObject();
        final S2sToken s2sToken = new S2sToken(authTokenGenerator.generate());

        final Set<CaseSummary> candidateCaseReferences = migrationHandler.getCandidateCases(userToken, s2sToken);
        log.info("{}: Identified {} candidate cases for migration", migrationId, candidateCaseReferences.size());

        Queue<MigrationTask> taskQueue = new ArrayDeque<>(candidateCaseReferences.size());
        for (final CaseSummary caseSummary : candidateCaseReferences) {
            final Future<MigrationState> task = executorService.submit(() -> runMigration(
                    migrationHandler,
                    caseSummary,
                    userToken,
                    s2sToken));
            taskQueue.add(new MigrationTask(caseSummary, task, new AtomicInteger(0)));
        }
        log.info("{}: Finished queuing migration tasks", migrationId);

        while (!taskQueue.isEmpty()) {
            final MigrationTask migrationTask = taskQueue.poll();
            final CaseSummary caseSummary = migrationTask.caseSummary;
            final Future<MigrationState> future = migrationTask.task;
            if (future.isDone()) {
                try {
                    final MigrationState result = future.get();
                    switch (result) {
                        case SUCCESS -> log.info("{}: Successfully migrated case: {}", migrationId, caseSummary);
                        case FAILED -> {
                            log.warn("{}: Migration failed for case: {}", migrationId, caseSummary);
                            failedMigrations.add(caseSummary);
                        }
                        case SKIPPED -> {
                            log.info("{}: Migration skipped for case: {}", migrationId, caseSummary);
                            skippedMigrations.add(caseSummary);
                        }
                    }
                } catch (ExecutionException e) {
                    log.error("{}: Exception executing task for case: {}", migrationId, caseSummary, e.getCause());
                    exceptionMigrations.add(caseSummary);
                } catch (InterruptedException e) {
                    log.error("{}: Task interrupted for case: {}", migrationId, caseSummary, e);
                    exceptionMigrations.add(caseSummary);
                }
            } else {
                final Integer waitCount = migrationTask.counter.incrementAndGet();
                log.info("{}: case migration for {} incomplete, checked {} times", migrationId, caseSummary, waitCount);
                taskQueue.add(migrationTask);
            }
        }
        log.info("{}: Finished waiting for migration tasks", migrationId);

        // TODO reporting?
    }

    private record MigrationTask(CaseSummary caseSummary, Future<MigrationState> task, AtomicInteger counter) {}

    private enum MigrationState {
        SUCCESS,
        FAILED,
        SKIPPED,
        ;
    }

    private MigrationState runMigration(
            final MigrationHandler migrationHandler,
            final CaseSummary caseSummary,
            final UserToken userToken,
            final S2sToken s2sToken) {
        final StartEventResponse startEventResponse = migrationHandler.startEventForCase(
                caseSummary,
                userToken,
                s2sToken);
        if (startEventResponse == null) {
            log.error("{}: event not started for {} case {}",
                    reimplConfig.getMigrationId(),
                    caseSummary.type(),
                    caseSummary.reference());
            return MigrationState.FAILED;
        }
        final boolean shouldMigrate = migrationHandler.shouldMigrateCase(
                caseSummary,
                startEventResponse);

        if (shouldMigrate) {
            final boolean migrationSuccess = migrationHandler.migrate(
                    caseSummary,
                    startEventResponse,
                    userToken,
                    s2sToken);
            if (migrationSuccess) {
                return MigrationState.SUCCESS;
            } else {
                return MigrationState.FAILED;
            }
        } else {
            return MigrationState.SKIPPED;
        }
    }
}
