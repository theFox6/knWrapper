package com.theFox6.knWrapper;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

public class KNWrapper {
    public static Path installationFolder = Path.of(".");
    public static Path knFolder = installationFolder.resolve("KleinerNerd/");
    public static Path knData = knFolder.resolve("bin/.KleinerNerd/");
    public static Path instanceFile = knData.resolve("instance.properties");
    public static File updateFolder = new File("updates");
    public static File knErrorLog = new File("errorLog.txt");
    public static File knStandardLog = new File("kleinerNerdLog.txt");
    public static Path logFolder = Path.of("LogArchive");
    public static File unpackLog = new File("knUnpackLog.txt");
    public static Properties instanceProperties;

    //perhaps check github for new releases

    public static void main(String[] args) {
        List<WrapperArgument> parsed = parseArgs(args);
        if (parsed.contains(WrapperArgument.HELP)) {
            WrapperArgument.printHelp();
            return;
        }
        new KNWrapper().launch(parsed);
    }

    private static List<WrapperArgument> parseArgs(String[] args) {
        //perhaps also support arguments that take values
        List<WrapperArgument> parsed = new LinkedList<>();
        for (String arg : args) {
            if (arg.startsWith("--")) {
                WrapperArgument l = WrapperArgument.getLongOpt(arg.substring(2));
                if (l == null) {
                    System.err.println("unexpected long option \"" + arg.substring(2) + '"');
                    parsed.add(WrapperArgument.HELP);
                } else
                    parsed.add(l);
            } else if (arg.startsWith("-")) {
                WrapperArgument s = WrapperArgument.getShortOpt(arg.substring(1));
                if (s == null) {
                    System.err.println("unexpected short option \"" + arg.substring(1) + '"');
                    parsed.add(WrapperArgument.HELP);
                } else
                    parsed.add(s);
            } else {
                System.err.println("unexpected command line option \"" + arg + '"');
                parsed.add(WrapperArgument.HELP);
            }
        }
        return parsed;
    }

    public void launch(List<WrapperArgument> args) {
        if (!Files.exists(knFolder)) {
            System.out.println("KleinerNerd folder not found in working directory");
            if (!args.contains(WrapperArgument.INSTALL)) {
                System.err.println("pass --install to install");
                return;
            }
            if (!checkForUpdate()) {
                System.err.println("installation from updates folder failed");
                return;
            }
            if (!Files.exists(knFolder)) {
                System.err.println("KleinerNerd not installed, check if you placed the package in the updates folder");
                return;
            }
        }
        if (!Files.exists(knData)) {
            System.out.println("KleinerNerd data folder not found in KleinerNerd folder");
            if (!Files.exists(knFolder.resolve("bin/KleinerNerd")))
                return;
            System.out.println("proceeding with \"first\" launch");
        }
        // initially check KleinerNerd state
        KNResponse r = checkState();
        if (!r.allowsStart()) {
            System.err.println("KleinerNerd is \"" + r.name() + "\". Startup aborted.");
            return;
        }
        if (args.contains(WrapperArgument.UPDATE)) {
            if (!checkForUpdate()) {
                System.err.println("update failed");
                return;
            }
        }
        //TODO: restart after "quit" and not "shutdown", if too many times in a row "quit" do not restart
        do {
            r = rerun(args);
            if (r.shouldUpdate()) {
                if (checkForUpdate()) {
                    instanceProperties.setProperty("state", KNResponse.UPDATED.name());
                    try {
                        instanceProperties.store(Files.newBufferedWriter(instanceFile), "KleinerNerd instance properties");
                    } catch (IOException e) {
                        System.err.println("could not set to updated state");
                        e.printStackTrace();
                    }
                } else {
                    System.err.println("update failed");
                    return;
                }
            }
        } while (r.shouldRestart());
        System.out.println("quit in " + r.name() + " state");
    }

    private KNResponse checkState() {
        if (instanceProperties == null)
            instanceProperties = new Properties();
        if (!Files.exists(instanceFile))
            return KNResponse.EMPTY;
        try (BufferedReader r = Files.newBufferedReader(instanceFile)) {
            instanceProperties.load(r);
        } catch (IOException e) {
            System.err.println("couldn't read instance file");
            e.printStackTrace();
            return KNResponse.UNKNOWN;
        }
        if (instanceProperties.isEmpty())
            return KNResponse.EMPTY;
        String stateString = instanceProperties.getProperty("state");
        if (stateString == null)
            return KNResponse.UNKNOWN;
        try {
            return KNResponse.valueOf(stateString);
        } catch (IllegalArgumentException e) {
            System.err.println("could not find instance state \"" + stateString + '"');
            return KNResponse.UNKNOWN;
        }
    }

