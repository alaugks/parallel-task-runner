// SPDX-License-Identifier: Apache-2.0

package io.github.alaugks.taskrunner;

/**
 * Thrown when a task run by the {@link TaskRunner} fails or is interrupted.
 */
public class TaskRunnerRuntimeException extends RuntimeException {

	/**
	 * @param cause the underlying failure
	 */
	public TaskRunnerRuntimeException(Throwable cause) {
		super(cause);
	}

}
