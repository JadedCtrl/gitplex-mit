package com.gitplex.server.manager.impl;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.inject.Singleton;

import com.gitplex.launcher.loader.Listen;
import com.gitplex.server.event.lifecycle.SystemStopping;
import com.gitplex.server.manager.WorkExecutor;
import com.gitplex.server.util.concurrent.PrioritizedCallable;
import com.gitplex.server.util.concurrent.PrioritizedExecutor;
import com.gitplex.server.util.concurrent.PrioritizedRunnable;

@Singleton
public class DefaultWorkExecutor implements WorkExecutor {

	private final PrioritizedExecutor delegator = new PrioritizedExecutor(Runtime.getRuntime().availableProcessors());
	
	@Override
	public void execute(PrioritizedRunnable command) {
		delegator.execute(command);
	}

	@Override
	public <T> Future<T> submit(PrioritizedCallable<T> task) {
		return delegator.submit(task);
	}

	@Override
	public <T> Future<T> submit(PrioritizedRunnable task, T result) {
		return delegator.submit(task, result);
	}

	@Override
	public Future<?> submit(PrioritizedRunnable task) {
		return delegator.submit(task);
	}

	@Override
	public <T> List<Future<T>> invokeAll(Collection<? extends PrioritizedCallable<T>> tasks)
			throws InterruptedException {
		return delegator.invokeAll(tasks);
	}

	@Override
	public <T> List<Future<T>> invokeAll(
			Collection<? extends PrioritizedCallable<T>> tasks, long timeout, TimeUnit unit)
			throws InterruptedException {
		return delegator.invokeAll(tasks, timeout, unit);
	}

	@Override
	public <T> T invokeAny(Collection<? extends PrioritizedCallable<T>> tasks)
			throws InterruptedException, ExecutionException {
		return delegator.invokeAny(tasks);
	}

	@Override
	public <T> T invokeAny(Collection<? extends PrioritizedCallable<T>> tasks,
			long timeout, TimeUnit unit) throws InterruptedException,
			ExecutionException, TimeoutException {
		return delegator.invokeAny(tasks, timeout, unit);
	}

	@Listen
	public void on(SystemStopping event) {
		delegator.shutdown();
	}

	@Override
	public void remove(PrioritizedRunnable task) {
		delegator.remove(task);
	}

}
