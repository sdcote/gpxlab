package com.sdcote.gpxlab;

public record LauncherCommand(String name, String description, Runnable action) {
    public LauncherCommand {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Command name cannot be null or blank");
        }
        if (action == null) {
            throw new IllegalArgumentException("Command action cannot be null");
        }
    }

    public LauncherCommand(String name, Runnable action) {
        this(name, null, action);
    }
}