package com.vlntdds.developlayground.camera;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.hardware.Camera;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.Transformation;
import android.widget.ImageView;
import android.widget.TextView;
import com.vlntdds.developlayground.R;
import java.io.IOException;
import java.util.List;
import butterknife.BindView;
import butterknife.ButterKnife;

/**
 * Created by Eduardo C. on 10/05/2016
 * Square Camera Fragment to Deal with the Preview, Surface and the Deprecated Camera API
 * (Just wanna use the deprecated API to ensure compatibility with older devices)
 */

@SuppressWarnings("deprecation")
public class CameraFragment extends Fragment implements SurfaceHolder.Callback, Camera.PictureCallback{

    private int mIDCamera = 0;
    private String mFlashType = Camera.Parameters.FLASH_MODE_OFF;
    private Camera mCamera;
    private CameraPreviewer cameraPreviewer;
    private ImageParameters mImageParams;
    private SurfaceHolder mSurface;
    private CameraOrientationListener mOrientationListener;
    public CameraFragment() {}
    public static Fragment newInstance() { return new CameraFragment(); }

    /*Flag to tell if it's safe to save a picture from Camera*/
    private boolean mSafeToTakePic = false;

    /* Butterknife Bindings */
    @BindView(R.id.top_border)
    View top_border;

    @BindView(R.id.bottom_border)
    View bottom_border;

    @BindView(R.id.btn_cameraswitch)
    ImageView btn_cameraswitch;

    @BindView(R.id.ic_flash_auto)
    TextView btn_flash;

    @BindView(R.id.btn_takepic)
    View btn_takepic;

    /* Just keep an listener to tell the orientation of the taken picture */
    @Override
    public void onAttach(Context c ) {
        super.onAttach(c);
        mOrientationListener = new CameraOrientationListener(c);
    }

    @Override
    public void onCreate(Bundle b) {
        super.onCreate(b);

        /* If the SavedInstance is not null, the parameters are already loaded */
        if (b != null) {
            mIDCamera = b.getInt("id_camera");
            mFlashType = b.getString("flash_type");
            mImageParams = b.getParcelable("image_info");
        } else {
            mIDCamera = Camera.CameraInfo.CAMERA_FACING_BACK;
            mFlashType = Camera.Parameters.FLASH_MODE_OFF;
            mImageParams = new ImageParameters();
        }
    }

