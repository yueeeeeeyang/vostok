package yueyang.vostok;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import yueyang.vostok.file.VKFileConfig;
import yueyang.vostok.office.VKOfficeConfig;
import yueyang.vostok.office.job.VKOfficeJobExecutionResult;
import yueyang.vostok.office.job.VKOfficeJobFilter;
import yueyang.vostok.office.job.VKOfficeJobRequest;
import yueyang.vostok.office.job.VKOfficeJobStatus;
import yueyang.vostok.office.job.VKOfficeJobSubscription;
import yueyang.vostok.office.job.VKOfficeJobType;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VostokOfficeJobCallbackTest {
    @TempDir
    Path tempDir;

    @AfterEach
    void tearDown() {
        Vostok.Office.close();
        Vostok.File.close();
    }

    private void init() {
        Vostok.File.init(new VKFileConfig().baseDir(tempDir.toString()));
        Vostok.Office.init(new VKOfficeConfig()
                .officeJobEnabled(true)
                .officeJobWorkerThreads(2)
                .officeJobCallbackThreads(2));
    }

    @Test
    void testOnJobAndFilterAndOnceAndOffAndDeadLetter() throws Exception {
        init();

        CountDownLatch completedLatch = new CountDownLatch(1);
        CountDownLatch failedLatch = new CountDownLatch(1);
        CountDownLatch deadLetterLatch = new CountDownLatch(1);
        AtomicInteger onceCount = new AtomicInteger(0);
        AtomicInteger offCount = new AtomicInteger(0);

        Vostok.Office.onJobCompleted(n -> completedLatch.countDown());
        Vostok.Office.onJob(VKOfficeJobStatus.FAILED,
                VKOfficeJobFilter.builder().tag("need-fail").build(),
                n -> failedLatch.countDown());
        Vostok.Office.onceJob(VKOfficeJobStatus.SUCCEEDED, n -> onceCount.incrementAndGet());
        VKOfficeJobSubscription willOff = Vostok.Office.onJob(VKOfficeJobStatus.SUCCEEDED, n -> offCount.incrementAndGet());
        Vostok.Office.offJob(willOff);

        String s1 = Vostok.Office.submitJob(VKOfficeJobRequest.create(() -> VKOfficeJobExecutionResult.empty())
                .type(VKOfficeJobType.CUSTOM).tag("ok-1"));
        String s2 = Vostok.Office.submitJob(VKOfficeJobRequest.create(() -> VKOfficeJobExecutionResult.empty())
                .type(VKOfficeJobType.CUSTOM).tag("ok-2"));
        String f1 = Vostok.Office.submitJob(VKOfficeJobRequest.create(() -> {
            throw new RuntimeException("x");
        }).type(VKOfficeJobType.CUSTOM).tag("need-fail"));

        Vostok.Office.awaitJob(s1, 5000);
        Vostok.Office.awaitJob(s2, 5000);
        Vostok.Office.awaitJob(f1, 5000);

        assertTrue(completedLatch.await(3, TimeUnit.SECONDS));
        assertTrue(failedLatch.await(3, TimeUnit.SECONDS));
        assertEquals(1, onceCount.get());
        assertEquals(0, offCount.get());

        Vostok.Office.offAllJobs();
        Vostok.Office.onJobDeadLetter(n -> deadLetterLatch.countDown());
        String dead = Vostok.Office.submitJob(VKOfficeJobRequest.create(VKOfficeJobExecutionResult::empty)
                .type(VKOfficeJobType.CUSTOM).tag("dead"));
        Vostok.Office.awaitJob(dead, 5000);
        assertTrue(deadLetterLatch.await(3, TimeUnit.SECONDS));
    }
}
