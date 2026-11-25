package com.sdcote.gpxlab;

import coyote.commons.ExceptionUtil;
import coyote.commons.cfg.Config;
import coyote.commons.cfg.ConfigUtil;
import coyote.commons.log.Log;
import coyote.commons.vault.Vault;
import coyote.commons.vault.VaultBuilder;
import coyote.commons.vault.VaultEntry;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * This is a static class that contains static method for all components in this application to share.
 */
public class GPXLab {

    static final String DEFAULT_VAULT_FILE = "gpxlab.vault";
    private static final String DEFAULT_CFG_FILENAME = ".gpxlab";

    public static Config CONFIG;


    private static Vault vaultInstance = null;
    private static String instanceConfigFileName = null;



    /**
     * Open the secrets vault file with the given password.
     *
     * <p>This also sets the default vault instance for the runtime.</p>
     *
     * @param vaultFilename the name of the vault file to open.
     * @param password      the password to use to open the vault file
     */
    public static void loadVault(String vaultFilename, String password) {
        vaultInstance = getVault(vaultFilename, password);
    }


    /**
     * Open the secrets vault file with the default vault file and password in the system properties.
     *
     * <p>This also sets the default vault instance for the runtime.</p>
     *
     * @return the secrets vault for this project.
     */
    public static Vault getVault() {
        if (vaultInstance == null) {
            vaultInstance = getVault(DEFAULT_VAULT_FILE, System.getProperty("vault.password"));
        }
        return vaultInstance;
    }


    /**
     * Open the secrets vault file with the given password.
     *
     * <p>Used when a specialized vault is required.</p>
     *
     * @param vaultFilename the name of the vault file to open
     * @param password      the password to use to open the vault file
     * @return the secrets vault
     */
    public static Vault getVault(String vaultFilename, String password) {
        Vault retval = null;
        VaultBuilder builder = new VaultBuilder()
                .setProvider("Local")
                .setMethod("file")
                .setProperty("filename", vaultFilename)
                .setProperty("password", password);
        try {
            retval = builder.build();
        } catch (Exception e) {
            Log.error(String.format("Could not build the vault in '%s'. Did you forget the `vault.password` system property?", vaultFilename), e);
        }
        return retval;
    }


    /**
     * Retrieve the vault entry with the given name from the default vault for this project.
     *
     * @param entryName the name of the entry to retrieve
     * @return the entry in the current vault with that name or null if an entry with that name does not exist.
     */
    public static VaultEntry getVaultEntry(String entryName) {
        VaultEntry retval = null;
        Vault vault = getVault();
        if (vault != null) {
            retval = vault.getEntry(entryName);
        }
        return retval;
    }

    public static void readConfig() {
        readConfig(DEFAULT_CFG_FILENAME);
    }

    public static void readConfig(String cfgFilename) {
        instanceConfigFileName = cfgFilename;
        if (Files.notExists(Path.of(cfgFilename))) {
            try {
                Files.writeString(Path.of(cfgFilename), "{}", StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            } catch (FileAlreadyExistsException e) {
                // Another process created the configuration; read it below.
            } catch (IOException e) {
                Log.error(String.format("Could not create configuration file `%s` - %s", cfgFilename, e.getMessage()));
                CONFIG = new Config();
                return;
            }
        }
        CONFIG = ConfigUtil.readConfig(cfgFilename);
    }

    public static void saveConfig() {
        if (CONFIG != null && instanceConfigFileName != null) {
            try {
                ConfigUtil.writeConfig(CONFIG, instanceConfigFileName);
            } catch (IOException e) {
                Log.error(String.format("Could not save configuration file `%s` - %s", instanceConfigFileName, ExceptionUtil.stackTrace(e)));
            }
        }
    }
    
}
