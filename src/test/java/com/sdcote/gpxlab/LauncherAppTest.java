package com.sdcote.gpxlab;

import coyote.commons.cfg.Config;
import coyote.commons.log.Log;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class LauncherAppTest {
    @Test
    void commandValidatesAndRunsAction() {
        assertThrows(IllegalArgumentException.class, () -> new LauncherCommand(null, () -> {}));
        assertThrows(IllegalArgumentException.class, () -> new LauncherCommand(" ", () -> {}));
        assertThrows(IllegalArgumentException.class, () -> new LauncherCommand("test", null));
        AtomicBoolean called = new AtomicBoolean();
        LauncherCommand command = new LauncherCommand("test", () -> called.set(true));
        command.action().run();
        assertTrue(called.get());
        assertNull(command.description());
        assertEquals("hello World!", HelloWorld.execute());
    }

    @Test
    void loggingSupportsNavistaFormats() {
        assertEquals(List.of(), LauncherApp.extractCategories(null));
        assertEquals(List.of("INFO", "WARN", "ERROR"), LauncherApp.extractCategories("INFO, WARN|ERROR"));
        assertEquals(List.of("INFO", "DEBUG"), LauncherApp.extractCategories(new Object[]{"INFO", null, "DEBUG"}));
        Config section = new Config();
        Config array = new Config();
        array.setArrayBias(true);
        array.add("WARN");
        array.add("ERROR");
        section.put("Category", array);
        assertEquals(List.of("WARN", "ERROR"), LauncherApp.extractCategories(section));
        assertEquals(List.of("WARN", "ERROR"), LauncherApp.extractCategories(array));
        Config config = new Config();
        LauncherApp.initLogging(config);
        assertNotNull(config.get("Logger"));
        assertTrue(Log.isLogging(Log.INFO));
        Config custom = new Config();
        custom.put("logger", "DEBUG");
        LauncherApp.initLogging(custom);
        assertFalse(Log.isLogging(Log.INFO));
        LauncherApp.initLogging(null);
    }

    @Test
    void missingConfigurationIsCreatedOnRead(@TempDir Path directory) {
        Path path = directory.resolve("settings.json");
        assertDoesNotThrow(() -> GPXLab.readConfig(path.toString()));
        assertNotNull(GPXLab.CONFIG);
        assertTrue(Files.isRegularFile(path));
        GPXLab.readConfig(path.toString());
        assertNotNull(GPXLab.CONFIG);
    }

    @Test
    void configurationCreationFailureUsesEmptyConfig(@TempDir Path directory) {
        Path path = directory.resolve("missing").resolve("settings.json");
        assertDoesNotThrow(() -> GPXLab.readConfig(path.toString()));
        assertNotNull(GPXLab.CONFIG);
        assertFalse(Files.exists(path));
    }

    @Test
    void configurationPersistsOnStop(@TempDir Path directory) {
        String path = directory.resolve("settings.json").toString();
        GPXLab.readConfig(path);
        GPXLab.CONFIG.put("test", "value");
        new LauncherApp().stop();
        GPXLab.readConfig(path);
        assertEquals("value", GPXLab.CONFIG.getString("test"));
    }

    @Test
    void missingVaultPasswordIsRejected() {
        String original = System.getProperty("vault.password");
        try {
            System.clearProperty("vault.password");
            Config config = new Config();
            assertThrows(IllegalStateException.class, () -> LauncherApp.openVault(config));
            assertEquals("gpxlab.vault", config.getString("vaultfile"));
        } finally {
            if (original != null) System.setProperty("vault.password", original);
        }
    }
}