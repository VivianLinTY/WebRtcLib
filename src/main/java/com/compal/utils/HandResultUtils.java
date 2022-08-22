package com.compal.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;

import com.google.mediapipe.solutioncore.ResultListener;
import com.google.mediapipe.solutions.hands.Hands;
import com.google.mediapipe.solutions.hands.HandsOptions;
import com.google.mediapipe.solutions.hands.HandsResult;

public class HandResultUtils {

    private static final String TAG = "HandResultUtils";

    private static HandResultUtils sInstance;

    public static HandResultUtils getInstance() {
        if (null == sInstance) {
            sInstance = new HandResultUtils();
        }
        return sInstance;
    }

    private Hands hands;

    public void registerHandListener(Context context, ResultListener<HandsResult> callback) {
        if (null != hands) {
            unRegisterHandListener();
        }
        LogUtils.v(TAG, "registerHandListener");
        hands = new Hands(context.getApplicationContext(),
                HandsOptions.builder()
                        .setStaticImageMode(true)
                        .setMaxNumHands(2)
                        .setRunOnGpu(true)
                        .build());

        hands.setResultListener(callback);
        hands.setErrorListener((message, e) -> LogUtils.e(TAG, "MediaPipe Hands error:" + message));
    }

    public void unRegisterHandListener() {
        LogUtils.v(TAG, "unRegisterHandListener");
        if (null != hands) {
            hands.close();
            hands.setResultListener(null);
            hands.setErrorListener(null);
            hands = null;
        }
    }

    public void handleBitmap(Bitmap bitmap) {
        if (null == bitmap) {
            LogUtils.w(TAG, "Bitmap is null.");
            return;
        }
        LogUtils.v(TAG, "handleBitmap");
        if (null != hands) {
            hands.send(bitmap);
        }
    }

    public Bitmap mirrorBitmap(Bitmap bitmap) {
        Matrix matrix = new Matrix();
        matrix.preScale(-1.0f, 1.0f);
        Bitmap newBitmap = Bitmap.createBitmap(bitmap, 0, 0,
                bitmap.getWidth(), bitmap.getHeight(), matrix, false);
        bitmap.recycle();
        return newBitmap;
    }

    public int[] mirrorImageArray(int width, int[] pixels) {
        int[] newPixels = new int[pixels.length];
        for (int i = 0; i < pixels.length; i++) {
            newPixels[i] = pixels[i - 2 * (i % width) + width - 1];
        }
        return newPixels;
    }
}
