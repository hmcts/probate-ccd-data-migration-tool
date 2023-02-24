package uk.gov.hmcts.reform.migration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.PropertySource;

@Slf4j
@SpringBootApplication
@PropertySource("classpath:application.properties")
public class CaseMigrationRunner implements CommandLineRunner {

    @Autowired
    private CaseMigrationProcessor caseMigrationProcessor;

    @Autowired
    private CaseMigrationRollbackProcessor caseMigrationRollbackProcessor;

    @Value("${migration.caseType}")
    private String caseType;

    @Value("${default.thread.limit}")
    private int defaultThreadLimit;

    @Value("${migration.rollback.start.datetime}")
    private String migrationrollbackStartDatetime;

    @Value("${migration.rollback.end.datetime}")
    private String migrationrollbackEndDatetime;

    public static void main(String[] args) {
        SpringApplication.run(CaseMigrationRunner.class, args);
    }

    @Override
    public void run(String... args) {
        try {
            if (defaultThreadLimit <= 1) {
                log.info("CaseMigrationRunner.defaultThreadLimit= {} ", defaultThreadLimit);
                if (migrationrollbackStartDatetime != null && migrationrollbackStartDatetime.length() > 0
                    && migrationrollbackEndDatetime != null && migrationrollbackEndDatetime.length() > 0) {
                    log.info("CaseMigrationRunner rollback  startDatetime: {} endDatetmie: {}",
                        migrationrollbackStartDatetime, migrationrollbackEndDatetime);
                    caseMigrationRollbackProcessor.rollbackCases(caseType);
                } else {
                    caseMigrationProcessor.migrateCases(caseType);
                }
            } else {
                log.info("CaseMigrationRunner.defaultThreadLimit= {} ", defaultThreadLimit);
                if (migrationrollbackStartDatetime != null && migrationrollbackStartDatetime.length() > 0
                    && migrationrollbackEndDatetime != null && migrationrollbackEndDatetime.length() > 0) {
                    log.info("CaseMigrationRunner rollback  startDatetime: {} endDatetmie: {}",
                        migrationrollbackStartDatetime, migrationrollbackEndDatetime);
                    caseMigrationRollbackProcessor.processRollback(caseType);
                } else {
                    caseMigrationProcessor.process(caseType);
                }
            }


        } catch (Exception e) {
            log.error("Migration failed with the following reason: {}", e.getMessage(), e);
        }
    }
}
