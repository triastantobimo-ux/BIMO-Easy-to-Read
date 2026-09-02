package com.bimo.easytoread;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class NavigationContractTest {
    @Test
    public void preservesFiveItemGlobalNavigationWithScanCentered() {
        assertArrayEquals(new String[] {
                "Beranda",
                "Dokumen",
                "Pindai",
                "Alat",
                "Aktivitas"
        }, NavigationContract.globalNavigation());
    }

    @Test
    public void preservesLockedScanModeOrder() {
        assertArrayEquals(new String[] {
                "Dokumen",
                "Tabel / Excel",
                "Teks cepat",
                "Beberapa halaman"
        }, NavigationContract.scanModes());
    }

    @Test
    public void preservesLockedWorkspaceTabOrder() {
        assertArrayEquals(
                new String[] { "Baca", "Edit", "Review" },
                NavigationContract.workspaceTabs()
        );
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
                NavigationContract.Screen.WORKSPACE
        ));
        assertTrue(NavigationContract.isAllowed(
                NavigationContract.Screen.WORKSPACE,
                NavigationContract.Screen.SCAN
        ));
        assertFalse(NavigationContract.isAllowed(
                NavigationContract.Screen.WORKSPACE,
                NavigationContract.Screen.TOOLS
        ));
    }
}
