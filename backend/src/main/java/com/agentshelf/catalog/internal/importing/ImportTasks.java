package com.agentshelf.catalog.internal.importing;

import com.github.kagkarlsson.scheduler.task.FailureHandler;
import com.github.kagkarlsson.scheduler.task.helper.OneTimeTask;
import com.github.kagkarlsson.scheduler.task.helper.Tasks;
import java.time.Duration;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The background task that runs imports. Task data is only the import job's ID. */
@Configuration(proxyBeanMethods = false)
class ImportTasks {

  static final String CSV_IMPORT = "catalog-csv-import";

  @Bean
  OneTimeTask<String> csvImportTask(ImportRunner runner) {
    return Tasks.oneTime(CSV_IMPORT, String.class)
        .onFailure(
            new FailureHandler.MaxRetriesFailureHandler<>(
                ImportRunner.MAX_ATTEMPTS,
                new FailureHandler.ExponentialBackoffFailureHandler<>(Duration.ofSeconds(10))))
        .execute((instance, context) ->
            runner.run(
                UUID.fromString(instance.getData()), context.getExecution().consecutiveFailures));
  }
}
