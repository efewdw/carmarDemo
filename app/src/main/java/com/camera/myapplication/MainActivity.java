package com.camera.myapplication;

import static android.content.ContentValues.TAG;

import android.Manifest;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.icu.text.SimpleDateFormat;
import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.nfc.Tag;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.sql.Date;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;

/**
 * todo:相机具体流程如下：
 * todo:1.首先检查自身权限是否被授权2.绑定textureView的监听函数直到被真正创建完成后再进行打开相机操作
 * todo:3.代开相机执行后是由一个回调函数来接收执行后的结果的需要一个handler和CameraDevice.StateCallback，相机的id
 * todo:4.然后在回调函数中实现相机预览的过程，手机拍照的请求什么的也可以创建在这个地方
 */

/**
 *
 */

public class MainActivity extends AppCompatActivity implements View.OnClickListener{
    //用来设置图片不重复的
    private Integer integer=0;
    private String formattedDate;
    //储存surfaces
    private ArrayList<Surface> arrayList=new ArrayList();
    //设置用来读取图片的载体
    private ImageReader imageReader;
    private Handler handler;
    //会话
    private CameraCaptureSession cameraCaptureSession;
    private SurfaceTexture surfaceTexture;
    //相机预览线程后续所有东西都在这个线程中进行
    private HandlerThread cameraHandler;
    //正在使用的相机id
    private Integer carmarId;
    private TextureView textureView;
    private volatile CameraDevice cameraDevice;

    CameraDevice.StateCallback cameraCallback = new CameraDevice.StateCallback() {



        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {

            Log.d("carmar","连接断开");
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            Log.d("carmar","相机回调失败");
        }

        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            Log.d("7","打开相机回调成功并保存cameraDevice");
            String id = camera.getId();
            Log.d("id",id);
            cameraDevice=camera;
            startPreview(surfaceTexture);



        }

    };
    private static final int REQUEST_CAMERA_PERMISSION = 1001;
    private Button button;

    private CameraManager cameraManager;
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        button = (Button)findViewById(R.id.btnTakePhoto);
        button.setOnClickListener(this);

        cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        Log.d("1","获取相机管理者");
        //通过main.xml获取textureView
        textureView= (TextureView)findViewById(R.id.textureView);
        textureView.setSurfaceTextureListener(surfaceTextureListener);

        Log.d("2","获取相机载体" );
        try {
            checkAndRequestCameraPermission();
            Log.d("3","检查是否拥有权限，如果无则需要用户授权,有则开始执行打开相机操作");

        } catch (CameraAccessException e) {
            throw new RuntimeException(e);
        }

    }

    private final TextureView.SurfaceTextureListener surfaceTextureListener =
            new TextureView.SurfaceTextureListener() {
                @Override
                public void onSurfaceTextureAvailable(SurfaceTexture st, int width, int height) {
                    surfaceTexture=st;
                    open();



                }

                @Override public void onSurfaceTextureSizeChanged(SurfaceTexture st, int w, int h) {}
                @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture st) { return true; }
                @Override public void onSurfaceTextureUpdated(SurfaceTexture st) {}
            };
    //切前台
    @Override
    protected void onResume() {
super.onResume();
if(textureView.isAvailable()){
    open();
}else{
    Toast.makeText(this,"wgfwf",Toast.LENGTH_SHORT).show();
}


    }
