package org.nypl.simplified.tests.files;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.nypl.simplified.files.DirectoryUtilities;
import org.nypl.simplified.files.FileLocking;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The main file locking contract.
 */

public abstract class FileLockingContract {

  /**
   * Construct a new contract.
   */

  public FileLockingContract() {

  }

  protected abstract Logger logger();

  /**
   * Test that a lock can be obtained.
   */

  @Test
  public void testLockingSimple()
    throws Exception {
    final File tmp = DirectoryUtilities.directoryCreateTemporary();
    final File lock = new File(tmp, "lock.txt");

    final AtomicBoolean locked = new AtomicBoolean(false);

    FileLocking.withFileThreadLocked(
      lock, 1000L, () -> {
        locked.set(true);
      });

    Assertions.assertTrue(locked.get());
  }

  /**
   * Test that a lock on a file can be obtained by a given thread, and that if
   * the same thread cannot obtain another lock until it has released the
   * first.
   */

  @Test
  public void testLockingSelf()
    throws Exception {
    final File tmp = DirectoryUtilities.directoryCreateTemporary();
    final File lock = new File(tmp, "lock.txt");

    final AtomicBoolean failed = new AtomicBoolean(false);
    final AtomicInteger count = new AtomicInteger(0);

    final Logger logger = this.logger();
    logger.debug("attempting outer lock");
    FileLocking.withFileThreadLocked(
      lock, 1000L, () -> {
        count.set(1);

        try {
          logger.debug("attempting inner lock");
          FileLocking.withFileThreadLocked(
            lock, 1000L, () -> {
              logger.debug("called inner lock");
              count.set(3);
            });
        } catch (final IOException e) {
          logger.error("io error: ", e);
          failed.set(true);
        }

        logger.debug("finished inner lock");
        count.set(2);
      });
    logger.debug("finished outer lock");

    Assertions.assertEquals(Integer.valueOf(count.get()), Integer.valueOf(2));
    Assertions.assertEquals(Boolean.valueOf(failed.get()), Boolean.TRUE);
  }

  /**
   * Test that two threads cannot obtain a lock on the same file.
   */

  @Test
  public void testLockingOtherThread()
    throws Exception {
    final File tmp = DirectoryUtilities.directoryCreateTemporary();
    final File lock = new File(tmp, "lock.txt");
    final CountDownLatch latch = new CountDownLatch(1);
    final AtomicInteger count = new AtomicInteger(0);
    final Logger logger = this.logger();

    final Thread t1 = new Thread() {
      @Override
      public void run() {
        try {
          FileLocking.withFileThreadLocked(
            lock, 1000L, () -> {
              count.incrementAndGet();
              latch.countDown();

              try {
                Thread.sleep(1000L);
              } catch (InterruptedException e) {
                e.printStackTrace();
              }
            });
        } catch (final Exception e) {
          logger.error("error: ", e);
        }
      }
    };

    t1.start();
    latch.await(5L, TimeUnit.SECONDS);

    final Thread t2 = new Thread() {
      @Override
      public void run() {
        try {
          FileLocking.withFileThreadLocked(
            lock, 500L, () -> {
              count.incrementAndGet();
            });
        } catch (Exception e) {
          logger.error("error: ", e);
        }
      }
    };

    Assertions.assertEquals(Integer.valueOf(1), Integer.valueOf(count.get()));
  }
}
