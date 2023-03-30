package walker;

import boundedbuffer.BoundedBufferMap;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * A walker is an object that walks a directory and prints the longest line in each file.
 */
public class SimpleWalker implements Walker {
    private final Path directory;
    private final int maxFiles;
    private final int numIntervals;
    private final int maxLines;
    private final int intervalLength;
    private final BoundedBufferMap<Integer,List<Path>> distribution;


    public SimpleWalker(Path dir, int maxFiles, int numIntervals, int maxLength, BoundedBufferMap<Integer,List<Path>> distribution) {
        this.directory = dir;
        this.maxFiles = maxFiles;
        this.numIntervals = numIntervals;
        this.maxLines = maxLength;
        this.intervalLength = maxLength / numIntervals;
        this.distribution = distribution;
    }

    @Override
    public void walk() {

        Thread thread = new Thread(() -> {
            try {
                walkRec(this.directory);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        thread.start();
        //check that all threads have finished

//        Thread thread2 = new Thread(() -> {
//            try {
//                while (true) {
//                    this.distribution.close();
//                    Thread.sleep(1000);
//                    this.distribution.open();
//
//                }
//
//            } catch (InterruptedException e) {
//                throw new RuntimeException(e);
//            }
//        });
//        thread2.start();
//
        try {
            thread.join();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        System.out.println("\nThe " + this.maxFiles + " files with the highest number of lines are:");
        this.printMaxFiles();

        System.out.println("\nThe distribution of files is:");
        this.printDistribution();
    }

    /**
     * Recursive method to walk the directory
     * @param directory the directory to walk
     */
    private void walkRec(Path directory) throws IOException {
        System.out.println("Walking " + directory);
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (Path path : stream) {
                int numberOfLines;
                if (Files.isRegularFile(path) && path.getFileName().toString().endsWith(".java")) {
                    try (Stream<String> fileStream = Files.lines(path)) {
                        numberOfLines = (int) fileStream.count();
                    }
                    int interval = getInterval(numberOfLines);
                    if( this.distribution.containsKey(interval)) {
                        this.distribution.get(interval).add(path);
                    } else {
                        List<Path> list = new ArrayList<>();
                        list.add(path);
                        this.distribution.put(interval, list);
                    }
                } else if (Files.isDirectory(path) && !Files.isHidden(path)) {
                    Thread thread = new Thread(() -> {
                        try {
                            walkRec(path);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
                    thread.start();
                    try {
                        thread.join();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Get the interval of the file
     * If the file has more lines than the max lines, it will be in a new interval that goes from the max lines to infinity
     * @param numberOfLines the max length of the file
     * @return the interval
     */
    private Integer getInterval(int numberOfLines) {
        if(numberOfLines > this.maxLines) {
            return this.numIntervals;
        }
        return numberOfLines / (this.maxLines / this.numIntervals);
    }

    public void printMaxFiles() {
        this.distribution.getMap()
                .values()
                .stream()
                .flatMap(List::stream)
                .limit(this.maxFiles).toList()
                .forEach(System.out::println);
    }

    public void printDistribution() {
        IntStream.range(0, this.numIntervals)
                .map(i -> i * this.intervalLength)
                .forEach(start -> {
                    int end = (start + this.intervalLength - 1);
                    int interval = getInterval(end);
                    List<Path> list = this.distribution.getMap().getOrDefault(interval, Collections.emptyList());
                    System.out.println("[" + start + "," + end + "]: " + list.size());
                });
    }
}