package com.camera.myapplication;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.nfc.Tag;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
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

import java.util.Arrays;

public class MainActivity extends AppCompatActivity implements View.OnClickListener{
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
            }
            // 正式申请权限（第二个参数是权限数组，可同时申请多个权限）
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
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
            CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics("1");
            StreamConfigurationMap map = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            Size[] outputSizes = map.getOutputSizes(SurfaceTexture.class);
            for (int i = 0; i < outputSizes.length; i++) {
                Log.d("sb","适配手机的尺寸"+outputSizes[i]);

            }

            // 1. 从 TextureView 拿到 SurfaceTexture，并设置缓冲区大小
//            SurfaceTexture surfaceTexture = textureView.getSurfaceTexture();
            surfaceTexture.setDefaultBufferSize(
                    textureView.getWidth(),
                    textureView.getHeight()
            );
            Log.d("9","构造surface作为相机输出目标");

            // 2. 用 SurfaceTexture 构造一个 Surface，作为相机输出目标
            Surface surface = new Surface(surfaceTexture);
            Log.d("10","创建预览的 CaptureRequest并进行自动对焦");
            // 3. 创建预览的 CaptureRequest
            final CaptureRequest.Builder previewRequestBuilder =
                    cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewRequestBuilder.addTarget(surface);
            // 自动对焦模式（如果支持）
            previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            Log.d("11","创建预览对话");
            // 4. 创建预览会话，把 Surface 传进去
            cameraDevice.createCaptureSession(
                    Arrays.asList(surface),
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

    @Override
    public void onClick(View v) {

    }
}

