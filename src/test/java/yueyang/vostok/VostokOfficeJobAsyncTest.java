package yueyang.vostok;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import yueyang.vostok.file.VKFileConfig;
import yueyang.vostok.office.VKOfficeConfig;
import yueyang.vostok.office.job.VKOfficeJobExecutionResult;
import yueyang.vostok.office.job.VKOfficeJobQuery;
import yueyang.vostok.office.job.VKOfficeJobRequest;
import yueyang.vostok.office.job.VKOfficeJobStatus;
import yueyang.vostok.office.job.VKOfficeJobType;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VostokOfficeJobAsyncTest {
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
    void testSubmitGetAwaitList() {
        init();
        String jobId = Vostok.Office.submitJob(VKOfficeJobRequest.create(() -> {
            Vostok.File.write("jobs/success.txt", "ok");
            return VKOfficeJobExecutionResult.ofPath("jobs/success.txt");
        }).type(VKOfficeJobType.CONVERT).tag("tag-a"));
        var result = Vostok.Office.awaitJob(jobId, 5000);
        assertEquals(VKOfficeJobStatus.SUCCEEDED, result.status());
        assertEquals("jobs/success.txt", result.resultPath());
        assertEquals(VKOfficeJobStatus.SUCCEEDED, Vostok.Office.getJob(jobId).status());
        List<?> list = Vostok.Office.listJobs(VKOfficeJobQuery.create().tag("tag-a"));
        assertFalse(list.isEmpty());
    }

    @Test
    void testCancelJob() {
        init();
        String jobId = Vostok.Office.submitJob(VKOfficeJobRequest.create(() -> {
            for (int i = 0; i < 200; i++) {
                Thread.sleep(10);
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException("cancelled");
                }
            }
            return VKOfficeJobExecutionResult.empty();
        }).type(VKOfficeJobType.CUSTOM).tag("tag-cancel"));
        assertTrue(Vostok.Office.cancelJob(jobId));
        var result = Vostok.Office.awaitJob(jobId, 5000);
        assertEquals(VKOfficeJobStatus.CANCELLED, result.status());
    }
}
