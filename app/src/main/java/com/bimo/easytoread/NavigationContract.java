package com.bimo.easytoread;

import java.util.EnumSet;

public final class NavigationContract {
    public enum Screen {
        HOME,
        DOCUMENTS,
        SCAN,
        TOOLS,
        ACTIVITY,
        WORKSPACE
    }

    private NavigationContract() {}

    public static boolean isAllowed(Screen from, Screen to) {
        if (from == null || to == null) return false;
        if (from == Screen.WORKSPACE) {
            return to == Screen.SCAN || to == Screen.HOME;
        }
        if (to == Screen.WORKSPACE) {
            return from == Screen.SCAN || from == Screen.DOCUMENTS;
        }
        return EnumSet.of(
                Screen.HOME,
                Screen.DOCUMENTS,
                Screen.SCAN,
                Screen.TOOLS,
                Screen.ACTIVITY
        ).contains(from) && EnumSet.of(
                Screen.HOME,
                Screen.DOCUMENTS,
                Screen.SCAN,
                Screen.TOOLS,
                Screen.ACTIVITY
        ).contains(to);
    }

    public static String[] globalNavigation() {
        return new String[] { "Beranda", "Dokumen", "Pindai", "Alat", "Aktivitas" };
    }

    public static String[] scanModes() {
        return new String[] {
                "Dokumen",
                "Tabel / Excel",
                "Teks cepat",
                "Beberapa halaman"
        };
    }

    public static String[] workspaceTabs() {
        return new String[] { "Baca", "Edit", "Review" };
    }

    public static String[] workspaceActions(boolean tableDetected) {
        return tableDetected
                ? new String[] { "Salin", "Bagikan", "Export", "Excel" }
                : new String[] { "Salin", "Bagikan", "Export" };
    }
}
