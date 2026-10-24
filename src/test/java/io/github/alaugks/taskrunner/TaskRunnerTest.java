// SPDX-License-Identifier: Apache-2.0

package io.github.alaugks.taskrunner;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskRunnerTest {

	interface TestTask extends TaskRunnerInterface {}

	interface OtherTask extends TaskRunnerInterface {}

	/** Worker threads are named with the "task-" prefix. */
	@Test
	void test_runAll_setsCustomThreadName() {
		Set<String> threadNames = ConcurrentHashMap.newKeySet();

		TestTask task = () -> threadNames.add(Thread.currentThread().getName());

		TaskRunner runner = TaskRunner.builder(List.of(task)).build();
		runner.runAll(TestTask.class);

		assertThat(threadNames).as("Exactly one thread name should be captured").hasSize(1);
		assertThat(threadNames.iterator().next()).startsWith("task-");
	}

	/** An exception thrown by a task is wrapped in a TaskRunnerRuntimeException. */
	@Test
	void test_runAll_wrapsExceptionOfTask() {
		TestTask failingTask = () -> { throw new IllegalStateException("Boom"); };

		TaskRunner runner = TaskRunner.builder(List.of(failingTask)).build();

		assertThatThrownBy(() -> runner.runAll(TestTask.class))
			.isInstanceOf(TaskRunnerRuntimeException.class)
			.hasCauseInstanceOf(IllegalStateException.class)
			.cause().hasMessage("Boom");
	}

	/** An interrupted caller gets the interrupt flag restored and a TaskRunnerRuntimeException. */
	@Test
	void test_runAll_restoresInterruptFlagWhenInterrupted() {
		CountDownLatch release = new CountDownLatch(1);
		TestTask blockingTask = () -> {
			try {
				release.await();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		};

		TaskRunner runner = TaskRunner.builder(List.of(blockingTask)).build();

		Thread.currentThread().interrupt();
		try {
			assertThatThrownBy(() -> runner.runAll(TestTask.class))
				.isInstanceOf(TaskRunnerRuntimeException.class)
				.hasCauseInstanceOf(InterruptedException.class);

			assertThat(Thread.currentThread().isInterrupted()).as("Interrupt flag should be restored").isTrue();
		} finally {
			Thread.interrupted();
			release.countDown();
		}
	}

	/** The builder rejects a pool size below 1. */
	@Test
	void test_builder_rejectsInvalidPoolSize() {
		TaskRunner.Builder builder = TaskRunner.builder(List.of());

		assertThatThrownBy(() -> builder.poolSize(0)).isInstanceOf(IllegalArgumentException.class);
	}

	/** The builder rejects a null task list. */
	@Test
	void test_builder_rejectsNullTasks() {
		assertThatThrownBy(() -> TaskRunner.builder(null)).isInstanceOf(NullPointerException.class);
	}

	/** With a pool size of 1, never more than one task runs at a time. */
	@Test
	void test_runAll_respectsPoolSize() {
		AtomicInteger running = new AtomicInteger();
		AtomicInteger maxRunning = new AtomicInteger();

		TestTask task = () -> {
			maxRunning.accumulateAndGet(running.incrementAndGet(), Math::max);
			try {
				Thread.sleep(50);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			running.decrementAndGet();
		};

		TaskRunner runner = TaskRunner.builder(List.of(task, task, task))
			.poolSize(1)
			.build();
		runner.runAll(TestTask.class);

		assertThat(maxRunning.get()).isEqualTo(1);
	}

	/** With a sufficient pool size, all tasks run at the same time. */
	@Test
	void test_runAll_runsTasksInParallel() {
		int taskCount = 3;
		CountDownLatch allRunning = new CountDownLatch(taskCount);
		AtomicInteger completed = new AtomicInteger();

		// Each task only finishes once all tasks are running at the same time.
		TestTask task = () -> {
			allRunning.countDown();
			try {
				if (allRunning.await(5, TimeUnit.SECONDS)) {
					completed.incrementAndGet();
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		};

		TaskRunner runner = TaskRunner.builder(List.of(task, task, task))
			.poolSize(taskCount)
			.build();
		runner.runAll(TestTask.class);

		assertThat(completed.get()).as("All tasks should have run at the same time").isEqualTo(taskCount);
	}

	/** Only tasks implementing the given interface run; tasks of other interfaces are skipped. */
	@Test
	void test_runAll_runsOnlyTasksOfGivenInterface() {
		AtomicInteger testTaskRuns = new AtomicInteger();
		AtomicInteger otherTaskRuns = new AtomicInteger();

		TestTask testTask = testTaskRuns::incrementAndGet;
		OtherTask otherTask = otherTaskRuns::incrementAndGet;

		TaskRunner runner = TaskRunner.builder(List.of(testTask, otherTask)).build();
		runner.runAll(TestTask.class);

		assertThat(testTaskRuns.get()).isEqualTo(1);
		assertThat(otherTaskRuns.get()).as("Tasks of other interfaces must not run").isZero();
	}
}
