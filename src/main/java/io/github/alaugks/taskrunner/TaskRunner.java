// SPDX-License-Identifier: Apache-2.0

package io.github.alaugks.taskrunner;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Runs {@link TaskRunnerInterface} tasks in parallel on a fixed thread pool.
 */
public class TaskRunner {

	/** Default number of threads in the pool. */
	public static final int DEFAULT_POOL_SIZE = 10;

	private static final Logger logger = LoggerFactory.getLogger(TaskRunner.class);
	private final List<TaskRunnerInterface> tasks;
	private final ExecutorService executor;

	private TaskRunner(Builder builder) {
		this.tasks = builder.tasks;
		this.executor = Executors.newFixedThreadPool(builder.poolSize);
	}

	/**
	 * Creates a builder for a {@link TaskRunner}.
	 *
	 * @param tasks the registered tasks, must not be {@code null}
	 * @return a new builder
	 */
	public static Builder builder(List<TaskRunnerInterface> tasks) {
		return new Builder(tasks);
	}

	/**
	 * Runs all registered tasks of the given type in parallel and waits for them to finish.
	 *
	 * @param taskClass the type of tasks to run
	 * @param <T>       the task type
	 * @throws TaskRunnerRuntimeException if a task fails or the waiting thread is interrupted
	 */
	public <T extends TaskRunnerInterface> void runAll(Class<T> taskClass) throws TaskRunnerRuntimeException {
		List<? extends Future<?>> futures = this.tasks.stream()
			.filter(taskClass::isInstance)
			.map(taskClass::cast)
			.map(task -> executor.submit(() -> {
				String threadName = "task-" + task.getClass().getSimpleName() + "-" + Thread.currentThread().getId();
				Thread.currentThread().setName(threadName);

				logger.debug("Running in thread: {}", threadName);

				task.run();
			}))
			.toList();

		for (Future<?> future : futures) {
			try {
				future.get();
			} catch (ExecutionException e) {
				throw new TaskRunnerRuntimeException(e.getCause());
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new TaskRunnerRuntimeException(e);
			}
		}
	}

	/**
	 * Builder for {@link TaskRunner}.
	 */
	public static final class Builder {

		private final List<TaskRunnerInterface> tasks;
		private int poolSize = DEFAULT_POOL_SIZE;

		private Builder(List<TaskRunnerInterface> tasks) {
			this.tasks = Objects.requireNonNull(tasks, "tasks must not be null");
		}

		/**
		 * Sets the thread pool size.
		 *
		 * @param poolSize number of threads, at least 1
		 * @return this builder
		 * @throws IllegalArgumentException if {@code poolSize} is less than 1
		 */
		public Builder poolSize(int poolSize) {
			if (poolSize < 1) {
				throw new IllegalArgumentException("poolSize must be at least 1 but was " + poolSize);
			}
			this.poolSize = poolSize;
			return this;
		}

		/**
		 * Builds the {@link TaskRunner}.
		 *
		 * @return a new task runner
		 */
		public TaskRunner build() {
			return new TaskRunner(this);
		}
	}
}
