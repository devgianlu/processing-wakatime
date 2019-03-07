package xyz.gianlu.wakatime.processing;

import java.io.*;

class ConfigFile {
    private static final String fileName = ".wakatime.cfg";
    private static String cachedConfigFile = null;

    private static String getConfigFilePath() {
        if (ConfigFile.cachedConfigFile == null) {
            if (System.getenv("WAKATIME_HOME") != null && !System.getenv("WAKATIME_HOME").trim().isEmpty()) {
                File folder = new File(System.getenv("WAKATIME_HOME"));
                if (folder.exists()) {
                    ConfigFile.cachedConfigFile = new File(folder, ConfigFile.fileName).getAbsolutePath();
                    Wakatime.LOG.config("Using $WAKATIME_HOME for config folder: " + ConfigFile.cachedConfigFile);
                    return ConfigFile.cachedConfigFile;
                }
            }

            ConfigFile.cachedConfigFile = new File(System.getProperty("user.home"), ConfigFile.fileName).getAbsolutePath();
            Wakatime.LOG.config("Using $HOME for config folder: " + ConfigFile.cachedConfigFile);
        }

        return ConfigFile.cachedConfigFile;
    }

    static String get(String section, String key) {
        String file = ConfigFile.getConfigFilePath();
        String val = null;
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String currentSection = "";
            String line = br.readLine();
            while (line != null) {
                if (line.trim().startsWith("[") && line.trim().endsWith("]")) {
                    currentSection = line.trim().substring(1, line.trim().length() - 1).toLowerCase();
                } else {
                    if (section.toLowerCase().equals(currentSection)) {
                        String[] parts = line.split("=");
                        if (parts.length == 2 && parts[0].trim().equals(key)) {
                            val = parts[1].trim();
                            br.close();
                            return val;
                        }
                    }
                }

                line = br.readLine();
            }
        } catch (IOException ex) { /* ignored */ }

        return val;
    }

    static void set(String section, String key, String val) {
        String file = ConfigFile.getConfigFilePath();
        StringBuilder contents = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String currentSection = "";
            String line = br.readLine();
            Boolean found = false;
            while (line != null) {
                if (line.trim().startsWith("[") && line.trim().endsWith("]")) {
                    if (section.toLowerCase().equals(currentSection) && !found) {
                        contents.append(key).append(" = ").append(val).append("\n");
                        found = true;
                    }

                    currentSection = line.trim().substring(1, line.trim().length() - 1).toLowerCase();
                    contents.append(line).append("\n");
                } else {
                    if (section.toLowerCase().equals(currentSection)) {
                        String[] parts = line.split("=");
                        String currentKey = parts[0].trim();
                        if (currentKey.equals(key)) {
                            if (!found) {
                                contents.append(key).append(" = ").append(val).append("\n");
                                found = true;
                            }
                        } else {
                            contents.append(line).append("\n");
                        }
                    } else {
                        contents.append(line).append("\n");
                    }
                }

                line = br.readLine();
            }

            if (!found) {
                if (!section.toLowerCase().equals(currentSection))
                    contents.append("[").append(section.toLowerCase()).append("]\n");

                contents.append(key).append(" = ").append(val).append("\n");
            }
        } catch (IOException e) {
            // cannot read config file, so create it
            contents = new StringBuilder();
            contents.append("[").append(section.toLowerCase()).append("]\n");
            contents.append(key).append(" = ").append(val).append("\n");
        }

        try (PrintWriter writer = new PrintWriter(file, "UTF-8")) {
            writer.print(contents.toString());
        } catch (FileNotFoundException | UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }
}
