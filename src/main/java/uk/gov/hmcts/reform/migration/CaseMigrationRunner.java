package uk.gov.hmcts.reform.migration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.PropertySource;

import java.text.MessageFormat;

@Slf4j
@SpringBootApplication(scanBasePackages = "uk.gov.hmcts.reform.migration")
@EnableFeignClients(basePackages = {"uk.gov.hmcts.reform.migration", "uk.gov.hmcts.reform.idam.client"})
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

    @Value("${case.migration.processing.caseReferences}")
    private String caseReferences;

    @Value("${rollback.processing.caseReferences}")
    private boolean rollbackCaseReferences;

    public static void main(String[] args) {
        SpringApplication.run(CaseMigrationRunner.class, args);
    }

    @Override
    public void run(String... args) {
        try {
            if (null != caseReferences && caseReferences.length() > 0) {
                log.info("case References to be migrate :  {}",caseReferences);
                log.info("CaseMigrationRunner rollbackCaseReferences = {} ", rollbackCaseReferences);
                if (rollbackCaseReferences) {
                    caseMigrationRollbackProcessor.rollbackCaseReferenceList(caseType,caseReferences);
                } else {
                    caseMigrationProcessor.migrateCaseReferenceList(caseType,caseReferences);
                }
            } else {
                if (defaultThreadLimit <= 1) {
                    log.info("CaseMigrationRunner.defaultThreadLimit= {} ", defaultThreadLimit);
                    if (migrationrollbackStartDatetime != null && migrationrollbackStartDatetime.length() > 0
                        && migrationrollbackEndDatetime != null && migrationrollbackEndDatetime.length() > 0) {
                        log.info("CaseMigrationRunner rollback  startDatetime: {} endDatetime: {}",
                            migrationrollbackStartDatetime, migrationrollbackEndDatetime);
                        caseMigrationRollbackProcessor.rollbackCases(caseType);
                    } else {
                        caseMigrationProcessor.migrateCases(caseType);
                    }
                } else {
                    log.info("CaseMigrationRunner.defaultThreadLimit= {} ", defaultThreadLimit);
                    if (migrationrollbackStartDatetime != null && migrationrollbackStartDatetime.length() > 0
                        && migrationrollbackEndDatetime != null && migrationrollbackEndDatetime.length() > 0) {
                        log.info("CaseMigrationRunner rollback  startDatetime: {} endDatetime: {}",
                            migrationrollbackStartDatetime, migrationrollbackEndDatetime);
                        caseMigrationRollbackProcessor.processRollback("GrantOfRepresentation");
                        caseMigrationRollbackProcessor.processRollback("Caveat");
                    } else {
                        caseMigrationProcessor.process("GrantOfRepresentation");
                        caseMigrationProcessor.process("Caveat");
                    }
                }
            }
        } catch (Exception e) {
            log.error(
                MessageFormat.format("Migration failed with the following reason: {0}", e.getMessage()),
                e);
        }
    }
}
