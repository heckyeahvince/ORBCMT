package ph.edu.dlsu.cmtorb;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.WindowManager;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import java.util.concurrent.atomic.AtomicReference;

public class CameraActivity extends Activity implements
        CvCameraViewListener2 {
    private static final String TAG = "CameraActivity";

    private static final int VIEW_MODE_RGBA     = 0;
    private static final int START_CMT          = 1;
    private static final int VIEW_MODE_CMT      = 2;

    static final int WIDTH = 320; // 400 ; //240;
    static final int HEIGHT =240; // 240 ; // 135;

    private int _canvasImgYOffset;
    private int _canvasImgXOffset;

    static boolean uno = true;

    private int mViewMode;
    private Mat mRgba;
    private Mat mIntermediateMat;
    private Mat mGray;

    private MenuItem mItemPreviewRGBA;
    private MenuItem mItemPreviewCMT;
    private MenuItem mItemPreviewSave;
    private MenuItem mItemPreviewLoad;

    private OpenCvCameraView mOpenCvCameraView;
    SurfaceHolder _holder;

    private Rect _trackedBox = null;

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS: {
                    Log.i(TAG, "OpenCV loaded successfully");

                    // Load native library after(!) OpenCV initialization
                    System.loadLibrary("opencv_native_module");

                    mOpenCvCameraView.enableView();
                    mOpenCvCameraView.enableFpsMeter();

                }
                break;
                default: {
                    super.onManagerConnected(status);
                }
                break;
            }
        }
    };

    public CameraActivity() {
        Log.i(TAG, "Instantiated new " + this.getClass());

    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "called onCreate");
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.opencv_surface_view);

        mOpenCvCameraView = (OpenCvCameraView) findViewById(R.id.camera_activity_surface_view);
        mOpenCvCameraView.setCvCameraViewListener(this);

        final AtomicReference<Point> trackedBox1stCorner = new AtomicReference<Point>();

        final Paint rectPaint = new Paint();
        rectPaint.setColor(Color.rgb(0, 255, 0));
        rectPaint.setStrokeWidth(5);
        rectPaint.setStyle(Style.STROKE);
        _holder = mOpenCvCameraView.getHolder();

        mOpenCvCameraView.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                // re-init

                final Point corner = new Point(
                        event.getX() - _canvasImgXOffset, event.getY()
                        - _canvasImgYOffset);
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        trackedBox1stCorner.set(corner);
                        Log.i("TAG", "1st corner: " + corner);
                        break;
                    case MotionEvent.ACTION_UP:
                        _trackedBox = new Rect(trackedBox1stCorner.get(), corner);
                        if (_trackedBox.area() > 100) {
                            Log.i("TAG", "Tracked box DEFINED: " + _trackedBox);
                                 mViewMode = START_CMT;

                        } else
                            _trackedBox = null;
                        break;
                    case MotionEvent.ACTION_MOVE:
                        final android.graphics.Rect rect = new android.graphics.Rect(
                                (int) trackedBox1stCorner.get().x
                                        + _canvasImgXOffset,
                                (int) trackedBox1stCorner.get().y
                                        + _canvasImgYOffset, (int) corner.x
                                + _canvasImgXOffset, (int) corner.y
                                + _canvasImgYOffset);
                        final Canvas canvas = _holder.lockCanvas(rect);
                        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
                        // remove old rectangle
                        canvas.drawRect(rect, rectPaint);
                        _holder.unlockCanvasAndPost(canvas);

                        break;
                }

                return true;
            }
        });

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        Log.i(TAG, "called onCreateOptionsMenu");
        mItemPreviewRGBA = menu.add("RGBA");
        mItemPreviewCMT =  menu.add("CMT");
        mItemPreviewSave = menu.add("Save"); // #TODO
        mItemPreviewLoad = menu.add("Load"); // #TODO

        // mOpenCvCameraView.setResolution(640, 480);

        return true;
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }


    @Override
    public void onResume()
    {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, mLoaderCallback);
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    public void onDestroy() {
        super.onDestroy();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    public void onCameraViewStarted(int width, int height) {
        mRgba = new Mat(height, width, CvType.CV_8UC4);
        mIntermediateMat = new Mat(height, width, CvType.CV_8UC4);
        mGray = new Mat(height, width, CvType.CV_8UC1);
    }

    public void onCameraViewStopped() {
        mRgba.release();
        mGray.release();
        mIntermediateMat.release();
    }

    public Mat onCameraFrame(CvCameraViewFrame inputFrame) {
        final int viewMode = mViewMode;

        switch (viewMode) {

            case VIEW_MODE_RGBA:
                // input frame has RBGA format
                mRgba = inputFrame.rgba();
                break;

            case START_CMT:
            {
                mRgba = inputFrame.rgba();
                mGray = Reduce(inputFrame.gray());
                double w = mGray.width();
                double h = mGray.height();
                if (_trackedBox == null)
                    OpenCMT(mGray.getNativeObjAddr(), mRgba.getNativeObjAddr(),
                            (long) (w / 2 - w / 4), (long) (h / 2 - h / 4),
                            (long) w / 2, (long) h / 2);
                else {

                    Log.i("TAG", "START DEFINED: " + _trackedBox.x / 2 + " "
                            + _trackedBox.y / 2 + " " + _trackedBox.width / 2 + " "
                            + _trackedBox.height / 2);

                    double px = (w) / (double) (mOpenCvCameraView.getWidth());
                    double py = (h) / (double) (mOpenCvCameraView.getHeight());

                    OpenCMT(mGray.getNativeObjAddr(), mRgba.getNativeObjAddr(),
                            (long) (_trackedBox.x * px),
                            (long) (_trackedBox.y * py),
                            (long) (_trackedBox.width * px),
                            (long) (_trackedBox.height * py));
                }
                uno = false;
                mViewMode = VIEW_MODE_CMT;
            }

            break;


            case VIEW_MODE_CMT:
                // input frame has RGBA format
            {
                mRgba = inputFrame.rgba();
                mGray = inputFrame.gray();
                mGray = Reduce(mGray);

                Mat mRgba2 = ReduceColor(mRgba);

                if (uno) {
                    int w = mGray.width();
                    int h = mGray.height();
                    OpenCMT(mGray.getNativeObjAddr(), mRgba.getNativeObjAddr(),
                            (long) w - w / 4, (long) h / 2 - h / 4, (long) w / 2,
                            (long) h / 2);
                    uno = false;
                } else {

                    ProcessCMT(mGray.getNativeObjAddr(), mRgba2.getNativeObjAddr());
                    double px = (double) mRgba.width() / (double) mRgba2.width();
                    double py = (double) mRgba.height() / (double) mRgba2.height();

                    int[] l = CMTgetRect();
                    if (l != null) {
                        Point topLeft = new Point(l[0] * px, l[1] * py);
                        Point topRight = new Point(l[2] * px, l[3] * py);
                        Point bottomLeft = new Point(l[4] * px, l[5] * py);
                        Point bottomRight = new Point(l[6] * px, l[7] * py);

                        Imgproc.line(mRgba, topLeft, topRight, new Scalar(255, 255,
                                255), 3);
                        Imgproc.line(mRgba, topRight, bottomRight, new Scalar(255,
                                255, 255), 3);
                        Imgproc.line(mRgba, bottomRight, bottomLeft, new Scalar(255,
                                255, 255), 3);
                        Imgproc.line(mRgba, bottomLeft, topLeft, new Scalar(255, 255,
                                255), 3);

                    }
                    uno = false;

                }
            }
            break;

        }

        return mRgba;
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        Log.i(TAG, "called onOptionsItemSelected; selected item: " + item);

        if (item == mItemPreviewRGBA) {
            mViewMode = VIEW_MODE_RGBA;
        } else if (item == mItemPreviewCMT) {
            mViewMode = START_CMT;
            _trackedBox = null;
            uno = true;
        }else if (item == mItemPreviewSave) {
            CMTSave(Environment.getExternalStorageDirectory().getPath()+"/Model.yml");
        }else if (item == mItemPreviewLoad) {
            CMTLoad(Environment.getExternalStorageDirectory().getPath()+"/Model.yml");
            uno = false;
            mViewMode = VIEW_MODE_CMT;
        }
        return true;
    }



    Mat Reduce(Mat m) {
        // return m;
        Mat dst = new Mat();
        Imgproc.resize(m, dst, new org.opencv.core.Size(WIDTH, HEIGHT));
        return dst;
    }

    Mat ReduceColor(Mat m) {
        Mat dst = new Mat();
        Bitmap bmp = Bitmap.createBitmap(m.width(), m.height(),
                Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(m, bmp);
        Bitmap bmp2 = Bitmap.createScaledBitmap(bmp, WIDTH, HEIGHT, false);

        Utils.bitmapToMat(bmp2, dst);
        return dst;
    }

    Mat UnReduceColor(Mat m, int w, int h) {
        // return m;

        Mat dst = new Mat();
        Bitmap bmp = Bitmap.createBitmap(m.width(), m.height(),
                Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(m, bmp);
        Bitmap bmp2 = Bitmap.createScaledBitmap(bmp, w, h, false);

        Utils.bitmapToMat(bmp2, dst);

        m.release();
        return dst;
    }


    public native void OpenCMT(long matAddrGr, long matAddrRgba, long x,
                               long y, long w, long h);

    public native void ProcessCMT(long matAddrGr, long matAddrRgba);

    public native void CMTSave(java.lang.String Path);

    public native void CMTLoad(java.lang.String Path);

    private static native int[] CMTgetRect();

}