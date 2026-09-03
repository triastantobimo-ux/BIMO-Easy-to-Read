package com.bimo.easytoread;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class NavigationContractTest {
    @Test
    public void preservesFiveItemGlobalNavigationWithScanCentered() {
        assertArrayEquals(new String[] {
                "Beranda",
                "PDF",
                "Pindai",
                "Alat",
                "OCR"
        }, NavigationContract.globalNavigation());
    }

    @Test
    public void preservesLockedScanModeOrder() {
        assertArrayEquals(new String[] {
                "Dokumen tunggal",
                "Batch halaman"
        }, NavigationContract.scanModes());
    }

    @Test
    public void ocrWorkspaceHasNoModeTabs() {
        assertArrayEquals(new String[0], NavigationContract.workspaceTabs());
    }

    @Test
    public void workspaceActionsAreAdaptiveWithoutDuplicateExportControls() {
        assertArrayEquals(
                new String[] { "Salin", "Bagikan", "Export" },
                NavigationContract.workspaceActions(false)
        );
        assertArrayEquals(
                new String[] { "Salin", "Bagikan", "Export", "Excel" },
                NavigationContract.workspaceActions(true)
        );
    }

    @Test
    public void workspaceIsContextualNotGlobal() {
        assertTrue(NavigationContract.isAllowed(
                NavigationContract.Screen.SCAN,
                NavigationContract.Screen.SCAN_REVIEW
        ));
        assertTrue(NavigationContract.isAllowed(
                NavigationContract.Screen.SCAN_REVIEW,
                NavigationContract.Screen.OCR_WORKSPACE
        ));
        assertTrue(NavigationContract.isAllowed(
                NavigationContract.Screen.PDF,
                NavigationContract.Screen.PDF_WORKSPACE
        ));
        assertTrue(NavigationContract.isAllowed(
                NavigationContract.Screen.OCR_WORKSPACE,
                NavigationContract.Screen.SCAN
        ));
    }
}

