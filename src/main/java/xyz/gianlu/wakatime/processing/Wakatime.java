package xyz.gianlu.wakatime.processing;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import processing.app.Mode;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Wakatime {
    static final Logger LOG = Logger.getLogger(Wakatime.class.getName());
    private static final String VERSION = Wakatime.class.getPackage().getImplementationVersion();
    private static final long FREQUENCY = 2 * 60; // Max secs between heartbeats for continuous coding
    private static final int QUEUE_TIMEOUT_SECONDS = 30;
    private static boolean DEBUG = true;
    private final String IDE_NAME = "Processing";
    private String lastFile = null;
    private long lastTime = 0;
    private ConcurrentLinkedQueue<Heartbeat> heartbeatsQueue = new ConcurrentLinkedQueue<>();
    private ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public Wakatime(@NotNull Component parent) {
        LOG.info("Initializing Wakatime plugin v" + VERSION + " (https://wakatime.com/)");

        setupDebugging();
        setLoggingLevel();

        Dependencies.configureProxy();
        checkApiKey(parent);

        if (Dependencies.isPythonInstalled()) {
            checkCore();
            setupQueueProcessor();
            checkDebug();
            LOG.info("Finished initializing Wakatime plugin");
        } else {
            LOG.info("Python not found, downloading python...");

            // download and install python
            Dependencies.installPython();

            if (Dependencies.isPythonInstalled()) {
                LOG.info("Finished installing python...");

                checkCore();
                setupQueueProcessor();
                checkDebug();
                LOG.info("Finished initializing Wakatime plugin");
            } else {
                System.err.println("Wakatime requires Python to be installed.\nYou can install it from https://www.python.org/downloads/\nAfter installing Python, restart your IDE.");
            }
        }
    }

    private static void setupDebugging() {
        String debug = ConfigFile.get("settings", "debug");
        Wakatime.DEBUG = debug != null && debug.trim().equals("true");
    }

    private static void setLoggingLevel() {
        if (Wakatime.DEBUG) {
            LOG.setLevel(Level.ALL);
            LOG.config("Logging level set to ALL");
        } else {
            LOG.setLevel(Level.INFO);
        }
    }

    @NotNull
    private static String obfuscateKey(@NotNull String key) {
        if (key.isEmpty()) return "";
        return key.length() > 4 ? "XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXX" + key.substring(key.length() - 4) : key;
    }

    @NotNull
    private static String[] obfuscateKey(@NotNull String[] cmds) {
        ArrayList<String> newCmds = new ArrayList<>();
        String lastCmd = "";
        for (String cmd : cmds) {
            if (lastCmd.equals("--key")) newCmds.add(obfuscateKey(cmd));
            else newCmds.add(cmd);
            lastCmd = cmd;
        }

        return newCmds.toArray(new String[0]);
    }

    @NotNull
    private static String getApiKey() {
        String apiKey = ConfigFile.get("settings", "api_key");
        if (apiKey == null) apiKey = "";
        return apiKey;
    }

    @NotNull
    private static String getObfuscatedApiKey() {
        return obfuscateKey(getApiKey());
    }

    private static boolean isApiKeyValid(@Nullable String key) {
        if (key == null || key.isEmpty()) return false;

        try {
            UUID.fromString(key);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private static long getCurrentTimestamp() {
        return System.currentTimeMillis() / 1000;
    }

    void showApiKeyPrompt(@NotNull Component parent) {
        String newKey = (String) JOptionPane.showInputDialog(parent, "Set Wakatime API key",
                "Wakatime API key", JOptionPane.QUESTION_MESSAGE, null, null, getApiKey());

        if (isApiKeyValid(newKey)) ConfigFile.set("settings", "api_key", newKey);
    }

    void appendHeartbeat(String project, String path, Mode mode, boolean isWrite) {
        if (!isWrite && path.equals(lastFile) && !enoughTimePassed())
            return;

        lastFile = path;
        lastTime = System.currentTimeMillis();

        String language;
        switch (mode.getDefaultExtension()) {
            case "pde":
            case "java":
                language = "Java";
                break;
            case "pyde":
                language = "Python";
                break;
            case "js":
                language = "JavaScript";
                break;
            case "coffee":
                language = "CoffeeScript";
                break;
            default:
                language = "Unknown";
                break;
        }

        heartbeatsQueue.add(new Heartbeat(path, lastTime, isWrite, project, language));
    }

    private void processHeartbeatQueue() {
        Heartbeat heartbeat = heartbeatsQueue.poll();
        if (heartbeat == null)
            return;

        List<Heartbeat> extraHeartbeats = new ArrayList<>();
        while (true) {
            Heartbeat h = heartbeatsQueue.poll();
            if (h == null) break;
            extraHeartbeats.add(h);
        }

        sendHeartbeat(heartbeat, extraHeartbeats);
    }

    private void sendHeartbeat(Heartbeat heartbeat, List<Heartbeat> extraHeartbeats) {
        String[] cmd = buildCliCommand(heartbeat, extraHeartbeats);
        LOG.config("Executing CLI: " + Arrays.toString(obfuscateKey(cmd)));

        try {
            Process process = Runtime.getRuntime().exec(cmd);
            if (extraHeartbeats.size() > 0) {
                JsonArray array = new JsonArray(extraHeartbeats.size());
                for (Heartbeat h : extraHeartbeats) {
                    JsonObject obj = new JsonObject();
                    array.add(obj);

                    obj.addProperty("entity", h.entity);
                    obj.addProperty("timestamp", h.timestamp);
                    obj.addProperty("is_write", h.isWrite);
                    if (h.project != null) obj.addProperty("project", h.project);
                    if (h.language != null) obj.addProperty("language", h.language);
                }

                String json = array.toString();
                LOG.config(json);
                try (BufferedWriter stdin = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()))) {
                    stdin.write(json);
                    stdin.write("\n");
                    stdin.flush();
                } catch (IOException e) {
                    LOG.log(Level.WARNING, null, e);
                }
            }

            if (Wakatime.DEBUG) {
                BufferedReader stdout = new BufferedReader(new InputStreamReader(process.getInputStream()));
                BufferedReader stderr = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                process.waitFor();

                String s;
                while ((s = stdout.readLine()) != null)
                    LOG.config(s);

                while ((s = stderr.readLine()) != null)
                    LOG.config(s);

                LOG.config("Command finished with return value: " + process.exitValue());
            }
        } catch (Exception ex) {
            LOG.log(Level.WARNING, null, ex);
        }
    }

    @NotNull
    private String[] buildCliCommand(@NotNull Heartbeat heartbeat, @NotNull List<Heartbeat> extraHeartbeats) {
        ArrayList<String> cmd = new ArrayList<>();
        cmd.add(Dependencies.getPythonLocation());
        cmd.add(Dependencies.getCLILocation());
        cmd.add("--entity");
        cmd.add(heartbeat.entity);
        cmd.add("--time");
        cmd.add(String.valueOf(heartbeat.timestamp));
        cmd.add("--key");
        cmd.add(getApiKey());

        if (heartbeat.project != null) {
            cmd.add("--project");
            cmd.add(heartbeat.project);
        }

        if (heartbeat.language != null) {
            cmd.add("--language");
            cmd.add(heartbeat.language);
        }

        cmd.add("--plugin");
        cmd.add(IDE_NAME + " processing-wakatime/" + VERSION);

        if (heartbeat.isWrite)
            cmd.add("--write");

        if (extraHeartbeats.size() > 0)
            cmd.add("--extra-heartbeats");

        return cmd.toArray(new String[0]);
    }

    private boolean enoughTimePassed() {
        return lastTime + FREQUENCY < getCurrentTimestamp();
    }

    private void checkCore() {
        if (!Dependencies.isCLIInstalled()) {
            LOG.info("Downloading and installing wakatime-cli...");
            Dependencies.installCLI();
            LOG.info("Finished downloading and installing wakatime-cli.");
        } else if (Dependencies.isCLIOld()) {
            LOG.info("Upgrading wakatime-cli...");
            Dependencies.upgradeCLI();
            LOG.info("Finished upgrading wakatime-cli.");
        } else {
            LOG.info("wakatime-cli is up to date.");
        }

        LOG.config("CLI location: " + Dependencies.getCLILocation());
    }

    private void checkApiKey(@NotNull Component parent) {
        if (!isApiKeyValid(getApiKey())) showApiKeyPrompt(parent);
        LOG.config("Api Key: " + getObfuscatedApiKey());
    }

    private void setupQueueProcessor() {
        scheduler.scheduleAtFixedRate(this::processHeartbeatQueue, QUEUE_TIMEOUT_SECONDS, QUEUE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private void checkDebug() {
        if (Wakatime.DEBUG)
            System.err.println("Running Wakatime in DEBUG mode. Your IDE may be slow when saving or editing files.");
    }
}
