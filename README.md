# GPXLab launcher

Run `com.sdcote.gpxlab.MainApp` with JavaFX and the project's runtime dependencies on the classpath. As in Navista, startup requires the JVM system property `vault.password`; configure it in the launch environment rather than storing a password in the project.

The launcher reads `.gpxlab` in the working directory and writes it on normal exit. File → Settings edits configuration and applies logging changes immediately. The `Logger` section accepts `categories` (an array or delimiter-separated string); the default categories are INFO, NOTICE, WARN, ERROR and FATAL. `vaultfile` defaults to `gpxlab.vault`; changing it takes effect on the next launch.

Add future tasks to the command list in `LauncherApp`. Each command runs in the JavaFX process, and its action should open the task's interface on the JavaFX application thread.