# GPXLab launcher

Run `com.sdcote.gpxlab.MainApp` with JavaFX and the project's runtime dependencies on the classpath. As in Navista, startup requires the JVM system property `vault.password`; configure it in the launch environment rather than storing a password in the project.

The launcher reads `.gpxlab` in the working directory and writes it on normal exit. File → Settings edits configuration and applies logging changes immediately. The `Logger` section accepts `categories` (an array or delimiter-separated string); the default categories are INFO, NOTICE, WARN, ERROR and FATAL. `vaultfile` defaults to `gpxlab.vault`; changing it takes effect on the next launch.

Add future tasks to the command list in `LauncherApp`. Each command runs in the JavaFX process, and its action should open the task's interface on the JavaFX application thread.

Choose **Sanitize GPX**, select a readable file and one or more sanitizers, then click **Execute**. Each file selection suggests a sibling `-sanitized.gpx` output; the output path can be edited. Existing output requires overwrite confirmation. Executed parameters are saved in `.gpxlab` and restored next time; canceling does not save changes. Selecting metadata removal also removes the `creator` attribute; because GPX 1.1 requires that attribute, this output is intentionally not strictly GPX 1.1 compliant. To add a sanitizer, implement `ISanitizer` and register it in `SanitizeGPX`.

Choose **GPX Simplifier** to reduce track points using minimum step distance, centerline tolerance, and elevation smoothing settings. Selecting an input suggests a `-simplified.gpx` output; you can change it and confirm before overwriting an existing file. Executed settings are remembered in `.gpxlab`; canceling does not save them.