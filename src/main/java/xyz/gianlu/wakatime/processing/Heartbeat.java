package xyz.gianlu.wakatime.processing;

class Heartbeat {
    final String entity;
    final long timestamp;
    final boolean isWrite;
    final String project;
    final String language;

    Heartbeat(String entity, long timestamp, boolean isWrite, String project, String language) {
        this.entity = entity;
        this.timestamp = timestamp;
        this.isWrite = isWrite;
        this.project = project;
        this.language = language;
    }
}
