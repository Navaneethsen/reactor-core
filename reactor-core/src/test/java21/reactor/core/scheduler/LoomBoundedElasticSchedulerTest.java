package reactor.core.scheduler;

import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.Disposable;
import reactor.test.util.RaceTestUtils;
import reactor.util.concurrent.Queues;

class LoomBoundedElasticSchedulerTest {

	Scheduler scheduler;

	@BeforeEach
	void setup() {
		scheduler = new LoomBoundedElasticScheduler(2,
				3,
				Thread.ofVirtual()
				      .name("loom", 0)
				      .factory());
		scheduler.init();
	}

	@AfterEach
	void teardown() {
		scheduler.dispose();
	}

	@Test
	public void ensuresTasksScheduling() throws InterruptedException {
		CountDownLatch latch = new CountDownLatch(1);

		Disposable disposable = scheduler.schedule(latch::countDown);

		Assertions.assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
		Assertions.assertThat(disposable.isDisposed()).isTrue();
	}

	@Test
	public void ensuresTasksDelayedScheduling() throws InterruptedException {
		CountDownLatch latch = new CountDownLatch(1);

		Disposable disposable = scheduler.schedule(latch::countDown, 200, TimeUnit.MILLISECONDS);

		Assertions.assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
		Assertions.assertThat(disposable.isDisposed()).isTrue();
	}

	@Test
	public void ensuresTasksDelayedZeroDelayScheduling() throws InterruptedException {
		CountDownLatch latch = new CountDownLatch(1);

		Disposable disposable = scheduler.schedule(latch::countDown, 0, TimeUnit.MILLISECONDS);

		Assertions.assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
		Assertions.assertThat(disposable.isDisposed()).isTrue();
	}

	@Test
	public void ensuresTasksPeriodicScheduling() throws InterruptedException {
		CountDownLatch latch = new CountDownLatch(10);

		Disposable disposable = scheduler.schedulePeriodically(latch::countDown,
				100,
				10,
				TimeUnit.MILLISECONDS);

		Assertions.assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
		Assertions.assertThat(disposable.isDisposed()).isFalse();
		disposable.dispose();
		Assertions.assertThat(disposable.isDisposed()).isTrue();
	}

	@Test
	public void ensuresTasksPeriodicZeroInitialDelayScheduling() throws InterruptedException {
		CountDownLatch latch = new CountDownLatch(10);

		Disposable disposable = scheduler.schedulePeriodically(latch::countDown,
				0,
				10,
				TimeUnit.MILLISECONDS);

		Assertions.assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
		Assertions.assertThat(disposable.isDisposed()).isFalse();
		disposable.dispose();
		Assertions.assertThat(disposable.isDisposed()).isTrue();
	}

	@Test
	public void ensuresTasksPeriodicWithInitialDelayAndInstantPeriodScheduling() throws InterruptedException {
		CountDownLatch latch = new CountDownLatch(10);

		Disposable disposable = scheduler.schedulePeriodically(latch::countDown,
				100,
				0,
				TimeUnit.MILLISECONDS);

		Assertions.assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
		Assertions.assertThat(disposable.isDisposed()).isFalse();
		disposable.dispose();
		Assertions.assertThat(disposable.isDisposed()).isTrue();
	}

	@Test
	public void ensuresTasksPeriodicWithZeroInitialDelayAndInstantPeriodScheduling() throws InterruptedException {
		CountDownLatch latch = new CountDownLatch(10);

		Disposable disposable = scheduler.schedulePeriodically(latch::countDown,
				0,
				0,
				TimeUnit.MILLISECONDS);

		Assertions.assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
		Assertions.assertThat(disposable.isDisposed()).isFalse();
		disposable.dispose();
		Assertions.assertThat(disposable.isDisposed()).isTrue();
	}

	@Test
	public void ensuresConcurrentTasksSchedulingWithinSingleWorker() throws InterruptedException {
		Queue<Object> queue = Queues.unboundedMultiproducer()
		                            .get();
		for (int i = 0; i < 100; i++) {
			CountDownLatch latch = new CountDownLatch(2);

			Scheduler.Worker worker = scheduler.createWorker();

			RaceTestUtils.race(() -> worker.schedule(() -> {
				queue.offer("1");
				queue.offer("1");
				queue.offer("1");
				latch.countDown();
			}), () -> worker.schedule(() -> {
				queue.offer("2");
				queue.offer("2");
				queue.offer("2");
				latch.countDown();
			}));

			Assertions.assertThat(latch.await(5, TimeUnit.SECONDS))
			          .isTrue();

			Object value1 = queue.poll();
			Assertions.assertThat(value1).isEqualTo(queue.poll());
			Assertions.assertThat(value1).isEqualTo(queue.poll());

			Object value2 = queue.poll();
			Assertions.assertThat(value2).isEqualTo(queue.poll());
			Assertions.assertThat(value2).isEqualTo(queue.poll());
			worker.dispose();
		}
	}

