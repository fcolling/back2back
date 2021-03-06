package org.ogerardin.b2b.files;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;

/**
 * Recursively walks a filesystem directory and collects the {@link Path} of all files.
 * Note: we do not use {@link Files#walk} because of unsuitable error control, see
 * https://stackoverflow.com/questions/22867286/files-walk-calculate-total-size/
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class RecursivePathCollector extends SimpleFileVisitor<Path> {
    private static final Log logger = LogFactory.getLog(RecursivePathCollector.class);

    private final Map<Path, BasicFileAttributes> paths = new HashMap<>();
    private long size = 0;

    private final Path rootDir;

    public RecursivePathCollector(Path rootDir) {
        this.rootDir = rootDir;
    }

    public void walkTree() throws IOException {
        logger.info("Collecting all files under " + rootDir);
        Files.walkFileTree(rootDir,this);
        logger.info("Found " + paths.size() + " files");
    }

    @Override
    public FileVisitResult visitFile(Path path, BasicFileAttributes basicFileAttributes) {
        if (basicFileAttributes.isRegularFile()) {
            this.paths.put(path, basicFileAttributes);
            this.size += basicFileAttributes.size();
        }
        return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult visitFileFailed(Path path, IOException e) {
        logger.warn("Failed to visit file: " + path + " (" + e + ")");
        return FileVisitResult.CONTINUE;
    }
}
