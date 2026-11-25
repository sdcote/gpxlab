package com.sdcote.gpxlab;

import org.junit.jupiter.api.Test;

import java.nio.channels.OverlappingFileLockException;

import static org.junit.jupiter.api.Assertions.*;

class ErrorDialogTest {
    @Test
    void recognizesLockWithoutMessage() {
        assertTrue(ErrorDialog.determineRootCause(new OverlappingFileLockException()).contains("locked"));
    }

    @Test
    void formatsEntireExceptionChain() {
        var exception = new IllegalStateException("outer", new IllegalArgumentException("inner"));
        String trace = ErrorDialog.formatStackTrace(exception);
        assertTrue(trace.contains("outer"));
        assertTrue(trace.contains("inner"));
        assertEquals("", ErrorDialog.formatStackTrace(null));
    }
}