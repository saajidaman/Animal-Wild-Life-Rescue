package org.wildlife;
import java.io.IOException;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.Scanner;
import java.util.logging.*;
import org.wildlife.Service.IncidentManager;
import org.wildlife.Storage.CsvFileManager;
import org.wildlife.Ui.*;
import static java.nio.file.StandardOpenOption.*;
public final class Main {
    private Main() { }
    public static void main(String[] args) {
        try {
            Path data = Path.of("data");
            for (int i = 0; i < args.length; i++) {
                if (args[i].equals("--data") && i + 1 < args.length) data = Path.of(args[++i]);
                else throw new IllegalArgumentException("Usage: java -jar wildlife-rescue.jar [--data directory]");
            }
            Files.createDirectories(data);
            try (FileChannel channel = FileChannel.open(data.resolve("app.lock"), CREATE, WRITE);
                 FileLock lock = channel.tryLock()) {
                if (lock == null) throw new IOException("Another instance is already using this data directory.");
                Logger logger = Logger.getLogger("org.wildlife"); logger.setUseParentHandlers(false);
                FileHandler handler = new FileHandler(data.resolve("application.log").toString(), true);
                handler.setFormatter(new SimpleFormatter()); logger.addHandler(handler);
                try (Scanner scanner = new Scanner(System.in, java.nio.charset.StandardCharsets.UTF_8)) {
                    IncidentManager manager = new IncidentManager(new CsvFileManager(data));
                    new ConsoleApp(manager, scanner, data.resolve("reports")).run();
                } finally { logger.removeHandler(handler); handler.close(); }
            }
        } catch (Exception exception) {
            System.err.println("Cannot continue: " + exception.getMessage());
            System.err.println("Check the data files and path. Existing committed data has not been replaced by startup failure.");
            System.exit(1);
        }
    }
}