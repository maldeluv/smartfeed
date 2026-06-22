package com.smartfeed.util;

import android.view.View;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public final class SystemBarInsets {

    private SystemBarInsets() {
    }

    public static void apply(View view, boolean top, boolean bottom) {
        int initialLeft = view.getPaddingLeft();
        int initialTop = view.getPaddingTop();
        int initialRight = view.getPaddingRight();
        int initialBottom = view.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(view, (target, windowInsets) -> {
            Insets insets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.statusBars()
                            | WindowInsetsCompat.Type.navigationBars()
                            | WindowInsetsCompat.Type.displayCutout()
            );
            target.setPadding(
                    initialLeft,
                    initialTop + (top ? insets.top : 0),
                    initialRight,
                    initialBottom + (bottom ? insets.bottom : 0)
            );
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(view);
    }
}
