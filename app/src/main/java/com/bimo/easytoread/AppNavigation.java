package com.bimo.easytoread;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.view.View;
import android.widget.Button;

final class AppNavigation {
    private AppNavigation() {}

    static void bind(Activity activity, NavigationContract.Screen active) {
        Button home = activity.findViewById(R.id.buttonNavHome);
        Button pdf = activity.findViewById(R.id.buttonNavPdf);
        Button scan = activity.findViewById(R.id.buttonNavScan);
        Button tools = activity.findViewById(R.id.buttonNavTools);
        Button ocr = activity.findViewById(R.id.buttonNavOcr);

        setSelected(activity, home, active == NavigationContract.Screen.HOME, false);
        setSelected(activity, pdf, active == NavigationContract.Screen.PDF, false);
        setSelected(activity, scan, active == NavigationContract.Screen.SCAN, true);
        setSelected(activity, tools, active == NavigationContract.Screen.TOOLS, false);
        setSelected(activity, ocr, active == NavigationContract.Screen.OCR, false);

        home.setOnClickListener(view -> {
            if (active == NavigationContract.Screen.HOME) return;
            activity.startActivity(new Intent(activity, HomeActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
            activity.finish();
        });
        pdf.setOnClickListener(view -> {
            if (active == NavigationContract.Screen.PDF) return;
            activity.startActivity(new Intent(activity, PdfLibraryActivity.class));
        });
        scan.setOnClickListener(view -> {
            if (active == NavigationContract.Screen.SCAN) return;
            activity.startActivity(MainActivity.createEntryIntent(activity, MainActivity.ENTRY_SCAN));
        });
        tools.setOnClickListener(view -> {
            if (active == NavigationContract.Screen.TOOLS) return;
            activity.startActivity(new Intent(activity, HubActivity.class)
                    .putExtra(HubActivity.EXTRA_DESTINATION, HubActivity.DESTINATION_TOOLS));
        });
        ocr.setOnClickListener(view -> {
            if (active == NavigationContract.Screen.OCR) return;
            activity.startActivity(MainActivity.createEntryIntent(activity, MainActivity.ENTRY_OCR));
        });
    }

    private static void setSelected(
            Activity activity,
            Button button,
            boolean selected,
            boolean primary
    ) {
        button.setSelected(selected);
        button.setTypeface(Typeface.create("sans-serif", selected ? Typeface.BOLD : Typeface.NORMAL));
        if (primary) {
            button.setBackgroundResource(R.drawable.bg_nav_primary);
            button.setTextColor(activity.getColor(R.color.accent_on_primary));
            button.setAlpha(selected ? 1f : 0.88f);
            return;
        }
        button.setBackgroundResource(selected
                ? R.drawable.bg_nav_selected
                : android.R.color.transparent);
        button.setTextColor(activity.getColor(selected
                ? R.color.accent_primary
                : R.color.text_primary));
        button.setAlpha(1f);
    }
}

