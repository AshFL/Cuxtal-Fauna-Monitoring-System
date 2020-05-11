package com.example.camcuxtal;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.text.Html;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.physicaloid.lib.Physicaloid;
import com.physicaloid.lib.usb.driver.uart.ReadLisener;
import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfFloat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;
import org.opencv.video.Video;

import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2 {
    private static String TAG = "MainActivity";
    //Permissions
    private static final int PERMISSIONS_READ_CAMERA = 1;
    private static final int PERMISSIONS_LISTEN_AUDIO = 2;
    private static final int PERMISSIONS_FINE_LOCATION = 3;
    private static final int PERMISSIONS_WRITE_STORAGE = 4;
    //UI Components
    private CameraBridgeViewBase OpenCVCamView;
    private TextView textView;
    //private MenuItem isItemSwitchCam = null;
    //private int viewMode;
    //OpenCV Data Components
    Mat rgba, rgbaT, rgbaF, intMat, gray, prevGray;
    private MatOfPoint2f prevFeatures, nextFeatures;
    private MatOfPoint features;
    private MatOfByte status;
    private MatOfFloat err;
    private int rows, rowStep, colStep, cols;
    private double motionPercent;
    private boolean motionDetected = false;
    //Physicaloid USB Data Components
    Physicaloid PhysicalLoad;
    Spinner spBaud;
    Handler handler = new Handler();

    //Activate Camera
    private BaseLoaderCallback LoaderCallback = new BaseLoaderCallback (this) {
        @Override
        public void onManagerConnected(int status) {
            if (status == BaseLoaderCallback.SUCCESS) {
                OpenCVCamView.enableView();                                //Enable Cam View
                Log.d(TAG, "Tried enabling Camera View!");
            } else { super.onManagerConnected(status); }
        }
    };

    private void resetVars(){
        prevGray = new Mat (gray.rows(), gray.cols(), CvType.CV_8UC1);
        features = new MatOfPoint();
        prevFeatures = new MatOfPoint2f();
        nextFeatures = new MatOfPoint2f();
        status = new MatOfByte();
        err = new MatOfFloat();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "on Create");
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);                                     //UI Layout loaded
        OpenCVCamView = (CameraBridgeViewBase) findViewById(R.id.cam_view);                                    //Identify cam view
        textView = findViewById(R.id.arduino_log);                                  //Identify Serial Log view
        textView.setVisibility(SurfaceView.VISIBLE);                                //Arduino Serial Log is visible
        OpenCVCamView.setVisibility(SurfaceView.VISIBLE);                               //Cam view is invisible
        //Check Camera Permission
        OpenCVCamView.setCvCameraViewListener(this);                                    //Visual Listener activated
        //Check Permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            if(ActivityCompat.shouldShowRequestPermissionRationale(this,Manifest.permission.CAMERA));
            else { ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, PERMISSIONS_READ_CAMERA); }
        } else {
            Log.d(TAG, "Permissions granted");
            OpenCVCamView.setCameraPermissionGranted();
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            if(ActivityCompat.shouldShowRequestPermissionRationale(this,Manifest.permission.RECORD_AUDIO));
            else { ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, PERMISSIONS_LISTEN_AUDIO); }
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            if(ActivityCompat.shouldShowRequestPermissionRationale(this,Manifest.permission.ACCESS_FINE_LOCATION));
            else { ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSIONS_FINE_LOCATION); }
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            if(ActivityCompat.shouldShowRequestPermissionRationale(this,Manifest.permission.WRITE_EXTERNAL_STORAGE));
            else { ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSIONS_WRITE_STORAGE); }
        }
    }

    @Override
    public void onRequestPermissionsResult( int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode){
            case PERMISSIONS_READ_CAMERA: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    OpenCVCamView.setCameraPermissionGranted();
                } else { this.finish(); }
                return;
            }
            //
