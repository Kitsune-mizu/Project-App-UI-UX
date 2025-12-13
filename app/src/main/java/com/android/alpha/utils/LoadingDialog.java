package com.android.alpha.utils;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Window;

import androidx.annotation.NonNull;

import com.airbnb.lottie.LottieAnimationView;
import com.android.alpha.R;

public class LoadingDialog extends Dialog {

    // === FIELDS ===
    private LottieAnimationView lottieAnimationView;
    private final boolean isCancelable;

    // === CONSTRUCTORS ===
    public LoadingDialog(@NonNull Context context) {
        this(context, false);
    }

    public LoadingDialog(@NonNull Context context, boolean isCancelable) {
        super(context);
        this.isCancelable = isCancelable;
    }

    // === LIFECYCLE METHODS ===
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setupDialog();
        setupAnimation();
    }

    @Override
    protected void onStop() {
        stopLottieAnimation();
        super.onStop();
    }

    @Override
    public void dismiss() {
        stopLottieAnimation();
        super.dismiss();
    }

    // === DIALOG SETUP ===
    private void setupDialog() {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_loading);
        setCancelable(isCancelable);

        Window window = getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
    }

    // === ANIMATION CONTROL ===
    private void setupAnimation() {
        lottieAnimationView = findViewById(R.id.lottieLoading);
        if (lottieAnimationView != null) {
            lottieAnimationView.setAnimation(R.raw.loading_animation);
            lottieAnimationView.playAnimation();
        }
    }

    private void stopLottieAnimation() {
        if (lottieAnimationView != null && lottieAnimationView.isAnimating()) {
            lottieAnimationView.cancelAnimation();
        }
    }
}