	@Test
	public void ensuresConcurrentDelayedTasksSchedulingSingleWorker() throws InterruptedException {
		Queue<Object> queue = Queues.unboundedMultiproducer()
		                              .get();
		for (int i = 0; i < 100; i++) {
			CountDownLatch latch = new CountDownLatch(3);

			Scheduler.Worker worker = scheduler.createWorker();

			RaceTestUtils.race(() -> worker.schedule(() -> {
				queue.offer("1");
				queue.offer("1");
				queue.offer("1");
				latch.countDown();
			}, 1, TimeUnit.MILLISECONDS), () -> worker.schedule(() -> {
				queue.offer("2");
				queue.offer("2");
				queue.offer("2");
				latch.countDown();
			}), () -> worker.schedule(() -> {
				queue.offer("3");
				queue.offer("3");
				queue.offer("3");
				latch.countDown();
			}, 1, TimeUnit.MILLISECONDS));

			Assertions.assertThat(latch.await(5, TimeUnit.SECONDS))
			          .isTrue();

			Object value1 = queue.poll();
			Assertions.assertThat(value1).isEqualTo(queue.poll());
			Assertions.assertThat(value1).isEqualTo(queue.poll());

			Object value2 = queue.poll();
			Assertions.assertThat(value2).isEqualTo(queue.poll());
			Assertions.assertThat(value2).isEqualTo(queue.poll());

			Object value3 = queue.poll();
			Assertions.assertThat(value3).isEqualTo(queue.poll());
			Assertions.assertThat(value3).isEqualTo(queue.poll());
			worker.dispose();
		}
	}

	@Test
	public void ensuresConcurrentPeriodicTasksSchedulingSingleWorker() throws InterruptedException {
		Queue<Object> queue = Queues.unboundedMultiproducer()
		                            .get();
		for (int i = 0; i < 100; i++) {
			CountDownLatch latch = new CountDownLatch(10);

			Scheduler.Worker worker = scheduler.createWorker();

			RaceTestUtils.race(() -> worker.schedulePeriodically(() -> {
				queue.offer("1");
				queue.offer("1");
				queue.offer("1");
				latch.countDown();
			}, 1, 0, TimeUnit.MILLISECONDS), () -> worker.schedule(() -> {
				queue.offer("2");
				queue.offer("2");
				queue.offer("2");
				latch.countDown();
			}, 1, TimeUnit.MILLISECONDS), () -> worker.schedulePeriodically(() -> {
				queue.offer("3");
				queue.offer("3");
				queue.offer("3");
				latch.countDown();
			}, 1, 1, TimeUnit.MILLISECONDS));

			Assertions.assertThat(latch.await(5, TimeUnit.SECONDS))
			          .isTrue();

			for (int j = 0; j < 10; j++) {
				Object value = queue.poll();
				Assertions.assertThat(value)
				          .isEqualTo(queue.poll());
				Assertions.assertThat(value)
				          .isEqualTo(queue.poll());
			}
			worker.dispose();
		}
	}

	@Test
	public void ensuresConcurrentWorkerTaskDisposure() throws InterruptedException {
		for (int i = 0; i < 100; i++) {
			CountDownLatch latch = new CountDownLatch(1);
			CountDownLatch latch2 = new CountDownLatch(1);

			Scheduler.Worker worker = scheduler.createWorker();
			worker.schedule(()-> {
				try {
					latch2.await();
				}
				catch (InterruptedException e) {
					throw new RuntimeException(e);
				}
			});
			Disposable disposable = worker.schedule(latch::countDown);
			RaceTestUtils.race(() -> worker.dispose(), () -> disposable.dispose());
			latch2.countDown();
			Assertions.assertThat(latch.getCount())
			          .isOne();
			Assertions.assertThat(worker.isDisposed()).isTrue();
			Assertions.assertThat(disposable.isDisposed()).isTrue();
		}
	}
}