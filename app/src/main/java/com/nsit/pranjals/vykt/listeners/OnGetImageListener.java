/*
 * Copyright 2016 Tzutalin
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.nsit.pranjals.vykt.listeners;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Point;
import android.media.Image;
import android.media.Image.Plane;
import android.media.ImageReader;
import android.media.ImageReader.OnImageAvailableListener;
import android.os.Handler;
import android.os.Trace;
import android.util.Log;
import android.view.Surface;

import com.nsit.pranjals.vykt.ChatActivity;
import com.nsit.pranjals.vykt.R;
import com.nsit.pranjals.vykt.views.FeatureView;
import com.tzutalin.dlibtest.FileUtils;
import com.tzutalin.dlibtest.ImageUtils;
import com.tzutalin.dlib.Constants;
import com.tzutalin.dlib.FaceDet;
import com.tzutalin.dlib.VisionDetRet;

import junit.framework.Assert;

import java.io.File;
import java.io.IOException;
import java.util.List;

import libsvm.*;

/**
 * Class that takes in preview frames and converts the image to Bitmaps
 * to process with dLib library.
 */
public class OnGetImageListener implements OnImageAvailableListener {
    private static final boolean SAVE_PREVIEW_BITMAP = false;

    public static final int INPUT_SIZE = 224;
    private static final String TAG = "OnGetImageListener";

    private int mPreviewWidth = 0;
    private int mPreviewHeight = 0;
    private byte[][] mYUVBytes;
    private int[] mRGBBytes = null;
    private Bitmap mRGBFrameBitmap = null;
    private Bitmap mCroppedBitmap = null;

    private svm_model model;

    private boolean mIsComputing = false;
    private Handler mInferenceHandler;

    private Context mContext;
    private FaceDet mFaceDet;
    private FeatureView featureView;

    public void initialize(
            final Context context,
            final Handler handler,
            final FeatureView featureView) {
        this.mContext = context;
        this.mInferenceHandler = handler;
        mFaceDet = new FaceDet(Constants.getFaceShapeModelPath());
        this.featureView = featureView;
        try {
            model = svm.svm_load_model(com.nsit.pranjals.vykt.utils.FileUtils.getSVMModelPath());
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    public void deInitialize() {
        synchronized (OnGetImageListener.this) {
            if (mFaceDet != null) {
                mFaceDet.release();
            }
        }
    }

    private void drawResizedBitmap(final Bitmap src, final Bitmap dst) {
        // Fixes rotation issues.
        final int rotation =
                ((ChatActivity) mContext).getWindowManager().getDefaultDisplay().getRotation();
        /*if (screen_width < screen_height) {
            mScreenRotation = 90;
        } else {
            mScreenRotation = 0;
        }*/

        // We detect faces in a cropped square frame.
        Assert.assertEquals(dst.getWidth(), dst.getHeight());
        final float minDim = Math.min(src.getWidth(), src.getHeight());

        final Matrix matrix = new Matrix();

        // Mirroring the image due to front camera usage.
        matrix.preScale(-1, 1, src.getWidth() / 2.0f, src.getHeight() / 2.0f);

        // We only want the center square out of the original rectangle.
        final float translateX = -Math.max(0, (src.getWidth() - minDim) / 2);
        final float translateY = -Math.max(0, (src.getHeight() - minDim) / 2);
        matrix.postTranslate(translateX, translateY);

        final float scaleFactor = dst.getHeight() / minDim;
        matrix.postScale(scaleFactor, scaleFactor);

        // Rotate around the center if necessary.
        /*if (mScreenRotation != 0) {
            matrix.postTranslate(-dst.getWidth() / 2.0f, -dst.getHeight() / 2.0f);
            matrix.postRotate(mScreenRotation);
            matrix.postTranslate(dst.getWidth() / 2.0f, dst.getHeight() / 2.0f);
        }*/
        if (Surface.ROTATION_90 == rotation) {
            matrix.postRotate(0, dst.getWidth() / 2.0f, dst.getHeight() / 2.0f);
        } else if (Surface.ROTATION_270 == rotation) {
            matrix.postRotate(180, dst.getWidth() / 2.0f, dst.getHeight() / 2.0f);
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180, dst.getWidth() / 2.0f, dst.getHeight() / 2.0f);
        } else {
            matrix.postRotate(90, dst.getWidth() / 2.0f, dst.getHeight() / 2.0f);
        }

        final Canvas canvas = new Canvas(dst);
        canvas.drawBitmap(src, matrix, null);
    }

    @Override
    public void onImageAvailable(final ImageReader reader) {
        Image image = null;
        try {
            image = reader.acquireLatestImage();

            if (image == null) {
                return;
            }

            // No mutex needed as this method is not reentrant.
            if (mIsComputing) {
                image.close();
                return;
            }
            mIsComputing = true;

            Trace.beginSection("imageAvailable");

            final Plane[] planes = image.getPlanes();

            // Initialize the storage bitmaps once when the resolution is known.
            if (mPreviewWidth != image.getWidth() || mPreviewHeight != image.getHeight()) {
                mPreviewWidth = image.getWidth();
                mPreviewHeight = image.getHeight();

                Log.d(
                        TAG,
                        String.format("Initializing at size %dx%d", mPreviewWidth, mPreviewHeight)
                );

                mRGBBytes = new int[mPreviewWidth * mPreviewHeight];
                mRGBFrameBitmap =
                        Bitmap.createBitmap(mPreviewWidth, mPreviewHeight, Config.ARGB_8888);
                mCroppedBitmap =
                        Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Config.ARGB_8888);

                mYUVBytes = new byte[planes.length][];
                for (int i = 0; i < planes.length; ++i) {
                    mYUVBytes[i] = new byte[planes[i].getBuffer().capacity()];
                }
            }

            for (int i = 0; i < planes.length; ++i) {
                planes[i].getBuffer().get(mYUVBytes[i]);
            }

            final int yRowStride = planes[0].getRowStride();
            final int uvRowStride = planes[1].getRowStride();
            final int uvPixelStride = planes[1].getPixelStride();
            ImageUtils.convertYUV420ToARGB8888(
                    mYUVBytes[0],
                    mYUVBytes[1],
                    mYUVBytes[2],
                    mRGBBytes,
                    mPreviewWidth,
                    mPreviewHeight,
                    yRowStride,
                    uvRowStride,
                    uvPixelStride,
                    false);

            image.close();
        } catch (final Exception e) {
            if (image != null) {
                image.close();
            }
            Log.e(TAG, "Exception!", e);
            Trace.endSection();
            return;
        }

        mRGBFrameBitmap.setPixels(mRGBBytes,
                0,                                  // offset
                mPreviewWidth,                      // number of pixels in a row
                0, 0,                               // start pixel coordinates
                mPreviewWidth, mPreviewHeight);     // end pixel coordinates

        drawResizedBitmap(mRGBFrameBitmap, mCroppedBitmap);

        if (SAVE_PREVIEW_BITMAP) {
            ImageUtils.saveBitmap(mCroppedBitmap);
        }

        mInferenceHandler.post(
                new Runnable() {
                    @Override
                    public void run() {
                        if (!new File(Constants.getFaceShapeModelPath()).exists()) {
                            FileUtils.copyFileFromRawToOthers(
                                    mContext,
                                    R.raw.shape_predictor_68_face_landmarks,
                                    Constants.getFaceShapeModelPath()
                            );
                        }
                        if (!new File(com.nsit.pranjals.vykt.utils.FileUtils.getSVMModelPath())
                                .exists()) {
                            FileUtils.copyFileFromRawToOthers(
                                    mContext,
                                    R.raw.svm_data,
                                    com.nsit.pranjals.vykt.utils.FileUtils.getSVMModelPath()
                            );
                        }

                        long startTime = System.currentTimeMillis();
                        List<VisionDetRet> results;
                        synchronized (OnGetImageListener.this) {
                            results = mFaceDet.detect(mCroppedBitmap);
                        }
                        long endTime = System.currentTimeMillis();
                        Log.v(
                                TAG,
                                "Processing completed in "
                                        + (endTime - startTime)
                                        + " milliseconds"
                        );

                        predict(results);

                        // Draw on the featureView.
                        featureView.drawResults(results);

                        mIsComputing = false;
                    }
                });

        Trace.endSection();
    }