//            case PERMISSIONS_LISTEN_AUDIO: {
//                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
//                } else { this.finish(); }
//                return;
//            }
//            case PERMISSIONS_FINE_LOCATION: {
//                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
//                } else { this.finish(); }
//                return;
//            }
//            case PERMISSIONS_WRITE_STORAGE: {
//                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
//                } else { this.finish(); }
//                return;
//            }
            default:{ super.onRequestPermissionsResult(requestCode, permissions, grantResults); }
        }
    }

    @Override
    public void onCameraViewStarted(int width, int height) {
        Log.d(TAG, "on Camera view Started");
        rgba = new Mat (height, width, CvType.CV_8UC4);                         //Create rgba data array
        rgbaF = new Mat (height, width, CvType.CV_8UC4);                        //Create rgbaF data array
        rgbaT = new Mat (height, width, CvType.CV_8UC4);                        //Create rgbaT data array
        intMat = new Mat(height, width, CvType.CV_8UC4);                        //Create intMat data array
        gray = new Mat (height, width, CvType.CV_8UC1);                         //Create grayscale data array
        resetVars();
    }

    @Override
    public void onCameraViewStopped() {
        Log.d(TAG, "on Camera view Stopped");
        rgba.release();                                                         //Clear rgba data
        gray.release();                                                         //Clear grayscale data
        intMat.release();                                                       //Clear intMat data
    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {

        rgba = inputFrame.rgba();                                               //Capture current frame into rgba array
        Log.d(TAG, "on Camera Frame");
        Core.transpose(rgba,rgbaT);
        Imgproc.resize(rgbaT,rgbaF,rgbaF.size(),0,0,0);
        Core.flip(rgbaF,rgba,1);
        gray = inputFrame.gray();                                               //Capture frame into grayscale array
        if (features.toArray().length == 0) {                                   //Convert gray frame to point array
            rowStep = 50;
            colStep = 100;
            rows = gray.rows() / rowStep;
            cols = gray.cols() / colStep;
            Point[] points = new Point[rows * cols];
            for (int i = 0; i < rows; i++) {
                for (int j = 0; j < cols; j++) { points[i * cols + j] = new Point(j*colStep, i*rowStep); }
            }
            features.fromArray(points);
            prevFeatures.fromList(features.toList());                       //Save grayscale array
            prevGray = gray.clone();
        }
        nextFeatures.fromArray(prevFeatures.toArray());
        Video.calcOpticalFlowPyrLK(prevGray, gray, prevFeatures, nextFeatures, status, err);    //Compare current grayscale frame to past grayscale frame
        List<Point> prevList = features.toList(), nextList = nextFeatures.toList();
        Scalar color = new Scalar(255, 0, 0);
        for (int i = 0; i < prevList.size(); i++) { Imgproc.line(gray, prevList.get(i), nextList.get(i), color); }
        Core.absdiff(gray,prevGray,intMat);
        if (intMat.equals(gray)){ motionDetected = false; }
        else {
            motionDetected = true;
            Log.d(TAG,"Motion Detected!");
        }
        prevGray = gray.clone();
        return rgba;                                                    //Test Camera View
    }

    public void serialTerminal(View v) {
        String baud = spBaud.getSelectedItem().toString();                  //Begin serial communication
        switch (baud) {                                                     //Check Baud Rate
            case "300 baud":
                PhysicalLoad.setBaudrate(300);
                break;
            case "1200 baud":
                PhysicalLoad.setBaudrate(1200);
                break;
            case "2400 baud":
                PhysicalLoad.setBaudrate(2400);
                break;
            case "4800 baud":
                PhysicalLoad.setBaudrate(4800);
                break;
            case "19200 baud":
                PhysicalLoad.setBaudrate(19200);
                break;
            case "38400 baud":
                PhysicalLoad.setBaudrate(38400);
                break;
            case "576600 baud":
                PhysicalLoad.setBaudrate(576600);
                break;
            case "744880 baud":
                PhysicalLoad.setBaudrate(744880);
                break;
            case "115200 baud":
                PhysicalLoad.setBaudrate(115200);
                break;
            case "230400 baud":
                PhysicalLoad.setBaudrate(230400);
                break;
            case "250000 baud":
                PhysicalLoad.setBaudrate(250000);
                break;
            default:
                PhysicalLoad.setBaudrate(9600);
        }
        if (PhysicalLoad.open()) {
            PhysicalLoad.addReadListener(new ReadLisener() {                    //Open Arduino Log
                @Override
                public void onRead(int size) {
                    byte[] buf = new byte[size];
                    PhysicalLoad.read(buf, size);
                    tAppend(textView, Html.fromHtml("<font color=blue>" + new String(buf) + "</font>"));
                }
            });
        } else {
            Toast.makeText(this, "Cannot connect to Arduino! Check USB connection", Toast.LENGTH_LONG).show();
        }
    }
    private void tAppend(TextView tv, CharSequence text){                       //Link Arduino Serial log to UI
        final TextView ftv = tv;
        final CharSequence ftext = text;
        handler.post(new Runnable() {
            @Override
            public void run() { ftv.append(ftext); }
        });
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG,"Application process Destroyed!");
        super.onDestroy();
        if(OpenCVCamView != null) { OpenCVCamView.disableView(); }             //Stop camera view
    }

    @Override
    protected void onPause(){
        Log.d(TAG,"Application Paused!");
        super.onPause();
        if(OpenCVCamView != null) { OpenCVCamView.disableView(); }            //Stop camera view
    }
    @Override
    protected void onResume(){
        super.onResume();
        Log.d(TAG,"Application Resumed!");
        if(OpenCVLoader.initDebug()) {                                          //Begin OpenCV debugger
            Log.d(TAG,"OpenCV Loaded Successfully!! :D");
            LoaderCallback.onManagerConnected(BaseLoaderCallback.SUCCESS);
            Toast.makeText(MainActivity.this, "Loaded OpenCV Library", Toast.LENGTH_SHORT).show();
        }else{
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION, this, LoaderCallback);
            Toast.makeText(MainActivity.this, "Failed to load OpenCV Library", Toast.LENGTH_SHORT).show();
            Log.d(TAG,"Failed to load OpenCV!! D:");
        }

    }
}