    @Override
    public void onStop() {
        mOrientationListener.disable();

        if (mCamera != null) {
            stopPreviewer();
            mCamera.release();
            mCamera = null;
        }

        super.onStop();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mCamera == null) {
            restartPreviewer();
        }
    }

    @Override
    public void onSaveInstanceState(Bundle o) {
        o.putInt("id_camera", mIDCamera);
        o.putString("flash_type", mFlashType);
        o.putParcelable("image_info", mImageParams);
        super.onSaveInstanceState(o);
    }

    @Override
    public View onCreateView(LayoutInflater i, ViewGroup c, Bundle b) {
        View v = i.inflate(R.layout.camera_fragment, c, false);
        ButterKnife.bind(this, v);
        return v;
    }

    @Override
    public void onViewCreated(View v, Bundle s) {
        super.onViewCreated(v, s);
        mOrientationListener.enable();
        cameraPreviewer = (CameraPreviewer) v.findViewById(R.id.camera_preview);
        cameraPreviewer.getHolder().addCallback(CameraFragment.this);
        mImageParams.Portrait = getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;

        if (s == null) {
            ViewTreeObserver observer = cameraPreviewer.getViewTreeObserver();
            observer.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    mImageParams.PreviewWidth = cameraPreviewer.getWidth();
                    mImageParams.PreviewHeight = cameraPreviewer.getHeight();
                    mImageParams.CoverWidth = mImageParams.CoverHeight = mImageParams.calculateCoverSize();

                    if (mImageParams.isPortrait()) {
                        top_border.getLayoutParams().height = mImageParams.returnCorrectSize();
                        bottom_border.getLayoutParams().height = mImageParams.returnCorrectSize();
                    } else {
                        top_border.getLayoutParams().width = mImageParams.returnCorrectSize();
                        bottom_border.getLayoutParams().width = mImageParams.returnCorrectSize();
                    }

                    resizeCovers(top_border, bottom_border);

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                        cameraPreviewer.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    } else {
                        cameraPreviewer.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                    }
                }
            });
        } else {
            if (mImageParams.isPortrait()) {
                top_border.getLayoutParams().height = mImageParams.CoverHeight;
                bottom_border.getLayoutParams().height = mImageParams.CoverHeight;
            } else {
                top_border.getLayoutParams().width = mImageParams.CoverWidth;
                bottom_border.getLayoutParams().width = mImageParams.CoverWidth;
            }
        }

        btn_cameraswitch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mIDCamera == Camera.CameraInfo.CAMERA_FACING_FRONT)
                    mIDCamera = Camera.CameraInfo.CAMERA_FACING_BACK;
                else
                    mIDCamera = Camera.CameraInfo.CAMERA_FACING_FRONT;
                restartPreviewer();
            }
        });

        btn_flash.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mFlashType.equalsIgnoreCase(Camera.Parameters.FLASH_MODE_AUTO)) {
                    mFlashType = Camera.Parameters.FLASH_MODE_ON;
                    btn_flash.setText(R.string.flash_mode_on);
                }
                else if (mFlashType.equalsIgnoreCase(Camera.Parameters.FLASH_MODE_ON)) {
                    mFlashType = Camera.Parameters.FLASH_MODE_OFF;
                    btn_flash.setText(R.string.flash_mode_off);
                }
                else if (mFlashType.equalsIgnoreCase(Camera.Parameters.FLASH_MODE_OFF)) {
                    mFlashType = Camera.Parameters.FLASH_MODE_AUTO;
                    btn_flash.setText(R.string.flash_mode_auto);
                }
                configureCamera();
            }
        });

        btn_takepic.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                takePicture();
            }
        });
    }

    private void resizeCovers( final View topCover, final View bottomCover) {
        ResizeAnimation resizeTopAnimation = new ResizeAnimation(topCover, mImageParams);
        resizeTopAnimation.setDuration(800);
        resizeTopAnimation.setInterpolator(new AccelerateDecelerateInterpolator());
        topCover.startAnimation(resizeTopAnimation);

        ResizeAnimation resizeBtmAnimation = new ResizeAnimation(bottomCover, mImageParams);
        resizeBtmAnimation.setDuration(800);
        resizeBtmAnimation.setInterpolator(new AccelerateDecelerateInterpolator());
        bottomCover.startAnimation(resizeBtmAnimation);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        mSurface = holder;
        startPreviewer();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
    }

    @Override
    public void onActivityResult(int req, int result, Intent d) {
        if (result != Activity.RESULT_OK) return;

        switch (req) {
            case 1:
                Uri imageUri = d.getData();
                break;
            default:
                super.onActivityResult(req, result, d);
        }
    }

    /* Start Camera Previewer */
    private void startPreviewer() {
        getDisplayOrientation();
        configureCamera();

        try {
            mCamera.setPreviewDisplay(mSurface);
            mCamera.startPreview();
            mSafeToTakePic = true;
            cameraPreviewer.CameraIsReadyToFocus = true;
        } catch (IOException e) {
            Log.d("DevelopmentPlayground", "IOException on Camera" + e);
            e.printStackTrace();
        }
    }

    /* Stop Camera Previewer */
    private void stopPreviewer() {
        mSafeToTakePic = false;
        cameraPreviewer.CameraIsReadyToFocus = false;
        mCamera.stopPreview();
        cameraPreviewer.initCamera(null);
    }

    /* Restart Camera Previewer */
    private void restartPreviewer() {
        if (mCamera != null) {
            stopPreviewer();
            mCamera.release();
            mCamera = null;
        }
        getCamera(mIDCamera);
        startPreviewer();
    }

    /* Early-setup the camera */
    private void configureCamera() {
        Camera.Parameters p = mCamera.getParameters();

        Camera.Size previewSize = determineBestSize(p.getSupportedPreviewSizes());
        Camera.Size photoSize = determineBestSize(p.getSupportedPictureSizes());

        p.setPreviewSize(previewSize.width, previewSize.height);
        p.setPictureSize(photoSize.width, photoSize.height);

        /* Hide/Un-hide the Flash Button */
        List<String> flashTypes = p.getSupportedFlashModes();
        if (flashTypes != null && flashTypes.contains(mFlashType)) {
            p.setFlashMode(mFlashType);
            btn_flash.setVisibility(View.VISIBLE);
        } else {
            btn_flash.setVisibility(View.INVISIBLE);
        }

        /* Set-up the Focus Mode */
        if (p.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
            p.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
        }

        mCamera.setParameters(p);
    }

    /* Determine Display and Camera Orientation */
    private void getDisplayOrientation() {
        Camera.CameraInfo i = new Camera.CameraInfo();
        Camera.getCameraInfo(mIDCamera, i);
        int degrees = 0;
        int orientation;

        /* Get the Device Rotation */
        switch(getActivity().getWindowManager().getDefaultDisplay().getRotation()) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
        }

        if (i.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            orientation = (i.orientation + degrees) % 360;
            orientation = (360 - orientation) % 360;
        } else {
            orientation = (i.orientation - degrees + 360) % 360;
        }

        mImageParams.DisplayOrientation = orientation;
        mImageParams.LayoutOrientation = degrees;
        mCamera.setDisplayOrientation(mImageParams.DisplayOrientation);
    }

    /* Determine Camera Photo and Preview Resolution */
    private Camera.Size determineBestSize(List<Camera.Size> sizes) {
        Camera.Size r = null;
        Camera.Size size;
        int numOfSizes = sizes.size();
        for (int i = 0; i < numOfSizes; i++) {
            size = sizes.get(i);
            boolean bratio = (size.width / 4) == (size.height / 3);
            boolean bsize = (r == null) || size.width > r.width;
            if (bratio && bsize) {
                r = size;
            }
        }

        if (r == null)
            return sizes.get(sizes.size() - 1);
        else
            return r;
    }

    /* Get the Picture Rotation Factor */
    /* Similar to com.desmond.squarecamera */
    private int getPhotoRotation() {
        int orientation = mOrientationListener.getRememberedNormalOrientation();
        Camera.CameraInfo i = new Camera.CameraInfo();
        Camera.getCameraInfo(mIDCamera, i);

        if (i.facing == Camera.CameraInfo.CAMERA_FACING_FRONT)
            return (i.orientation - orientation + 360) % 360;
        else
            return (i.orientation + orientation) % 360;
    }

    /* Take Picture and set the Safety Parameter off to avoid double capture */
    private void takePicture() {
        if (mSafeToTakePic) {
            mSafeToTakePic = false;
            mOrientationListener.rememberOrientation();
            mCamera.takePicture(null, null, null, this);
        }
    }

    /* Deal with the taken photo and open the Edit Fragment */
    /* Similar to com.desmond.squarecamera */
    @Override
    public void onPictureTaken(byte[] data, Camera c) {
        int rotation = getPhotoRotation();
        getFragmentManager()
                .beginTransaction()
                .replace(
                        R.id.fragment_container,
                        PhotoEditFragment.newInstance(data, rotation, mImageParams.createCopy()),
                        "PhotoEditFragment")
                .addToBackStack(null)
                .commit();

        mSafeToTakePic = true;
    }

    private void getCamera(int ID) {
        try {
            mCamera = Camera.open(ID);
            cameraPreviewer.initCamera(mCamera);
        } catch (Exception e) {
            Log.d("DevelopmentPlayground", "Cannot open camera");
            e.printStackTrace();
        }
    }

    static class ImageParameters implements Parcelable {

        public boolean Portrait;
        public int DisplayOrientation;
        public int LayoutOrientation;
        public int CoverHeight, CoverWidth;
        public int PreviewHeight, PreviewWidth;

        public ImageParameters(Parcel i) {
            Portrait = (i.readByte() == 1);
            DisplayOrientation = i.readInt();
            LayoutOrientation = i.readInt();
            CoverHeight = i.readInt();
            CoverWidth = i.readInt();
            PreviewHeight = i.readInt();
            PreviewWidth = i.readInt();
        }

        public ImageParameters() {}

        public int getAnimationParameter() {
            return Portrait ? CoverHeight : CoverWidth;
        }

        public int calculateCoverSize() {
            return Math.abs(PreviewHeight - PreviewWidth) / 2;
        }

        public int returnCorrectSize() {
            return Portrait ? CoverHeight : CoverWidth;
        }

        public boolean isPortrait() {
            return Portrait;
        }

        public ImageParameters createCopy() {
            ImageParameters imageParameters = new ImageParameters();
            imageParameters.Portrait = Portrait;
            imageParameters.DisplayOrientation = DisplayOrientation;
            imageParameters.LayoutOrientation = LayoutOrientation;
            imageParameters.CoverHeight = CoverHeight;
            imageParameters.CoverWidth = CoverWidth;
            imageParameters.PreviewHeight = PreviewHeight;
            imageParameters.PreviewWidth = PreviewWidth;
            return imageParameters;
        }

         @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {

        }

        public static final Creator<ImageParameters> CREATOR = new Parcelable.Creator<ImageParameters>() {
            @Override
            public ImageParameters createFromParcel(Parcel source) {
                return new ImageParameters(source);
            }

            @Override
            public ImageParameters[] newArray(int size) {
                return new ImageParameters[size];
            }
        };
    }

    private static class CameraOrientationListener extends OrientationEventListener {

        private int mCurrentNormalizedOrientation;
        private int mRememberedNormalOrientation;

        public CameraOrientationListener(Context context) {
            super(context, SensorManager.SENSOR_DELAY_NORMAL);
        }

        @Override
        public void onOrientationChanged(int orientation) {
            if (orientation != ORIENTATION_UNKNOWN) {
                mCurrentNormalizedOrientation = normalize(orientation);
            }
        }

        private int normalize(int degrees) {
            if (degrees > 315 || degrees <= 45) {
                return 0;
            }

            if (degrees > 45 && degrees <= 135) {
                return 90;
            }

            if (degrees > 135 && degrees <= 225) {
                return 180;
            }

            if (degrees > 225 && degrees <= 315) {
                return 270;
            }

            throw new RuntimeException("The physics as we know them are no more. Watch out for anomalies.");
        }

        public void rememberOrientation() {
            mRememberedNormalOrientation = mCurrentNormalizedOrientation;
        }

        public int getRememberedNormalOrientation() {
            rememberOrientation();
            return mRememberedNormalOrientation;
        }
    }

    private class ResizeAnimation extends Animation {
        final int mStartLength;
        final int mFinalLength;
        final boolean mIsPortrait;
        final View mView;

        public ResizeAnimation(@NonNull View view, final CameraFragment.ImageParameters imageParameters) {
            mIsPortrait = imageParameters.isPortrait();
            mView = view;
            mStartLength = mIsPortrait ? mView.getHeight() : mView.getWidth();
            mFinalLength = imageParameters.getAnimationParameter();
        }

        @Override
        protected void applyTransformation(float interpolatedTime, Transformation t) {
            int newLength = (int) (mStartLength + (mFinalLength - mStartLength) * interpolatedTime);

            if (mIsPortrait) {
                mView.getLayoutParams().height = newLength;
            } else {
                mView.getLayoutParams().width = newLength;
            }

            mView.requestLayout();
        }

        @Override
        public void initialize(int width, int height, int parentWidth, int parentHeight) {
            super.initialize(width, height, parentWidth, parentHeight);
        }

        @Override
        public boolean willChangeBounds() {
            return true;
        }
    }
}