    private double[] x = new double[68];
    private double[] y = new double[68];
    private double[] a = new double[68];
    private double[] d = new double[68];
    private double angleNose;

    private void predict (List<VisionDetRet> results) {

        if (results == null || results.size() == 0)
            return;

        List<Point> points = results.get(0).getFaceLandmarks();

        if (points.size() == 0) return;

        double mx = 0, my = 0;

        for (int i = 0; i < 68; i++) {
            mx += points.get(i).x;
            my += points.get(i).y;
        }

        mx = mx / 68;
        my = my / 68;

        for (int i = 0; i < 68; i++) {
            x[i] = points.get(i).x - mx;
            y[i] = points.get(i).y - my;
        }
        if (x[26] == x[29]) {
            angleNose = 0;
        } else {
            angleNose = (int) (Math.atan((y[26] - y[29])/(x[26] - x[29]))*180/Math.PI);
        }
        if (angleNose < 0) {
            angleNose += 90;
        } else {
            angleNose -= 90;
        }
        double maxDist = 0;
        for (int i = 0; i < 68; i++) {
            d[i] = Math.sqrt(x[i]*x[i] + y[i]*y[i]);
            if (d[i] > maxDist) {
                maxDist = d[i];
            }
            if (x[i] != 0) {
                a[i] = (Math.atan(y[i] / x[i]) * 180 / Math.PI) - angleNose;
            } else {
                a[i] = 90 - angleNose;
            }
        }

        svm_node[] s = new svm_node[272];

        for (int j = 0; j < 68; j++) {
            int k = j * 4;
            s[k] = new svm_node();
            s[k].index = k + 1;
            s[k].value = x[j] / 68;
            k++;
            s[k] = new svm_node();
            s[k].index = k + 1;
            s[k].value = y[j] / 68;
            k++;
            s[k] = new svm_node();
            s[k].index = k + 1;
            s[k].value = d[j] / 68;
            k++;
            s[k] = new svm_node();
            s[k].index = k + 1;
            s[k].value = a[j];
        }

        double r = svm.svm_predict(model, s);
        Log.v("res27", r + " with maxDist " + maxDist);
    }

}
