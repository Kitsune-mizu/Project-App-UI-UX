package com.android.alpha.utils;

import android.view.View;
import android.view.animation.AlphaAnimation;

import com.facebook.shimmer.ShimmerFrameLayout;

public class ShimmerHelper {

    // === SHIMMER CONTROL METHODS ===

    /**
     * Shows the Shimmer layout and hides content views.
     */
    public static void show(ShimmerFrameLayout shimmerLayout, View... contentViews) {
        if (shimmerLayout == null) return;

        shimmerLayout.setVisibility(View.VISIBLE);
        shimmerLayout.startShimmer();
        hideContent(contentViews);
    }

    /**
     * Hides the Shimmer layout and shows content views with fade-in animation.
     */
    public static void hide(ShimmerFrameLayout shimmerLayout, View... contentViews) {
        if (shimmerLayout == null) return;

        shimmerLayout.stopShimmer();
        shimmerLayout.setVisibility(View.GONE);

        showContentWithFade(contentViews);
    }

    // === VISIBILITY & ANIMATION HELPERS ===

    private static void hideContent(View... views) {
        for (View v : views) {
            if (v != null) v.setVisibility(View.INVISIBLE);
        }
    }

    private static void showContentWithFade(View... views) {
        for (View v : views) {
            if (v != null) {
                v.setVisibility(View.VISIBLE);
                applyFadeIn(v);
            }
        }
    }

    private static void applyFadeIn(View view) {
        AlphaAnimation fadeIn = new AlphaAnimation(0.3f, 1f);
        fadeIn.setDuration(400);
        fadeIn.setFillAfter(true);
        view.startAnimation(fadeIn);
    }
}