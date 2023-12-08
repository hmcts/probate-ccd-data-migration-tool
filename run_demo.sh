#!/usr/bin/env bash
/Users/iswarya.pepakayala/Library/Java/JavaVirtualMachines/corretto-17.0.7/Contents/Home/bin/java -jar \
-Dspring.application.name="probate-ccd-case-migration-tool" \
-Didam.api.url="https://idam-api.demo.platform.hmcts.net" \
-Didam.client.id="probate" \
-Didam.client.secret="staSwA5Hu6as6upra8ew3upeq2drUbup" \
-Didam.client.redirect_uri="https://probate-frontend-demo.service.core-compute-demo.internal/oauth2/callback" \
-Dcore_case_data.api.url="http://ccd-data-store-api-demo.service.core-compute-demo.internal" \
-Didam.s2s-auth.url="http://rpe-service-auth-provider-demo.service.core-compute-demo.internal" \
-Dprd.organisations.url="http://rd-professional-api-demo.service.core-compute-demo.internal" \
-Didam.s2s-auth.microservice="probate_backend" \
-Didam.s2s-auth.totp_secret="2MCCIH6ATQDXLBEH" \
-Dmigration.idam.username="ProbateSchedulerDEMO@gmail.com" \
-Dmigration.idam.password="Pa55word11" \
-Dmigration.caseType="GrantOfRepresentation" \
-Dlogging.level.root="ERROR" \
-Dlogging.level.uk.gov.hmcts.reform="INFO" \
-Dfeign.client.config.default.connectTimeout="60000" \
-Dfeign.client.config.default.readTimeout="60000" \
-Ddefault.thread.limit=1 \
-Ddefault.query.size=1 \
build/libs/ccd-case-migration.jar