//切后台
    @Override
    protected void onPause() {
        super.onPause();
        try {
            cameraCaptureSession.stopRepeating();
            cameraCaptureSession.close();
            cameraDevice.close();
            cameraCaptureSession=null;
            cameraDevice=null;
            cameraHandler.quitSafely();
            cameraHandler=null;



        } catch (CameraAccessException e) {
            throw new RuntimeException(e);
        }

    }

    @Override
    protected void onStop() {
        super.onStop();


    }

    /**
     * 检查并申请相机权限
     */
    private void checkAndRequestCameraPermission() throws CameraAccessException {
        // 步骤1：检查权限是否已授予（安卓6.0+方法）
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            // 权限已授予，直接执行相机操作
            String[] cameraIdList = cameraManager.getCameraIdList();
            Log.d("4","获取到的相机id"+ Arrays.toString(cameraIdList));
            open();


        } else {
            // 步骤2：权限未授予，向用户申请
            // shouldShowRequestPermissionRationale：判断是否需要向用户解释为什么需要该权限
            if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                // 可选：弹出对话框，向用户说明需要相机权限的原因（比如“需要相机权限才能拍照”）
                Toast.makeText(this, "需要相机权限才能使用拍照功能，请允许", Toast.LENGTH_SHORT).show();
                // 正式申请权限（第二个参数是权限数组，可同时申请多个权限）
                requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
            }

        }
    }
    /**
     * 获取到相机id之后
     * 后续相机的打开等操作
     */
    public void open() {
        try {
            // 枚举相机ID（测试相机权限是否生效）
            String[] cameraIds = cameraManager.getCameraIdList();
            if (cameraIds.length > 0) {
                Toast.makeText(this, "相机权限已授予，检测到" + cameraIds.length + "个相机", Toast.LENGTH_SHORT).show();
                Log.d("CameraPermission", "可用相机ID：" + cameraIds[0]);
                // 后续可调用 cameraManager.openCamera(...) 打开相机
                Log.d("5","相机handler线程的创建");
               cameraHandler =new HandlerThread("wps");
               cameraHandler.start();
                 handler = new Handler(cameraHandler.getLooper());//todo
                Log.d("6","调用相机api2.0的打开相机操作");
                cameraManager.openCamera("1",cameraCallback,handler);
            } else {
                Toast.makeText(this, "设备无可用相机", Toast.LENGTH_SHORT).show();
            }
        } catch (CameraAccessException e) {
            Log.e("CameraPermission", "打开相机失败：" + e.getMessage());
            Toast.makeText(this, "打开相机失败", Toast.LENGTH_SHORT).show();
        }
    }
    /**
     * 相机预览方法
     * 通过carmarDevice获取
     */
    private void startPreview(SurfaceTexture surfaceTexture) {
        if (cameraDevice == null || !textureView.isAvailable()){
            Log.d("debug","没有获取到camerDevice");
            return;
        }


        try {
            Log.d("8","开始设置缓冲区大小");
            /**
             * 这个方法可以获取手机可以适配的像素，并进行输出到log中
             */
            CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics("1");
            StreamConfigurationMap map = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            Size[] outputSizes = map.getOutputSizes(SurfaceTexture.class);
            for (int i = 0; i < outputSizes.length; i++) {
                Log.d("sb","适配手机的尺寸"+outputSizes[i]);

            }
            /**
             *
             */
            Log.d("imageReader","创建imageReader用于接受照片");
            imageReader=ImageReader.newInstance(textureView.getWidth(),textureView.getHeight(), ImageFormat.JPEG,1);
            imageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener(){

                @Override
                public void onImageAvailable(ImageReader reader) {
                    Image image = reader.acquireLatestImage();
                    if (image == null) {
                        return;
                    }

                    ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                    byte[] bytes = new byte[buffer.remaining()];
                    buffer.get(bytes);
                    new Thread(() -> {
                        try {
                            File dir = new File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "Camera");
                            if (!dir.exists()) dir.mkdirs();
                            String fileName="wdw_"+integer+"jpg";
//                            String fileName = "IMG_" +
//                                    new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()) +
//                                    ".jpg";
                            File file = new File(dir, fileName);

                            FileOutputStream fos = new FileOutputStream(file);
                            fos.write(bytes);
                            fos.close();

                            String filePath = file.getAbsolutePath();
                            Log.d(TAG, "照片保存路径: " + filePath);

                            // Android 10+ 添加到 MediaStore（相册可见）
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                saveToMediaStore(bytes, fileName);
                            } else {
                                                                        // Android 9及以下：添加到媒体库
                                MediaStore.Images.Media.insertImage(
                                        getContentResolver(),
                                        filePath,
                                        fileName,
                                        null
                                );

                                // 通知相册更新
                                sendBroadcast(new Intent(
                                        Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
                                        Uri.fromFile(file)
                                ));
                            }

                            runOnUiThread(() ->
                                            Toast.makeText(MainActivity.this, "照片已保存到相册", Toast.LENGTH_SHORT).show()
                            );

                        } catch (IOException e) {
                            Log.e(TAG, "保存失败", e);
                        } finally {
                            image.close();
                        }
                    }).start();
                }
                },handler);
                    // 1. 从 TextureView 拿到 SurfaceTexture，并设置缓冲区大小
