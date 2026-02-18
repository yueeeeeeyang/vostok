package yueyang.vostok;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import yueyang.vostok.file.VKFileConflictStrategy;
import yueyang.vostok.file.VKFileWatchEventType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VostokFileAdvancedTest {
    @TempDir
    Path tempDir;

    @Test
    void testHashAndMkdirTypeAndRename() {
        Vostok.File.initLocal(tempDir.toString());
        Vostok.File.mkdirs("a");
        Vostok.File.mkdir("a/b");
        Vostok.File.write("a/b/f.txt", "abc");

        String md5 = Vostok.File.hash("a/b/f.txt", "MD5");
        assertEquals("900150983cd24fb0d6963f7d28e17f72", md5);
        assertTrue(Vostok.File.isFile("a/b/f.txt"));
        assertTrue(Vostok.File.isDirectory("a/b"));
        assertThrows(IllegalArgumentException.class, () -> Vostok.File.hash("a/b/f.txt", "NO_SUCH"));

        Vostok.File.rename("a/b/f.txt", "g.txt");
        assertFalse(Vostok.File.exists("a/b/f.txt"));
        assertTrue(Vostok.File.exists("a/b/g.txt"));
    }

    @Test
    void testWalkWithFilter() {
        Vostok.File.initLocal(tempDir.toString());
        Vostok.File.write("w/x.txt", "1");
        Vostok.File.write("w/y.log", "2");
        Vostok.File.write("w/sub/z.txt", "3");

        List<String> txt = Vostok.File.walk("w", true, f -> !f.directory() && f.path().endsWith(".txt"))
                .stream().map(i -> i.path().replace('\\', '/')).sorted().toList();
        assertEquals(List.of("w/sub/z.txt", "w/x.txt"), txt);

        List<String> direct = Vostok.File.walk("w", false).stream()
                .map(i -> i.path().replace('\\', '/')).sorted().toList();
        assertEquals(List.of("w/sub", "w/x.txt", "w/y.log"), direct);
    }

    @Test
    void testDeleteIfExistsAndDeleteRecursively() {
        Vostok.File.initLocal(tempDir.toString());
        Vostok.File.write("d/a.txt", "1");
        Vostok.File.write("d/sub/b.txt", "2");

        assertFalse(Vostok.File.deleteIfExists("d/missing.txt"));
        assertTrue(Vostok.File.deleteIfExists("d/a.txt"));
        assertFalse(Vostok.File.exists("d/a.txt"));

        assertTrue(Vostok.File.deleteRecursively("d"));
        assertFalse(Vostok.File.exists("d"));
        assertFalse(Vostok.File.deleteRecursively("d"));
    }

    @Test
    void testCopyDirAndMoveDirWithConflictStrategy() {
        Vostok.File.initLocal(tempDir.toString());
        Vostok.File.write("src/a.txt", "v1");
        Vostok.File.write("src/sub/b.txt", "v2");

        Vostok.File.copyDir("src", "dst", VKFileConflictStrategy.FAIL);
        assertEquals("v1", Vostok.File.read("dst/a.txt"));
        assertEquals("v2", Vostok.File.read("dst/sub/b.txt"));

        assertThrows(IllegalStateException.class, () -> Vostok.File.copyDir("src", "dst", VKFileConflictStrategy.FAIL));

        Vostok.File.write("src/a.txt", "new");
        Vostok.File.copyDir("src", "dst", VKFileConflictStrategy.SKIP);
        assertEquals("v1", Vostok.File.read("dst/a.txt"));

        Vostok.File.copyDir("src", "dst", VKFileConflictStrategy.OVERWRITE);
        assertEquals("new", Vostok.File.read("dst/a.txt"));

        Vostok.File.write("stage/x.txt", "x1");
        Vostok.File.write("target/x.txt", "old");
        Vostok.File.moveDir("stage", "target", VKFileConflictStrategy.SKIP);
        assertTrue(Vostok.File.exists("stage/x.txt"));
        assertEquals("old", Vostok.File.read("target/x.txt"));

        Vostok.File.moveDir("stage", "target", VKFileConflictStrategy.OVERWRITE);
        assertFalse(Vostok.File.exists("stage"));
        assertEquals("x1", Vostok.File.read("target/x.txt"));
    }

    @Test
    void testWatch() throws Exception {
        Vostok.File.initLocal(tempDir.toString());
        Vostok.File.mkdirs("watch");

        CountDownLatch latch = new CountDownLatch(1);
        List<VKFileWatchEventType> types = new CopyOnWriteArrayList<>();
        try (var handle = Vostok.File.watch("watch", e -> {
            types.add(e.type());
            latch.countDown();
        })) {
            Vostok.File.write("watch/a.txt", "1");
            assertTrue(latch.await(5, TimeUnit.SECONDS));
        }

        assertFalse(types.isEmpty());
        assertNotEquals(Set.of(VKFileWatchEventType.OVERFLOW), Set.copyOf(types));
    }

    @Test
    void testMoveDirFailWhenTargetExists() {
        Vostok.File.initLocal(tempDir.toString());
        Vostok.File.write("m1/a.txt", "1");
        Vostok.File.write("m2/a.txt", "2");
        assertThrows(IllegalStateException.class, () -> Vostok.File.moveDir("m1", "m2", VKFileConflictStrategy.FAIL));
    }

    @Test
    void testMkdirFailsWhenParentMissing() {
        Vostok.File.initLocal(tempDir.toString());
        assertThrows(RuntimeException.class, () -> Vostok.File.mkdir("x/y"));
    }
}
