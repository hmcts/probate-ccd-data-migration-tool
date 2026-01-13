package uk.gov.hmcts.reform.migration.reimpl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.migration.reimpl.config.ReimplConfig;
import uk.gov.hmcts.reform.migration.reimpl.dto.CaseSummary;
import uk.gov.hmcts.reform.migration.reimpl.dto.MigrationEvent;
import uk.gov.hmcts.reform.migration.reimpl.service.AuthenticationProvider;
import uk.gov.hmcts.reform.migration.reimpl.service.MigrationHandler;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Slf4j
public class ReimplMigrationRunner {

    private final ReimplConfig reimplConfig;

    private final AuthenticationProvider authenticationProvider;

    private final Map<String, MigrationHandler> migrationHandlers;

    public ReimplMigrationRunner(
            final ReimplConfig reimplConfig,
            final AuthenticationProvider authenticationProvider,
            final Map<String, MigrationHandler> migrationHandlers) {
        this.reimplConfig = Objects.requireNonNull(reimplConfig);
        this.authenticationProvider = Objects.requireNonNull(authenticationProvider);
        this.migrationHandlers = Objects.requireNonNull(migrationHandlers);
    }

    public void runMigrations() {
        final Set<CaseSummary> successfulMigrations = new HashSet<>();
        final Set<CaseSummary> exceptionMigrations = new HashSet<>();
        final Set<CaseSummary> failedMigrations = new HashSet<>();
        final Set<CaseSummary> skippedMigrations = new HashSet<>();

        final String migrationId = reimplConfig.getMigrationId();

        try (final ExecutorService executorService = reimplConfig.getNewExecutor()) {
            final MigrationHandler migrationHandler = migrationHandlers.get(migrationId);
            if (migrationHandler == null) {
                log.error("{}: No migration handler found", migrationId);
                throw new IllegalStateException("No migration handler found for " + migrationId);
            }
            log.info("{}: Starting migration", migrationId);

            final Set<CaseSummary> candidateCaseReferences = migrationHandler.getCandidateCases(
                    authenticationProvider.getUserToken(),
                    authenticationProvider.getS2sToken());
            log.info("{}: Identified {} candidate cases for migration", migrationId, candidateCaseReferences.size());

            Queue<MigrationTask> taskQueue = new ArrayDeque<>(candidateCaseReferences.size());
            for (final CaseSummary caseSummary : candidateCaseReferences) {
                final Future<MigrationState> task = executorService.submit(() -> runMigration(
                        migrationHandler,
                        caseSummary));
                taskQueue.add(new MigrationTask(caseSummary, task, new AtomicInteger(0)));
            }
            log.info("{}: Finished queuing migration tasks", migrationId);

            while (!taskQueue.isEmpty()) {
                final MigrationTask firstTask = taskQueue.peek();
                final int loopCount = firstTask.counter.get();
                log.info("{}: Processing task queue iteration {} (pre sleep)", migrationId, loopCount);
                try {
                    Thread.sleep(Duration.ofSeconds(loopCount));
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                log.info("{}: Processing task queue iteration {} (post sleep)", migrationId, loopCount);
                // the condition inside the loop is evaluated on each iteration so if a task completes the queue size
                // will reduce. this means the last logical entry in this iteration will not be checked. fixing the size
                // once at the start and iterating up to that fixes the issue.
                final int initialTaskQueueSize = taskQueue.size();
                for (int i = 0; i < initialTaskQueueSize; i++) {
                    final MigrationTask migrationTask = taskQueue.poll();
                    final CaseSummary caseSummary = migrationTask.caseSummary;
                    final Future<MigrationState> future = migrationTask.task;
                    if (future.isDone()) {
                        try {
                            final MigrationState result = future.get();
                            // this value is never used - it's only present to enforce that this is a switch expression
                            // and thus prevent the style warning from a switch statement not having a default handler.
                            @SuppressWarnings("java:S1481")
                            final boolean r = switch (result) {
                                case SUCCESS -> {
                                    log.info("{}: Successfully migrated case: {}", migrationId, caseSummary);
                                    yield successfulMigrations.add(caseSummary);
                                }
                                case FAILED -> {
                                    log.warn("{}: Migration failed for case: {}", migrationId, caseSummary);
                                    yield failedMigrations.add(caseSummary);
                                }
                                case SKIPPED -> {
                                    log.info("{}: Migration skipped for case: {}", migrationId, caseSummary);
                                    yield skippedMigrations.add(caseSummary);
                                }
                            };
                        } catch (ExecutionException e) {
                            log.error("{}: Exception executing task for case: {}",
                                    migrationId,
                                    caseSummary,
                                    e.getCause());
                            exceptionMigrations.add(caseSummary);
                        } catch (InterruptedException e) {
                            log.error("{}: Task interrupted for case: {}", migrationId, caseSummary, e);
                            exceptionMigrations.add(caseSummary);
                        }
                    } else {
                        migrationTask.counter.incrementAndGet();
                        taskQueue.add(migrationTask);
                    }
                }
            }
            log.info("{}: Finished waiting for migration tasks", migrationId);
        }

        log.info("{}: Successfully migrated {} cases", migrationId, successfulMigrations.size());
        log.info("{}: Skipped migrating {} cases", migrationId, skippedMigrations.size());
        log.info("{}: Failed to migrate {} cases", migrationId, failedMigrations.size());
        for (final CaseSummary caseSummary : failedMigrations) {
            log.info("{}: Failed migrating case: {}", migrationId, caseSummary);
        }
        log.info("{}: Exception when migrating {} cases", migrationId, exceptionMigrations.size());
        for (final CaseSummary caseSummary : exceptionMigrations) {
            log.info("{}: Exception migrating case: {}", migrationId, caseSummary);
        }
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
            final CaseSummary caseSummary) {
        final MigrationEvent migrationEvent = migrationHandler.startEventForCase(
                caseSummary,
                authenticationProvider.getUserToken(),
                authenticationProvider.getS2sToken());
        if (migrationEvent.startEventResponse() == null) {
            log.error("{}: event not started for {} case {}",
                    reimplConfig.getMigrationId(),
                    caseSummary.type(),
                    caseSummary.reference());
            return MigrationState.FAILED;
        }
        final boolean shouldMigrate = migrationHandler.shouldMigrateCase(migrationEvent);

        if (shouldMigrate) {
            final boolean migrationSuccess = migrationHandler.migrate(migrationEvent);
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
