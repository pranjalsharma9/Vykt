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

package com.nsit.pranjals.vykt;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ImageReader;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.util.SparseArray;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.WindowManager;
import android.widget.ListView;
import android.widget.Toast;

import com.nsit.pranjals.vykt.adapters.ChatListAdapter;
import com.nsit.pranjals.vykt.listeners.OnGetImageListener;
import com.nsit.pranjals.vykt.models.Message;
import com.nsit.pranjals.vykt.views.AutoFitTextureView;
import com.nsit.pranjals.vykt.views.FeatureView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import hugo.weaving.DebugLog;

public class ChatActivity extends AppCompatActivity {

    //==============================================================================================
    // PERMISSIONS AND RELATED STUFF!
    //==============================================================================================

    private static String[] PERMISSIONS_REQ = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.CAMERA
    };

    private static final int REQUEST_CODE_PERMISSION = 2;

    // Function with code to verify permissions for camera and storage in a go!
    private static boolean verifyPermissions(Activity activity) {
        // Check if we have write permission
        int write_permission = ActivityCompat.checkSelfPermission(activity,
                Manifest.permission.WRITE_EXTERNAL_STORAGE);
        int read_permission = ActivityCompat.checkSelfPermission(activity,
                Manifest.permission.WRITE_EXTERNAL_STORAGE);
        int camera_permission = ActivityCompat.checkSelfPermission(activity,
                Manifest.permission.CAMERA);

        if (    write_permission != PackageManager.PERMISSION_GRANTED
                || read_permission != PackageManager.PERMISSION_GRANTED
                || camera_permission != PackageManager.PERMISSION_GRANTED ) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(
                    activity,
                    PERMISSIONS_REQ,
                    REQUEST_CODE_PERMISSION
            );
            return false;
        } else {
            return true;
        }
    }

    //==============================================================================================
    // CAMERA STUFF!
    //==============================================================================================

    // The camera preview's center square is the only region processed.
    // This dimension is the desired size in pixels.
    private static final int MINIMUM_PREVIEW_SIZE = 320;
    private static final String TAG = "ChatActivity";


    // Conversion from screen rotation to JPEG orientation.
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    // Listener to listen TextureView changes.
    private final TextureView.SurfaceTextureListener surfaceTextureListener =
            new TextureView.SurfaceTextureListener() {
                @Override
                public void onSurfaceTextureAvailable(
                        final SurfaceTexture texture, final int width, final int height) {
                    openCamera(width, height);
                }

                @Override
                public void onSurfaceTextureSizeChanged(
                        final SurfaceTexture texture, final int width, final int height) {
                    configureTransform(width, height);
                }

                @Override
                public boolean onSurfaceTextureDestroyed(final SurfaceTexture texture) {
                    return true;
                }

                @Override
                public void onSurfaceTextureUpdated(final SurfaceTexture texture) {
                }
            };

    // Camera ID of the current camera.
    private String cameraId;

    // TextureView for displaying camera feed. Changed from AutoFitTextureView.
    private AutoFitTextureView textureView;

    // FeatureView for drawing features on TextureView.
    private FeatureView featureView;

    // For camera preview.
    private CameraCaptureSession captureSession;

    // CameraDevice reference.
    private CameraDevice cameraDevice;

    // Camera preview size.
    private Size previewSize;

    // android.hardware.camera2.CameraDevice.StateCallback
    // called when the CameraDevice changes it's state.
    private final CameraDevice.StateCallback stateCallback =
            new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull final CameraDevice cd) {
                    // This method is called when the camera is opened. We start camera preview.
                    cameraOpenCloseLock.release();
                    cameraDevice = cd;
                    createCameraPreviewSession();
                }

                @Override
                public void onDisconnected(@NonNull final CameraDevice cd) {
                    cameraOpenCloseLock.release();
                    cd.close();
                    cameraDevice = null;
                    mOnGetPreviewListener.deInitialize();
                }

                @Override
                public void onError(@NonNull final CameraDevice cd, final int error) {
                    cameraOpenCloseLock.release();
                    cd.close();
                    cameraDevice = null;
                    finish();
                    mOnGetPreviewListener.deInitialize();
                }
            };

    // An ImageReader that handles preview frame capture.
    private ImageReader previewReader;

    // android.hardware.camera2.CaptureRequest.Builder for the camera preview
    private CaptureRequest.Builder previewRequestBuilder;

    // CaptureRequest generated by previewRequestBuilder
    private CaptureRequest previewRequest;

    // A Semaphore to prevent the app from exiting before closing the camera.
    private final Semaphore cameraOpenCloseLock = new Semaphore(1);

    private final OnGetImageListener mOnGetPreviewListener = new OnGetImageListener();

    /*
     * Sets up member variables related to camera.
     * width    The width of available size for camera preview
     * height   The height of available size for camera preview
     */
    @DebugLog
    @SuppressLint({"UseSparseArrays", "LongLogTag"})
    private void setUpCameraOutputs() {
        final CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            SparseArray<Integer> cameraFaceTypeMap = new SparseArray<>();
            // Check the facing types of camera devices
            for (final String cameraId : manager.getCameraIdList()) {
                final CameraCharacteristics characteristics =
                        manager.getCameraCharacteristics(cameraId);
                final Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                // Counts the number of front facing cameras.
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    if (cameraFaceTypeMap.get(CameraCharacteristics.LENS_FACING_FRONT) != null) {
                        cameraFaceTypeMap.append(CameraCharacteristics.LENS_FACING_FRONT,
                                cameraFaceTypeMap.get(CameraCharacteristics.LENS_FACING_FRONT) + 1);
                    } else {
                        cameraFaceTypeMap.append(CameraCharacteristics.LENS_FACING_FRONT, 1);
                    }
                }
                // Counts the number of back facing cameras.
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    if (cameraFaceTypeMap.get(CameraCharacteristics.LENS_FACING_BACK) != null) {
                        cameraFaceTypeMap.append(CameraCharacteristics.LENS_FACING_BACK,
                                cameraFaceTypeMap.get(CameraCharacteristics.LENS_FACING_BACK) + 1);
                    } else {
                        cameraFaceTypeMap.append(CameraCharacteristics.LENS_FACING_BACK, 1);
                    }
                }
            }

            Integer num_facing_front_camera =
                    cameraFaceTypeMap.get(CameraCharacteristics.LENS_FACING_FRONT);

            for (final String cameraId : manager.getCameraIdList()) {
                final CameraCharacteristics characteristics =
                        manager.getCameraCharacteristics(cameraId);
                final Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                // If facing front camera or facing external camera exist,
                // we won't use facing back camera
                if (num_facing_front_camera != null && num_facing_front_camera > 0) {
                    // We don't use a back facing camera if there are front facing cameras.
                    if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                        continue;
                    }
                }
                // We reach the code below with either a front facing camera or an external camera.
                // Get the map configuration map of the selected camera.
                final StreamConfigurationMap map =
                        characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

                // Check next camera if map is null.
                if (map == null) {
                    continue;
                }

                // For still image captures, we use the largest available size.
                //final Size largest =
                //        Collections.max(
                //                Arrays.asList(map.getOutputSizes(ImageFormat.YUV_420_888)),
                //                new CompareSizesByArea());

                // Danger, W.R.! Attempting to use too large a preview size could  exceed the camera
                // bus' bandwidth limitation, resulting in gorgeous previews but the storage of
                // garbage capture data.
                previewSize = chooseOptimalSize(
                        map.getOutputSizes(SurfaceTexture.class)
                );

                // We fit the aspect ratio of AutoFitTextureView to the size of preview we picked.
                final int orientation = getResources().getConfiguration().orientation;
                if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    textureView.setAspectRatio(previewSize.getWidth(), previewSize.getHeight());
                    featureView.setAspectRatio(previewSize.getWidth(), previewSize.getHeight());
                } else {
                    textureView.setAspectRatio(previewSize.getHeight(), previewSize.getWidth());
                    featureView.setAspectRatio(previewSize.getHeight(), previewSize.getWidth());
                }

                this.cameraId = cameraId;
                return;
            }
        } catch (final CameraAccessException e) {
            Log.e(TAG, "Exception!", e);
        } catch (final NullPointerException e) {
            // Currently an NPE is thrown when the Camera2API is used but not supported on the
            // device this code runs.
            Log.e(TAG, "Camera2 API not supported!", e);
        }
    }

    // Opens the camera specified by cameraId.
    @SuppressLint("LongLogTag")
    @DebugLog
    private void openCamera(final int width, final int height) {
        setUpCameraOutputs();
        configureTransform(width, height);
        final CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            if (!cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "checkSelfPermission CAMERA");
            }
            manager.openCamera(cameraId, stateCallback, backgroundHandler);
            Log.d(TAG, "open Camera");
        } catch (final CameraAccessException e) {
            Log.e(TAG, "Exception!", e);
        } catch (final InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
        }
    }

    // Closes the current CameraDevice.
    @DebugLog
    private void closeCamera() {
        try {
            cameraOpenCloseLock.acquire();
            if (null != captureSession) {
                captureSession.close();
                captureSession = null;
            }
            if (null != cameraDevice) {
                cameraDevice.close();
                cameraDevice = null;
            }
            if (null != previewReader) {
                previewReader.close();
                previewReader = null;
            }
            mOnGetPreviewListener.deInitialize();
        } catch (final InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
        } finally {
            cameraOpenCloseLock.release();
        }
    }

    /*
     * Given choices of Sizes supported by a camera, chooses the smallest one whose
     * width and height are at least as large as the respective requested values, and whose aspect
     * ratio matches with the specified value.
     *
     * choices      The list of sizes that the camera supports for the intended output class
     * width        The minimum desired width
     * height       The minimum desired height
     * aspectRatio  The aspect ratio
     * return       The optimal {@code Size}, or an arbitrary one if none were big enough
     */
    @SuppressLint("LongLogTag")
    @DebugLog
    private static Size chooseOptimalSize(final Size[] choices) {
        // Collect the supported resolutions that are at least as big as the preview Surface
        final List<Size> bigEnough = new ArrayList<>();
        for (final Size option : choices) {
            if (option.getHeight() >= MINIMUM_PREVIEW_SIZE
                    && option.getWidth() >= MINIMUM_PREVIEW_SIZE) {
                Log.i(TAG, "Adding size: " + option.getWidth() + "x" + option.getHeight());
                bigEnough.add(option);
            } else {
                Log.i(TAG, "Not adding size: " + option.getWidth() + "x" + option.getHeight());
            }
        }

        // Pick the smallest of those, assuming we found any
        if (bigEnough.size() > 0) {
            final Size chosenSize = Collections.min(bigEnough, new CompareSizesByArea());
            Log.i(TAG, "Chosen size: " + chosenSize.getWidth() + "x" + chosenSize.getHeight());
            return chosenSize;
        } else {
            Log.e(TAG, "Couldn't find any suitable preview size");
            return choices[0];
        }
    }

    private final CameraCaptureSession.CaptureCallback captureCallback =
            new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureProgressed(
                        @NonNull final CameraCaptureSession session,
                        @NonNull final CaptureRequest request,
                        @NonNull final CaptureResult partialResult) {}

                @Override
                public void onCaptureCompleted(
                        @NonNull final CameraCaptureSession session,
                        @NonNull final CaptureRequest request,
                        @NonNull final TotalCaptureResult result) {}
            };

    // Creates a new CameraCaptureSession for camera preview.
    @SuppressLint("LongLogTag")
    @DebugLog
    private void createCameraPreviewSession() {
        try {
            final SurfaceTexture texture = textureView.getSurfaceTexture();
            assert texture != null;

            // We configure the size of default buffer to be the size of camera preview we want.
            texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());

            // This is the output Surface we need to start preview.
            final Surface surface = new Surface(texture);

            // We set up a CaptureRequest.Builder with the output Surface.
            previewRequestBuilder =
                    cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewRequestBuilder.addTarget(surface);

            Log.i(TAG,
                    "Opening camera preview: " + previewSize.getWidth()
                            + "x" + previewSize.getHeight());

            // Create the reader for the preview frames.
            previewReader =
                    ImageReader.newInstance(
                            previewSize.getWidth(),
                            previewSize.getHeight(),
                            ImageFormat.YUV_420_888, 2
                    );

            previewReader.setOnImageAvailableListener(mOnGetPreviewListener, backgroundHandler);
            previewRequestBuilder.addTarget(previewReader.getSurface());

            // Here, we create a CameraCaptureSession for camera preview.
            cameraDevice.createCaptureSession(
                    Arrays.asList(surface, previewReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {

                        @Override
                        public void onConfigured(@NonNull final CameraCaptureSession session) {
                            // The camera is already closed
                            if (null == cameraDevice) {
                                return;
                            }

                            // When the session is ready, we start displaying the preview.
                            captureSession = session;
                            try {
                                // Auto focus should be continuous for camera preview.
                                previewRequestBuilder.set(
                                        CaptureRequest.CONTROL_AF_MODE,
                                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                                // Flash is automatically enabled when necessary.
                                previewRequestBuilder.set(
                                        CaptureRequest.CONTROL_AE_MODE,
                                        CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);

                                // Finally, we start displaying the camera preview.
                                previewRequest = previewRequestBuilder.build();
                                captureSession.setRepeatingRequest(
                                        previewRequest,
                                        captureCallback,
                                        backgroundHandler
                                );
                            } catch (final CameraAccessException e) {
                                Log.e(TAG, "Exception!", e);
                            }
                        }

                        @Override
                        public void onConfigureFailed(@NonNull final CameraCaptureSession session) {
                            showToast("Failed");
                        }
                    },
                    null);
        } catch (final CameraAccessException e) {
            Log.e(TAG, "Exception!", e);
        }

        Log.i(TAG, "Getting assets.");

        mOnGetPreviewListener.initialize(this, inferenceHandler, featureView);
    }

    /*
     * Configures the necessary android.graphics.Matrix transformation to mTextureView.
     * This method should be called after the camera preview size is determined in
     * setUpCameraOutputs and also the size of mTextureView is fixed.
     *
     * viewWidth    The width of mTextureView
     * viewHeight   The height of mTextureView
     */
    @DebugLog
    private void configureTransform(final int viewWidth, final int viewHeight) {
        if (null == textureView || null == previewSize) {
            return;
        }
        final int rotation = getWindowManager().getDefaultDisplay().getRotation();
        final Matrix matrix = new Matrix();
        final RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        final RectF bufferRect = new RectF(0, 0, previewSize.getHeight(), previewSize.getWidth());
        final float centerX = viewRect.centerX();
        final float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            final float scale =
                    Math.max(
                            (float) viewHeight / previewSize.getHeight(),
                            (float) viewWidth / previewSize.getWidth()
                    );
            matrix.postScale(scale, scale, centerX, centerY);
            // matrix.postRotate(90 * (rotation - 2), centerX, centerY);
            if (Surface.ROTATION_90 == rotation) {
                matrix.postRotate(270, centerX, centerY);
            } else {
                matrix.postRotate(90, centerX, centerY);
            }
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180, centerX, centerY);
        }
        textureView.setTransform(matrix);
    }

    // Compares two Sizes based on their areas.
    private static class CompareSizesByArea implements Comparator<Size> {
        @Override
        public int compare(final Size lhs, final Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum(
                    (long) lhs.getWidth()*lhs.getHeight() - (long) rhs.getWidth()*rhs.getHeight()
            );
        }
    }

    //==============================================================================================
    // OTHER ACTIVITY-NECESSITIES!
    //==============================================================================================

    // Shows a Toast on the UI thread.
    private void showToast(final String text) {
        this.runOnUiThread(
                new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(ChatActivity.this, text, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // An additional thread for running tasks that shouldn't block the UI.
    private HandlerThread backgroundThread;

    // A Handler for running tasks in the background.
    private Handler backgroundHandler;

    // An additional thread for running inference so as not to block the camera.
    private HandlerThread inferenceThread;

    // Handler for running tasks in the background.
    private Handler inferenceHandler;

    @Override
    protected void onCreate (Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_chat);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED) {
                verifyPermissions(this);
            }
        }
        textureView = (AutoFitTextureView) findViewById(R.id.camera_feed_texture_view);
        featureView = (FeatureView) findViewById(R.id.feature_view);
        initChat();
    }

    @Override
    public void onResume() {
        super.onResume();
        startBackgroundThread();
        // When the screen is turned off and turned back on, the SurfaceTexture is already
        // available, and "onSurfaceTextureAvailable" will not be called. In that case, we can open
        // a camera and start preview from here (otherwise, we wait until the surface is ready in
        // the SurfaceTextureListener).
        if (textureView.isAvailable()) {
            openCamera(textureView.getWidth(), textureView.getHeight());
        } else {
            textureView.setSurfaceTextureListener(surfaceTextureListener);
        }
    }

    @Override
    public void onPause() {
        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }

    // Starts a background thread and its Handler.
    @DebugLog
    private void startBackgroundThread() {
        backgroundThread = new HandlerThread("ImageListener");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());

        inferenceThread = new HandlerThread("InferenceThread");
        inferenceThread.start();
        inferenceHandler = new Handler(inferenceThread.getLooper());
    }

    // Stops the background thread and its Handler.
    @SuppressLint("LongLogTag")
    @DebugLog
    private void stopBackgroundThread() {
        backgroundThread.quitSafely();
        inferenceThread.quitSafely();
        try {
            backgroundThread.join();
            backgroundThread = null;
            backgroundHandler = null;

            inferenceThread.join();
            inferenceThread = null;
            inferenceThread = null;
        } catch (final InterruptedException e) {
            Log.e(TAG, "error" ,e );
        }
    }

    //==============================================================================================
    // CHAT RELATED STUFF!
    //==============================================================================================

    private ArrayList<Message> messages;

    private void initChat () {
        Log.v("test27", "reached here!");
        messages = new ArrayList<>();
        //setting the adapters.
        messages.add(new Message(9018202, "sender name", "Sample message"));
        messages.add(new Message(9018202, "sender name", "Sample message"));
        messages.add(new Message(9018202, "sender name", "Sample message"));
        messages.add(new Message(9018202, "sender name", "Sample message"));
        messages.add(new Message(9018202, "sender name", "Sample message"));
        messages.add(new Message(9018202, "sender name", "Sample message"));
        messages.add(new Message(9018202, "sender name", "Sample message"));
        messages.add(new Message(9018202, "sender name", "Sample message"));
        messages.add(new Message(9018202, "sender name", "Sample message"));
        messages.add(new Message(9018202, "sender name", "Sample message"));
        messages.add(new Message(9018202, "sender name", "Sample message"));
        messages.add(new Message(9018202, "sender name", "Sample message"));
        messages.add(new Message(9018202, "sender name", "Sample message"));
        messages.add(new Message(9018202, "sender name", "Sample message"));
        messages.add(new Message(9018202, "sender name", "Sample message"));
        ListView chatList = (ListView) findViewById(R.id.act_chat_chat_list);
        ChatListAdapter chatListAdapter = new ChatListAdapter(messages, chatList);
        chatList.setAdapter(chatListAdapter);
    }

}
