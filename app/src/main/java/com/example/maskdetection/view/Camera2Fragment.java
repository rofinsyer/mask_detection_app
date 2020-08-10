package com.example.maskdetection.view;

import android.Manifest;
import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.example.maskdetection.R;

import java.io.File;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;


public class Camera2Fragment extends Fragment {
    private static final String TAG = "Camera2Fragment";
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    private static final String FRAGMENT_DIALOG = "dialog";

    public static Fragment newInstance(){
        return new Camera2Fragment();
    }

    static{
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    private static final int STATE_PREVIEW  = 0;
    private static final int STATE_WAITING_LOCK = 1;
    private static final int STATE_WAITING_PRECATURE =2;
    private static final int STATE_WAITING_NON_PRECATURE = 3;
    private static final int STATE_PICTURE_TAKEN = 4;
    private static final int MAX_PREVIEW_WIDTH = 1920; //TODO tablet width check
    private static final int MAX_PREVIEW_HEIGHT = 1080; //TODO table height check

    private String mCameraId;
    private AutoFitTextureView mTextureView;
    private CameraCaptureSession mCaptureSession;
    private CameraDevice mCameraDevice;
    private Size mPreviewSize;
    private Semaphore mCameraOpenCloseLock = new Semaphore(1);
    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler;
    private ImageReader mImageReader;
    private File mFile;
    CameraManager manager;
    CameraCharacteristics characteristics;

    private CaptureRequest.Builder mPreviewRequestBuilder;
    private CaptureRequest mPreviewRequest;

    private int mSensorOrientation;


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_camera, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mTextureView = (AutoFitTextureView)view.findViewById(R.id.texture_camera);
    }

    @Override
    public void onResume() {
        super.onResume();
        startBackgroundThread();

        if(mTextureView.isAvailable()){
            openCamera(mTextureView.getWidth(), mTextureView.getHeight());






        }else {
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }
    }

    @Override
    public void onPause() {
        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }

    private final TextureView.SurfaceTextureListener mSurfaceTextureListener
            = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
            openCamera(width, height);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int width, int height) {
            configureTransform(width, height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {

        }
    };

    private void convertBitmapImage(){
        mTextureView.getBitmap();
        Toast.makeText(getActivity(), "Get Bitmap!", Toast.LENGTH_SHORT).show();
    }

    private Handler bitmapHandler;

    private Runnable bitmapRunnable = new Runnable() {
        @Override
        public void run() {
            if(getActivity().isFinishing() == true){
                return;
            }
            convertBitmapImage();
            bitmapHandler.postDelayed(this, 300);
        }
    };

    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            mCameraOpenCloseLock.release();
            mCameraDevice = cameraDevice;
            createCameraPreviewSession();
            bitmapHandler = new Handler(Looper.myLooper());
            bitmapHandler.postDelayed(bitmapRunnable, 500);
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
            bitmapHandler.removeCallbacks(bitmapRunnable);
            bitmapHandler = null;
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int i) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
            Activity activity = getActivity();
            if(activity != null){
                activity.finish();
            }
        }
    };

    private void openCamera(int width, int height){
        if(ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED){
            //TODO requestCameraPermission()
            return;
        }
        setUpCameraOutputs(width, height);
        configureTransform(width, height);
        manager = (CameraManager)getActivity().getSystemService(Context.CAMERA_SERVICE);

        try{
            if(!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)){
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }
            manager.openCamera(mCameraId, mStateCallback, mBackgroundHandler);
        }catch(CameraAccessException e){
            e.printStackTrace();
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    @SuppressWarnings("SuspiciousNameCombination")
    private void setUpCameraOutputs(int width, int height){
        Activity activity = getActivity();
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try{
            for(String cameraId : manager.getCameraIdList()){
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if(facing != null && facing == CameraCharacteristics.LENS_FACING_BACK){
                    continue;
                }
                StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if(map == null){
                    continue;
                }

                Size largest = Collections.max(Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)), new CompareSizesByArea());
                //TODO image capture require?????
                mImageReader = ImageReader.newInstance(largest.getWidth(), largest.getHeight(), ImageFormat.JPEG, /*maxImages*/2);

                int displayRotation = activity.getWindowManager().getDefaultDisplay().getRotation();
                mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
                boolean swappedDimensions = false;
                switch(displayRotation){
                    case Surface.ROTATION_0:
                    case Surface.ROTATION_180:
                        if(mSensorOrientation == 90 || mSensorOrientation == 270){
                            swappedDimensions = true;
                        }
                        break;
                    case Surface.ROTATION_90:
                    case Surface.ROTATION_270:
                        if(mSensorOrientation == 0 || mSensorOrientation == 180){
                            swappedDimensions = true;
                        }
                        break;
                    default:
                            Log.e(TAG , "Display rotation is invalid : " + displayRotation);
                }

                Point displaySize = new Point();
                activity.getWindowManager().getDefaultDisplay().getSize(displaySize);
                int rotatedPreviewWidth  = width;
                int rotatedPreviewHeight = height;
                int maxPreviewWidth = displaySize.x;
                int maxPreviewHeight = displaySize.y;

                if(swappedDimensions){
                    rotatedPreviewWidth = height;
                    rotatedPreviewHeight = width;
                    maxPreviewWidth = displaySize.y;
                    maxPreviewHeight = displaySize.x;
                }

                if(maxPreviewWidth > MAX_PREVIEW_WIDTH){
                    maxPreviewWidth = MAX_PREVIEW_WIDTH;
                }

                if(maxPreviewHeight > MAX_PREVIEW_HEIGHT){
                    maxPreviewHeight = MAX_PREVIEW_HEIGHT;
                }

                mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
                        rotatedPreviewWidth, rotatedPreviewHeight, maxPreviewWidth,
                        maxPreviewHeight, largest);

                int orientation = getResources().getConfiguration().orientation;
                if(orientation == Configuration.ORIENTATION_LANDSCAPE){
                    mTextureView.setAspectRatio(mPreviewSize.getWidth(), mPreviewSize.getHeight());
                }else{
                    mTextureView.setAspectRatio(mPreviewSize.getHeight(), mPreviewSize.getWidth());
                }

                mCameraId = cameraId;
                return;
            }
        }catch (CameraAccessException e){
            e.printStackTrace();
        }catch(Exception e){
            e.printStackTrace();
        }
    }
    private static Size chooseOptimalSize(Size[] choices, int textureViewWidth, int textureViewHeight, int maxWidth,int maxHeight, Size aspectRatio){
        List<Size> bigEnough = new ArrayList<>();
        List<Size> notBigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();

        for(Size option: choices){
            if(option.getWidth() <= maxWidth && option.getHeight() <=  maxHeight &&
                    option.getHeight() == option.getWidth() * h / w){
                if(option.getWidth() >= textureViewWidth && option.getHeight() >= textureViewHeight){
                    bigEnough.add(option);
                }else{
                    notBigEnough.add(option);
                }
            }
        }

        if(bigEnough.size() > 0){
            return Collections.min(bigEnough, new CompareSizesByArea());
        }else if(notBigEnough.size() > 0){
            return Collections.max(notBigEnough, new CompareSizesByArea());
        }else {
            Log.d(TAG, "Couldn't find any suitable preview size");
            if(choices.length > 1){
                return choices[0];
            }else{
                return null;
            }
        }
    }

    private void configureTransform(int width, int height){
        if(mTextureView == null || mPreviewSize == null){
            Log.e(TAG,"mTextureView == null || mPreviewSize == null");
            return;
        }

        int rotation  = getActivity().getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, width, height);
        RectF bufferRect = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();

        if(Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation){
            bufferRect.offset(centerX - bufferRect.centerX(), centerY- bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) height / mPreviewSize.getHeight(),
                    (float) width / mPreviewSize.getWidth()
            );
            Log.d(TAG, "width" + mPreviewSize.getWidth() + ", height : " + mPreviewSize.getHeight());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation -2), centerX, centerY);
        }else if(Surface.ROTATION_180 == rotation){
            matrix.postRotate(180, centerX, centerY);
        }
        mTextureView.setTransform(matrix);
    }


    static class CompareSizesByArea implements Comparator<Size> {
        @Override
        public int compare(Size lhs, Size rhs) {
            return Long.signum((long)lhs.getWidth() * lhs.getHeight() - (long)rhs.getWidth() * rhs.getHeight());
        }
    }

    private void createCameraPreviewSession(){
        try{
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            assert texture != null;

            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            Surface surface = new Surface(texture);

            mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewRequestBuilder.addTarget(surface);

            mCameraDevice.createCaptureSession(Arrays.asList(surface, mImageReader.getSurface()), //TODO mImageReader check
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                            if(mCameraDevice == null){
                                return;
                            }
                            mCaptureSession = cameraCaptureSession;
                            try{
                                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                                mPreviewRequest = mPreviewRequestBuilder.build();
                                mCaptureSession.setRepeatingRequest(mPreviewRequest,
                                        null, mBackgroundHandler); //TODO captureCallback
                            }catch(CameraAccessException e){
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {

                        }
                    }, null);

        }catch(CameraAccessException e){
            e.printStackTrace();
        }
    }
    private void startBackgroundThread(){
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }
    private void stopBackgroundThread(){
        mBackgroundThread.quitSafely();
        try{
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        }catch(InterruptedException e){
            e.printStackTrace();
        }
    }
    private void closeCamera(){
        try{
            mCameraOpenCloseLock.acquire();
            if(mCaptureSession != null){
                mCaptureSession.close();
                mCaptureSession = null;
            }
            if(mCameraDevice != null){
                mCameraDevice.close();
                mCameraDevice = null;
            }
            if(mImageReader != null){
                mImageReader.close();
                mImageReader = null;
            }
            if(bitmapHandler != null){
                bitmapHandler.removeCallbacks(bitmapRunnable);
                bitmapHandler = null;
            }
        }catch(InterruptedException e){
            e.printStackTrace();
        }finally {
            mCameraOpenCloseLock.release();
        }
    }
    private int getOrientation(int rotation){
        return (ORIENTATIONS.get(rotation) + mSensorOrientation + 270) % 360;
    }
}
