package de.haumacher.phoneblock.scheduler;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Test case for {@link SchedulerService}.
 */
public class TestSchedulerService {

	/**
	 * Test that the {@link SchedulerService} can execute more parallel tasks than specified in its core pool size.
	 */
	@Test
	public void testExecute() {
		SchedulerService service = new SchedulerService();
		service.contextInitialized(null);
		
		AtomicInteger done = new AtomicInteger();
		AtomicBoolean fence = new AtomicBoolean();
		int cnt = SchedulerService.CORE_POOL_SIZE + 5;
		for (int n = 0; n < cnt; n++) {
			service.executor().execute(() -> {
				System.out.println("Waiting.");
				while (!fence.get()) {
					try {
						Thread.sleep(100);
					} catch (InterruptedException e) {
						Assertions.fail("Interrupted");
					}
				}
				int id = done.incrementAndGet();
				System.out.println("Completed " + id +".");
			});
		}
		
		AtomicBoolean check = new AtomicBoolean();
		service.executor().execute(() -> {
			System.out.println("Releasing.");
			check.set(true);
		});
		
		while (!check.get()) {
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				Assertions.fail("Interrupted");
			}
		}
		
		fence.set(true);
		
		while (done.get() < cnt) {
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				Assertions.fail("Interrupted");
			}
		}
		
		service.contextDestroyed(null);
	}
}
