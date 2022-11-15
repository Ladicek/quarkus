package io.quarkus.scheduler.common.runtime;

import java.util.List;

import com.cronutils.model.CronType;

public interface SchedulerContext {

    CronType getCronType();

    List<ScheduledMethod> getScheduledMethods();

}