//            SurfaceTexture surfaceTexture = textureView.getSurfaceTexture();
            surfaceTexture.setDefaultBufferSize(
                    textureView.getWidth(),
                    textureView.getHeight()
            );
            Log.d("9","构造surface作为相机输出目标");

            // 2. 用 SurfaceTexture 构造一个 Surface，作为相机输出目标
            Surface previewSurface = new Surface(surfaceTexture);
            //将surface加入到集合中
             arrayList.add(previewSurface);
             //添加imagereader的surface
            Surface imageReaderSurface = imageReader.getSurface();
            arrayList.add(imageReaderSurface);
            Log.d("10","创建预览的 CaptureRequest并进行自动对焦");
            // 3. 创建预览的 CaptureRequest
            final CaptureRequest.Builder previewRequestBuilder =
                    cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewRequestBuilder.addTarget(previewSurface);
            // 自动对焦模式（如果支持）
            previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            Log.d("11","创建预览对话");
            // 4. 创建预览会话，把 Surface 传进去
            cameraDevice.createCaptureSession(
                    arrayList,
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            cameraCaptureSession=session;
                            Log.d("12","相机预览开始设置为连续预览");

                            try {

                                // 5. 设置为重复请求，开始连续预览
                                session.setRepeatingRequest(
                                        previewRequestBuilder.build(),
                                        null,
                                        handler
                                );
                                Log.d("12","相机预览成功");
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            // 会话配置失败时的处理
                        }
                    },
                    handler   // 回调运行的线程（后台）
            );

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
    /**
     * 将照片保存到 MediaStore（Android 10+ 推荐方式，照片会出现在相册中）
     *
     * @param imageBytes 图片字节数组
     * @param displayName 显示名称（文件名）
     */

    private void saveToMediaStore(byte[] imageBytes, String displayName) {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, displayName);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Camera");

        // 可选：添加日期信息
        values.put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000);
        values.put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis());

        Uri uri = getContentResolver().insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                values
        );

        if (uri != null) {
            try {
                OutputStream outputStream = getContentResolver().openOutputStream(uri);
                if (outputStream != null) {
                    outputStream.write(imageBytes);
                    outputStream.close();

                    Log.d(TAG, "照片已保存到MediaStore: " + uri.toString());

                    // 可选：在主线程显示提示
                    runOnUiThread(() -> {
                        Toast.makeText(this, "照片已保存到相册", Toast.LENGTH_SHORT).show();
                    });
                } else {
                    Log.e(TAG, "无法打开输出流");
                }
            } catch (IOException e) {
                Log.e(TAG, "保存到MediaStore失败", e);
            }
        } else {
            Log.e(TAG, "无法创建MediaStore条目");
        }
    }

    @Override
    public void onClick(View v) {
        CaptureRequest.Builder captureBuilder =
                null;
        try {
            captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
        } catch (CameraAccessException e) {
            throw new RuntimeException(e);
        }
        captureBuilder.addTarget(imageReader.getSurface());
        captureBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

// 设置拍照方向
        try {
            CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics("1");
            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION,
                    getOrientation(rotation,cameraCharacteristics));
        } catch (CameraAccessException e) {
            throw new RuntimeException(e);
        }


// 执行拍照
        try {
            cameraCaptureSession.capture(captureBuilder.build(),
                    new CameraCaptureSession.CaptureCallback() {
                        @Override
                        public void onCaptureCompleted(CameraCaptureSession session,
                                                       CaptureRequest request, TotalCaptureResult result) {
                            // 拍照完成
                        }
                    }, handler);
        } catch (CameraAccessException e) {
            throw new RuntimeException(e);
        }

    }
    /**
     * 计算拍照时图片应该旋转的角度
     *
     * @param rotation 屏幕旋转角度（Display.getRotation()）
     * @param characteristics 相机特性
     * @return JPEG图片旋转角度（0, 90, 180, 270）
     */
    private int getOrientation(int rotation, CameraCharacteristics characteristics) {
        // 1. 获取相机传感器方向
        int sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);

        // 2. 将旋转角度转换为度数（rotation: 0=0°, 1=90°, 2=180°, 3=270°）
        int degrees = rotation * 90;

        // 3. 判断是否为前置摄像头
        Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
        boolean isFrontFacing = (facing != null &&
                facing == CameraCharacteristics.LENS_FACING_FRONT);

        // 4. 根据前置/后置计算最终角度
        if (isFrontFacing) {
            // 前置摄像头：需要镜像处理
            degrees = (sensorOrientation + degrees) % 360;
            degrees = (360 - degrees) % 360;
        } else {
            // 后置摄像头
            degrees = (sensorOrientation - degrees + 360) % 360;
        }

        return degrees;
    }
}