    private KNResponse rerun(List<WrapperArgument> args) {
        try {
            if (knStandardLog.exists()) {
                backupLogs(knStandardLog.lastModified());
            } else if (knErrorLog.exists()) {
                backupLogs(knErrorLog.lastModified());
            }
        } catch (IOException e) {
            System.err.println("log backup failed");
            return KNResponse.WRAPPER_ERROR;
        }
        //perhaps also support windows
        List<String> command = new LinkedList<>();
        command.add("./KleinerNerd");
        command.add("--no-colorize"); //since we redirect to files
        if (args.contains(WrapperArgument.FULLLOG))
            command.add("--fullog");
        if (args.contains(WrapperArgument.CHECKS))
            command.add("--checks");
        ProcessBuilder b = new ProcessBuilder(command);
        b.directory(knFolder.resolve("bin/").toFile());
        b.redirectError(knErrorLog);
        b.redirectOutput(knStandardLog);
        try {
            Process p = b.start();
            p.waitFor();
        } catch (IOException e) {
            System.out.println("error while trying to run KleinerNerd");
            e.printStackTrace();
        } catch (InterruptedException e) {
            System.err.println("interrupted");
            e.printStackTrace();
        }
        KNResponse r = checkState();
        if (r.isUnexpected()) {
            System.err.println("KleinerNerd terminated in unexpected state: " + r.name());
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                System.err.println("interrupted while waiting for termination");
                r = checkState();
                e.printStackTrace();
                return r;
            }
            r = checkState();
            System.err.println("After 10 seconds the state is: " + r.name());
            return r;
        }
        try {
            backupLogs(knStandardLog.lastModified());
        } catch (IOException e) {
            System.err.println("log backup failed");
            return KNResponse.WRAPPER_ERROR;
        }
        return r;
    }

    private void backupLogs(long timestamp) throws IOException {
        Instant mod;
        if (timestamp == 0) {
            System.err.println("no timestamp given for log backup, using current time instead");
            mod = Instant.now();
        } else
            mod = Instant.ofEpochMilli(timestamp);
        if (!Files.exists(logFolder))
            Files.createDirectories(logFolder);
        DateTimeFormatter format = DateTimeFormatter.ofPattern("uuuuMMdd_HHmmss").withZone(ZoneId.of("UTC"));
        String stdLogName = format.format(mod) + '_' + knStandardLog.getName();
        Path stdLogPath = logFolder.resolve(stdLogName);
        String errLogName = format.format(mod) + '_' + knErrorLog.getName();
        Path errLogPath = logFolder.resolve(errLogName);
        if (knStandardLog.exists())
            Files.move(knStandardLog.toPath(), stdLogPath);
        if (knErrorLog.exists())
            Files.move(knErrorLog.toPath(), errLogPath);
    }

    /**
     * checks for an update and unpacks/installs it
     * @return true if the update was successful
     */
    private boolean checkForUpdate() {
        if (!updateFolder.exists()) {
            if (!updateFolder.mkdir()) {
                System.err.println("updates folder does not exist and cannot be created");
                return false;
            }
            return true;
        }
        File[] updates = updateFolder.listFiles();
        if (updates == null) {
            System.err.println("could not list updates folder contents");
            return false;
        }
        if (updates.length == 0) {
            /* in case there is trouble without update
            if (expectUpdate)
                System.err.println("update expected but not found");
            */
            return true;
        }
        if (updates.length > 1) {
            //perhaps support incremental updating
            System.err.println("More than one update found. Not supported yet.");
            return false;
        }
        //perhaps check if it is a KleinerNerd
        //perhaps backup data
        //perhaps support data migration
        try {
            unpack(updates[0], installationFolder.toFile());
        } catch (IOException e) {
            System.err.println("could not unpack zip file: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
        if (!updates[0].delete()) {
            System.err.println("update was not deleted after unpacking");
        }
        return true;
    }

    private void unpack(File update, File targetFolder) throws IOException {
        // ZipInput stream could probably also do the job
        // perhaps support windows
        // TODO: support tar
        if (!update.getName().endsWith(".zip"))
            System.out.println("Only zip files supported for now.");
        ProcessBuilder b = new ProcessBuilder("unzip", "-o", update.getAbsolutePath());
        b.directory(targetFolder);
        b.redirectOutput(unpackLog);
        b.redirectError(unpackLog);
        Process p = b.start();
        try {
            p.waitFor();
        } catch (InterruptedException e) {
            System.err.println("interrupted while extracting update");
            throw new IOException("unpacking unfinished", e);
        }
    }
}
