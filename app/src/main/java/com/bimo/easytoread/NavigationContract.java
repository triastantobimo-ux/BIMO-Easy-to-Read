package com.bimo.easytoread;

import java.util.EnumSet;

public final class NavigationContract {
    public enum Screen {
        HOME,
        DOCUMENTS,
        SCAN,
        TOOLS,
        OCR,
        SCAN_REVIEW,
        OCR_WORKSPACE,
        PDF_WORKSPACE,
        TOOL_JOB
    }

    private NavigationContract() {}

    public static boolean isAllowed(Screen from, Screen to) {
        if (from == null || to == null) return false;
        EnumSet<Screen> global = EnumSet.of(
                Screen.HOME,
                Screen.DOCUMENTS,
                Screen.SCAN,
                Screen.TOOLS,
                Screen.OCR
        );
        if (global.contains(from) && global.contains(to)) return true;
        if (from == Screen.SCAN && to == Screen.SCAN_REVIEW) return true;
        if (from == Screen.SCAN_REVIEW) {
            return to == Screen.SCAN || to == Screen.OCR_WORKSPACE
                    || to == Screen.PDF_WORKSPACE || to == Screen.DOCUMENTS;
        }
        if ((from == Screen.SCAN || from == Screen.OCR || from == Screen.DOCUMENTS
                || from == Screen.PDF_WORKSPACE) && to == Screen.OCR_WORKSPACE) return true;
        if (from == Screen.DOCUMENTS && to == Screen.PDF_WORKSPACE) return true;
        if ((from == Screen.DOCUMENTS || from == Screen.PDF_WORKSPACE)
                && to == Screen.TOOL_JOB) return true;
        if (from == Screen.TOOL_JOB) {
            return to == Screen.PDF_WORKSPACE || to == Screen.DOCUMENTS || to == Screen.TOOLS;
        }
        if (from == Screen.PDF_WORKSPACE) {
            return to == Screen.DOCUMENTS || to == Screen.OCR || to == Screen.TOOLS;
        }
        if (from == Screen.OCR_WORKSPACE) {
            return to == Screen.OCR || to == Screen.SCAN || to == Screen.DOCUMENTS;
        }
        return false;
    }

    public static String[] globalNavigation() {
        return new String[] { "Beranda", "Dokumen", "Pindai", "Alat PDF", "OCR" };
    }

    public static String[] scanModes() {
        return new String[] {
                "Dokumen tunggal",
                "Batch halaman"
        };
    }

    public static String[] workspaceTabs() {
        return new String[0];
    }

    public static String[] workspaceActions(boolean tableDetected) {
        return tableDetected
                ? new String[] { "Salin", "Bagikan", "Export", "Excel" }
                : new String[] { "Salin", "Bagikan", "Export" };
    }
}
