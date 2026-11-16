package com.performance.platform.application.ports.in;

import com.performance.platform.application.schedule.ScheduledRun;

import java.util.List;

/** Liste les runs planifies (pending et historique recent). */
public interface ListScheduledRunsUseCase {

    List<ScheduledRun> list(int limit);
}
