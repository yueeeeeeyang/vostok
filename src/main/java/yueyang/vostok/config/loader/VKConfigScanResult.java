package yueyang.vostok.config.loader;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public record VKConfigScanResult(List<VKConfigSource> defaultSources, Set<Path> watchRoots) {
}
