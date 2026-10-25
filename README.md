# Parallel Task Runner

[![Quality gate status](https://sonarcloud.io/api/project_badges/measure?project=alaugks_pkg-taskrunner&metric=alert_status)](https://sonarcloud.io/summary/overall?id=alaugks_pkg-taskrunner)

A small library that runs multiple tasks in parallel. It works with
plain Java and integrates easily with Spring, but has no Spring dependency.

`TaskRunner` takes a list of `TaskRunnerInterface` tasks (for example all
Spring-managed beans) and executes them concurrently on a fixed thread pool.
Exceptions thrown inside a task are wrapped and propagated back to the caller,
and each worker thread is renamed to `task-<ClassName>-<ThreadId>` to make logs
easier to read.

**The interface is the filter.** Each task implements a marker interface that
says when or in which context it is responsible (for example `NoonTask` or
`MidnightTask`). `runAll(MidnightTask.class)` runs exactly the tasks
implementing `MidnightTask` and skips all others. This way you hand every task
to the runner once and decide per call which group runs.

## Table of Contents

- [Requirements](#requirements)
- [Getting the code](#getting-the-code)
  - [Try it via Maven Central snapshots](#try-it-via-maven-central-snapshots)
  - [Production: copy the source](#production-copy-the-source)
- [Usage](#usage)
  - [1. Define a marker interface for a group of tasks](#1-define-a-marker-interface-for-a-group-of-tasks)
  - [2. Implement tasks](#2-implement-tasks)
- [Usage without Spring](#usage-without-spring)
- [Usage with Spring](#usage-with-spring)
  - [Tasks as Spring beans](#tasks-as-spring-beans)
  - [Create the runner](#create-the-runner)
  - [Run tasks on a schedule](#run-tasks-on-a-schedule)
- [Behavior](#behavior)
- [Build](#build)
- [License](#license)

## Requirements

- Java 17+
- `slf4j-api` (the only dependency)
- Spring is optional. The Spring examples below use Spring Boot 4.x.

## Getting the code

> [!NOTE]
> **For testing only.** For the time being, the library is published as a
> `SNAPSHOT` to the Maven Central snapshot repository. Use it to try the
> library out. For production, copy the source code into your own project (see
> below).

### Try it via Maven Central snapshots

Snapshots are not resolved from Maven Central itself. Add the snapshot
repository and the dependency to your `pom.xml`. No authentication is required.

```xml
<repositories>
    <repository>
        <id>central-snapshots</id>
        <url>https://central.sonatype.com/repository/maven-snapshots/</url>
        <releases>
            <enabled>false</enabled>
        </releases>
        <snapshots>
            <enabled>true</enabled>
        </snapshots>
    </repository>
</repositories>

<dependency>
    <groupId>io.github.alaugks</groupId>
    <artifactId>task-runner</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

### Production: copy the source

The library consists of only three classes, `TaskRunner`,
`TaskRunnerInterface` and `TaskRunnerRuntimeException`. Copy them into your own project. Keep the license and the
`SPDX-License-Identifier` header (see [License](#license)).

## Usage

### 1. Define a marker interface for a group of tasks

Group related tasks behind a shared interface that extends
`TaskRunnerInterface`. The interface acts as a filter: the runner only runs
the tasks implementing the interface you pass to `runAll`. Use one interface
per group of tasks that should run together, for example per schedule.

```java
package com.example.tasks;

import io.github.alaugks.taskrunner.TaskRunnerInterface;

public interface NoonTask extends TaskRunnerInterface {
}

public interface MidnightTask extends TaskRunnerInterface {
}
```

You can define as many marker interfaces as you need and call
`runAll(Class<T> taskClass)` separately for each one.

### 2. Implement tasks

Each task implements the marker interface of the group it belongs to. The
`run()` method contains the task logic. `SendMessageTask` belongs to the
`NoonTask` group, `CleanupTempFilesTask` and `RefreshCacheTask` to the
`MidnightTask` group.

```java
package com.example.tasks;

public class SendMessageTask implements NoonTask {
    @Override
    public void run() {
        // ...
    }
}

public class CleanupTempFilesTask implements MidnightTask {
    @Override
    public void run() {
        // ...
    }
}

public class RefreshCacheTask implements MidnightTask {
    @Override
    public void run() {
        // ...
    }
}
```

## Usage without Spring

Pass all tasks to the builder and call `runAll(Class<T> taskClass)`
with the marker interface. The interface filters the tasks: every task
implementing it runs concurrently, all others are ignored. The call returns
once all matching tasks finish. The pool size is optional and defaults to 10.

```java
package com.example;

import io.github.alaugks.taskrunner.TaskRunner;
import io.github.alaugks.taskrunner.TaskRunnerInterface;
import com.example.tasks.CleanupTempFilesTask;
import com.example.tasks.MidnightTask;
import com.example.tasks.NoonTask;
import com.example.tasks.RefreshCacheTask;
import com.example.tasks.SendMessageTask;
import java.util.List;

public class Application {
    public static void main(String[] args) {
        List<TaskRunnerInterface> tasks = List.of(
            new SendMessageTask(),
            new CleanupTempFilesTask(),
            new RefreshCacheTask()
        );

        TaskRunner taskRunner = TaskRunner.builder(tasks)
            .poolSize(4) // optional, default: 10
            .build();

        // Runs only the tasks implementing MidnightTask
        // (CleanupTempFilesTask, RefreshCacheTask)
        taskRunner.runAll(MidnightTask.class);

        // Runs only the tasks implementing NoonTask (SendMessageTask)
        taskRunner.runAll(NoonTask.class);
    }
}
```

## Usage with Spring

### Tasks as Spring beans

Annotate each task with `@Component`:

```java
package com.example.tasks;

import org.springframework.stereotype.Component;

@Component
public class SendMessageTask implements NoonTask {
    @Override
    public void run() {
        // ...
    }
}

@Component
public class CleanupTempFilesTask implements MidnightTask {
    @Override
    public void run() {
        // ...
    }
}

@Component
public class RefreshCacheTask implements MidnightTask {
    @Override
    public void run() {
        // ...
    }
}
```

### Create the runner

Create the runner with its builder and expose it as a bean. The builder takes
all tasks; Spring injects every bean implementing
`TaskRunnerInterface`. The pool size is optional and defaults to 10.

```java
package com.example.tasks;

import io.github.alaugks.taskrunner.TaskRunner;
import io.github.alaugks.taskrunner.TaskRunnerInterface;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TaskRunnerConfig {

    @Bean
    public TaskRunner taskRunner(List<TaskRunnerInterface> tasks) {
        return TaskRunner.builder(tasks)
            .poolSize(4) // optional, default: 10
            .build();
    }
}
```

### Run tasks on a schedule

Inject `TaskRunner` and call `runAll(Class<T> taskClass)` with the marker
interface. The interface filters the beans: every bean implementing it runs
concurrently, all others are ignored. To run a task group on a schedule,
enable Spring scheduling and annotate a method with `@Scheduled`. The example
below runs `NoonTask` every day at 12:00 and `MidnightTask` every day at 00:00.

Enable scheduling once in your application. Then add a scheduled trigger that delegates to `TaskRunner`.

```java
package com.example.tasks;

import io.github.alaugks.taskrunner.TaskRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
public class ScheduledTasks {

    private final TaskRunner taskRunner;

    public ScheduledTasks(TaskRunner taskRunner) {
        this.taskRunner = taskRunner;
    }

    @Scheduled(cron = "0 0 12 * * *", zone = "Europe/Berlin")
    public void runNoon() {
        this.taskRunner.runAll(NoonTask.class);
    }

    @Scheduled(cron = "0 0 0 * * *", zone = "Europe/Berlin")
    public void runMidnight() {
        this.taskRunner.runAll(MidnightTask.class);
    }
}
```

To adjust the schedule per environment, move the cron expressions to
`application.yml`:

```yaml
tasks:
  noon:
    cron: "0 0 12 * * *"
  midnight:
    cron: "0 0 0 * * *"
```

and reference them in `@Scheduled`:

```java
@Scheduled(cron = "${tasks.noon.cron}", zone = "Europe/Berlin")
public void runNoon() {
    this.taskRunner.runAll(NoonTask.class);
}
```

## Behavior

- **Filtering:** `runAll(Class<T> taskClass)` runs every task that is an
  instance of `taskClass`. A task implementing several marker interfaces runs
  whenever one of them is passed.
- **Concurrency:** tasks run on a fixed thread pool. The size is set with
  `poolSize(int poolSize)` on the builder and defaults to 10.
- **Thread names:** each worker thread is named
  `task-<TaskClassName>-<ThreadId>` for the duration of the task.
- **Error handling:** `runAll` waits for the tasks in order. If a task
  throws, the exception is wrapped in a `TaskRunnerRuntimeException` (the
  original exception is its cause). If the calling thread is interrupted, the
  interrupt flag is restored and a `TaskRunnerRuntimeException` is thrown.
  Tasks that are already running are not cancelled.
- **Logging:** task start is logged at `DEBUG` via SLF4J under the
  `io.github.alaugks.taskrunner.TaskRunner` logger.

## Build

```bash
mvn clean install
```

## License

Licensed under the [Apache License, Version 2.0](LICENSE).
