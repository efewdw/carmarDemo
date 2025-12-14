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
import android.media.MediaRecorder;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.nfc.Tag;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
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
    private CaptureRequest.Builder recordBuilder;
    private CaptureRequest.Builder previewBuilder;
    private Surface previewSurface;
    //用来设置图片不重复的
    private Integer integer=0;
    private String formattedDate;
    //储存surfaces
    private ArrayList<Surface> arrayList;
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
    private  MediaRecorder mediaRecorder;
    private String videoFilePath;
    private Integer isRecording=0;

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
    private ImageView button;
    private File videoFile;

    private CameraManager cameraManager;
    //长点击的监听
    private View.OnLongClickListener longClickListener=new View.OnLongClickListener() {
        @Override
        public boolean onLongClick(View v) {
            Log.d("w1","长点击事件监听到了");
            //进行判断避免重复请求
            if (isRecording==1){
                try {
                    initializeMediaRecorders();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                isRecording=0;
            }
          if (isRecording==0){
              try {

                  cameraCaptureSession.setRepeatingRequest(recordBuilder.build(),null,handler);
              } catch (CameraAccessException e) {
                  throw new RuntimeException(e);
              }

              mediaRecorder.start();
              isRecording=1;
              Log.d("wd","开始录像了");




              Toast.makeText(MainActivity.this,"开始录制了",Toast.LENGTH_LONG);
          }


            return true;
        }
    };
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        button = (ImageView)findViewById(R.id.btnTakePhoto);
        button.setOnClickListener(this);
        button.setOnLongClickListener(longClickListener);
        cameraHandler=new HandlerThread("camearHandler");
        cameraHandler.start();
        Looper looper = cameraHandler.getLooper();
        handler=new Handler(looper);


        cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        Log.d("1","获取相机管理者");
        //通过main.xml获取textureView
        textureView= (TextureView)findViewById(R.id.textureView);

        Log.d("2","获取相机载体" );


    }

    private final TextureView.SurfaceTextureListener surfaceTextureListener =
            new TextureView.SurfaceTextureListener() {
                @Override
                public void onSurfaceTextureAvailable(SurfaceTexture st, int width, int height) {
                    surfaceTexture=st;
                    Log.d("surfaceTexuuer",""+st+"----"+surfaceTexture);
                    surfaceTexture.setDefaultBufferSize(960,720);
                    // 2. 用 SurfaceTexture 构造一个 Surface，作为相机输出目标
                     previewSurface = new Surface(surfaceTexture);
                    //将surface加入到集合中
                    if (arrayList==null){
                        arrayList=new ArrayList<Surface>();
                    }

                    arrayList.add(previewSurface);
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
if(checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED&&checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED&&
        checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED){
    if(textureView.isAvailable()){
        Log.d("wd","textureview实例完成");

        surfaceTexture = textureView.getSurfaceTexture();
        surfaceTexture.setDefaultBufferSize(1600,1200);
        previewSurface = new Surface(surfaceTexture);
        //将surface加入到集合中
        if (arrayList==null){
            arrayList=new ArrayList<Surface>();
        }
        arrayList.add(previewSurface);
        open();

    }else {
        textureView.setSurfaceTextureListener(surfaceTextureListener);
        Log.d("w","textureView的监听函数");
    }
}else {
    //真正进行申请
    requestPermissions(new String[]{Manifest.permission.CAMERA,Manifest.permission.RECORD_AUDIO,Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE},REQUEST_CAMERA_PERMISSION);
}
//if(textureView.isAvailable()){
//    open();
//}else{
//    Toast.makeText(this,"wgfwf",Toast.LENGTH_SHORT).show();
//}


    }
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        // 第一步：匹配相机权限的请求码（避免和其他权限请求混淆）
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            // 第二步：检查授权结果（必须判空，避免数组越界）
            if (grantResults.length > 0) {
                // 因为只申请了 CAMERA 一个权限，所以取 grantResults[0]
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // 情况1：权限授予成功 → 初始化相机
                    Log.d("CameraPermission", "相机权限授予成功");
                  if(textureView.isAvailable()){
                      open();
                  }else{
                      textureView.setSurfaceTextureListener(surfaceTextureListener);
                  }
                } else {
                    // 情况2：权限授予失败 → 提示用户，可选引导到设置页
                    Log.d("CameraPermission", "相机权限授予失败");
                    Toast.makeText(this, "未授予相机权限，无法使用预览功能", Toast.LENGTH_SHORT).show();

                    // 进阶：判断用户是否“永久拒绝”（不再提示），引导到应用设置页
                    if (!ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
                        // 用户勾选了“不再提示”，弹窗引导到设置页开启权限
                        new AlertDialog.Builder(this)
                                .setTitle("权限申请")
                                .setMessage("需要相机权限才能使用功能，请前往设置开启")
                                .setPositiveButton("去设置", (dialog, which) -> {
                                    // 跳转到应用权限设置页
                                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                    Uri uri = Uri.fromParts("package", getPackageName(), null);
                                    intent.setData(uri);
                                    startActivity(intent);
                                })
                                .setNegativeButton("取消", null)
                                .show();
                    }
                }
            }
        }
    }
//切后台
    @Override
    protected void onPause() {
        super.onPause();
        try {
           if(isRecording==1){
               mediaRecorder.stop();
           }
           if (cameraCaptureSession!=null){
               cameraCaptureSession.stopRepeating();
               cameraCaptureSession.close();
           } if (cameraDevice!=null){
                cameraDevice.close();
                arrayList=null;
            }


//            cameraCaptureSession=null;
//            cameraDevice=null;





        } catch (CameraAccessException e) {
            throw new RuntimeException(e);
        }

    }

    @Override
    protected void onStop() {
        super.onStop();


    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraHandler.quitSafely();


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

                 Log.d("wd",""+surfaceTexture);

                Log.d("6","调用相机api2.0的打开相机操作");
                if(checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED){
                    cameraManager.openCamera("1",cameraCallback,null);
                }

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
        if (arrayList==null){
            arrayList=new ArrayList<Surface>();
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
            //创建mediaRecoder
            initializeMediaRecorders();
            Log.d("wd",""+mediaRecorder);
            Surface surface = mediaRecorder.getSurface();
            Log.d("wd","是否有效"+surface.isValid());
            arrayList.add(surface);
            imageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener(){
                /**
                 * 创建
                 * @param reader the ImageReader the callback is associated with.
                 */

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
//            surfaceTexture.setDefaultBufferSize(
//                    textureView.getWidth(),
//                    textureView.getHeight()
//            );

            Log.d("9","构造surface作为相机输出目标");


             //添加imagereader的surface
            Surface imageReaderSurface = imageReader.getSurface();
            arrayList.add(imageReaderSurface);
            Log.d("10","创建预览的 CaptureRequest并进行自动对焦");
            // 3. 创建预览的 CaptureRequest
           previewBuilder =cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
             recordBuilder =
                    cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            previewBuilder.addTarget(previewSurface);
            recordBuilder.addTarget(surface);
            recordBuilder.addTarget(previewSurface);
            setupRecordingRequest(recordBuilder);
            // 自动对焦模式（如果支持）
            previewBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            Log.d("11","创建预览对话");
            if (cameraDevice == null ) {
                Log.d("wd","相机设备未打开/已关闭");

                return;
            }
            if (previewSurface == null) {
                Log.d("wd","预览Surface为空");

                return;
            }

            // 4. 创建预览,拍照，录像会话，把 Surface 传进去
            cameraDevice.createCaptureSession(
                    arrayList,
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            cameraCaptureSession=session;
                            Log.d("12","相机预览开始设置为连续预览");

//                            try {
//                                Surface surface = mediaRecorder.getSurface();
//                                CaptureRequest.Builder builder = null;
//                                try {
//                                    builder = cameraDevice.createCaptureRequest(
//                                            CameraDevice.TEMPLATE_RECORD);
//                                    Log.d("ed","没有报错");
//                                } catch (CameraAccessException e) {
//                                    throw new RuntimeException(e);
//                                }
//                                builder.addTarget(surface);
//                                setupRecordingRequest(builder);
                            try {
                                session.setRepeatingRequest(previewBuilder.build(), null, handler);
                            } catch (CameraAccessException e) {
                                throw new RuntimeException(e);
                            }
//
//                                // 5. 设置为重复请求，开始连续预览
//                                session.setRepeatingRequest(
//                                        builder.build(),
//                                        null,handler
//                                );
//                                Log.d("12","相机预览成功");
//                            } catch (CameraAccessException e) {
//                                e.printStackTrace();
//                            }
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            // 会话配置失败时的处理
                            Log.d("dw","绘画配置失败444444cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc");
                        }
                    },
                    handler   // 回调运行的线程（后台）
            );

        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (IOException e) {
            throw new RuntimeException(e);
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
    private void initializeMediaRecorders() throws IOException {
        // 步骤1: 如果MediaRecorder已存在，先释放
        if (mediaRecorder != null) {
            mediaRecorder.release();
            mediaRecorder = null;
        }

        // 步骤2: 创建新的MediaRecorder实例
        mediaRecorder = new MediaRecorder();
        Log.d("wd","创建mediarecorder实例成功");

        // 步骤3: 设置音频源 - 使用麦克风录制音频
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);

        // 步骤4: 设置视频源 - 使用Surface作为视频输入源（来自Camera2）
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);

        // 步骤5: 设置输出格式 - 使用MPEG4格式（.mp4文件）
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);

        // 步骤6: 生成视频文件路径并设置
         videoFilePath = getPublicVideoFile().getAbsolutePath();


        mediaRecorder.setOutputFile(videoFilePath);

        // 步骤7: 设置视频编码参数
        // 编码器：H264（广泛兼容的编码格式）
        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        // 视频分辨率：使用之前选择的videoSize960x720
        mediaRecorder.setVideoSize(960, 720);
        // 视频帧率：30fps（流畅的视频帧率）
        mediaRecorder.setVideoFrameRate(30);
        // 视频码率：10Mbps（高质量视频，可根据需要调整）
        mediaRecorder.setVideoEncodingBitRate(10000000);

        // 步骤8: 设置音频编码参数
        // 编码器：AAC（高质量音频编码）
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        // 音频采样率：44100Hz（CD质量）
        mediaRecorder.setAudioSamplingRate(44100);
        // 音频码率：128kbps（高质量音频）
        mediaRecorder.setAudioEncodingBitRate(128000);
// 1. 定义旋转角度→视频方向的映射数组（核心：和屏幕旋转角度对应）
         final int[] ORIENTATIONS = {
                0,    // 屏幕旋转0° → 视频方向0°
                90,   // 屏幕旋转90° → 视频方向90°
                180,  // 屏幕旋转180° → 视频方向180°
                270   // 屏幕旋转270° → 视频方向270°
        };
        // 步骤9: 设置视频方向（根据设备旋转角度）
        int rotation = getDeviceRotation();
   int orientation = ORIENTATIONS[rotation];
        mediaRecorder.setOrientationHint(orientation);

        // 步骤10: 准备MediaRecorder（必须在start()之前调用）
        mediaRecorder.prepare();
    }
    // ========== 方法3: 生成视频文件 ==========
//    /**
//     * 生成视频文件的保存路径
//     * 调用时机：初始化MediaRecorder时
//     * @return 视频文件对象
//     */
//    private File getVideoFile() {
//        // 步骤1: 获取应用的外部文件目录（不需要存储权限）
//        File mediaDir = MainActivity.this.getExternalFilesDir(null);
//
//        // 步骤2: 创建视频子目录（如果不存在）
//        File videoDir = new File(mediaDir, "Videos");
//        if (!videoDir.exists()) {
//            videoDir.mkdirs();
//        }
//
//        // 步骤3: 使用时间戳生成唯一的文件名
//        String fileName = "VIDEO_" + System.currentTimeMillis() + ".mp4";
//
//        // 步骤4: 返回完整的文件路径
//        return new File(videoDir, fileName);
//    }

    @Override
    public void onClick(View v) {
        if(isRecording==1&&mediaRecorder!=null){
            Log.d("wd",""+isRecording);
            Log.d("wd","录像结束了");
            Log.d("wd",videoFilePath);
            mediaRecorder.stop();
            try {
                cameraCaptureSession.setRepeatingRequest(previewBuilder.build(),null,handler);
            } catch (CameraAccessException e) {
                throw new RuntimeException(e);
            }

            scan(videoFile);
            mediaRecorder.reset();
            isRecording=0;

            Toast.makeText(MainActivity.this,"保存录制视频成功",Toast.LENGTH_LONG);
        }
        else {
            CaptureRequest.Builder captureBuilder = null;
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

    // ========== 方法5: 开始录制视频 ==========
    /**
     * 开始视频录制的主方法
     * 调用时机：用户点击录制按钮，且canStartRecording()返回true
     */
    private void startRecordingVideo() {
        // 步骤1: 检查是否可以开始录制



            // 步骤2: 关闭当前的预览会话（必须关闭才能创建新的录制会话）


            // 步骤3: 初始化MediaRecorder（配置所有参数）
//            initializeMediaRecorder();

            // 步骤4: 创建用于录制的CaptureSession


            // 步骤5: 更新UI状态（在UI线程中执行）



    }
    // ========== 方法7: 配置录制请求参数 ==========
    /**
     * 配置录制CaptureRequest的具体参数
     * 调用时机：createRecordingSession()中，创建CaptureRequest.Builder后
     * @param builder CaptureRequest构建器
     */
    private void setupRecordingRequest(CaptureRequest.Builder builder) {
        // 步骤1: 设置自动对焦模式 - 连续自动对焦（适合视频录制）
        builder.set(CaptureRequest.CONTROL_AF_MODE,
                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO);

        // 步骤2: 设置自动曝光模式 - 自动曝光（根据环境光自动调整）
        builder.set(CaptureRequest.CONTROL_AE_MODE,
                CaptureRequest.CONTROL_AE_MODE_ON);

        // 步骤3: 设置自动白平衡模式 - 自动白平衡（根据环境光调整色温）
        builder.set(CaptureRequest.CONTROL_AWB_MODE,
                CaptureRequest.CONTROL_AWB_MODE_AUTO);

        // 步骤4: 设置图像稳定模式 - 如果设备支持，启用防抖
        builder.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,
                CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON);

        // 步骤5: 设置控制模式 - 自动模式（让系统自动优化参数）
        builder.set(CaptureRequest.CONTROL_MODE,
                CaptureRequest.CONTROL_MODE_AUTO);
    }
    // ========== 方法8: 停止录制视频 ==========
    /**
     * 停止视频录制的主方法
     * 调用时机：用户点击停止按钮
     */
//    private void stopRecordingVideo() {
//        // 步骤1: 检查是否正在录制
//        if (!isRecording) {
//            return; // 没有在录制，直接返回
//        }
//
//        // 步骤2: 更新录制状态标志（先更新，避免重复调用）
//        isRecording = false;
//
//        try {
//            // 步骤3: 停止CaptureSession的重复请求（不再捕获新帧）
//            if (cameraCaptureSession != null) {
//                cameraCaptureSession.stopRepeating();
//                // 步骤4: 中止所有正在进行的捕获操作
//                cameraCaptureSession.abortCaptures();
//            }
//
//            // 步骤5: 停止MediaRecorder（完成视频文件写入）
//            if (mediaRecorder != null) {
//                mediaRecorder.stop();
//            }
//
//            // 步骤6: 重置MediaRecorder（清理状态，准备下次使用）
//            if (mediaRecorder != null) {
//                mediaRecorder.reset();
//            }
//
//            // 步骤7: 显示保存成功提示（在UI线程中执行）
//
//
//            // 步骤8: 关闭录制会话
//
//
//            // 步骤9: 重新启动预览（恢复正常的预览功能）
//
//
//            // 步骤10: 更新UI状态（在UI线程中执行）
//
//
//        } catch (Exception e) {
//            // 步骤11: 处理停止录制时的异常
//
//        }
//    }
    // ========== 方法13: 获取设备旋转角度 ==========
    /**
     * 获取当前设备的旋转角度
     * 调用时机：初始化MediaRecorder时，设置视频方向
     * @return 旋转角度索引（0=竖屏, 1=横屏, 2=倒置, 3=反向横屏）
     */
    private int getDeviceRotation() {
        // 步骤1: 获取窗口管理器
        android.view.WindowManager windowManager =
                (android.view.WindowManager) MainActivity.this.getSystemService(Context.WINDOW_SERVICE);

        // 步骤2: 获取当前显示旋转角度
        int rotation = windowManager.getDefaultDisplay().getRotation();

        // 步骤3: 返回旋转角度（0-3对应0度、90度、180度、270度）
        return rotation;
    }
    /**
     * 华为机型专属：强制触发媒体扫描+图库刷新
     */
    private void scan(File videoFile) {
        if (videoFile == null || !videoFile.exists()) return;

        // 方式1：基础媒体扫描（通用）
        MediaScannerConnection.scanFile(MainActivity.this,
                new String[]{videoFile.getAbsolutePath()},
                new String[]{"video/mp4"},
                (path, uri) -> {
                    Log.d("ScanHuaWei", "基础扫描完成，URI：" + uri);
                    // 方式2：华为专属：发送图库刷新广播
                    Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                    mediaScanIntent.setData(Uri.fromFile(videoFile));
                    MainActivity.this.sendBroadcast(mediaScanIntent);
                    Log.d("ScanHuaWei", "已发送图库刷新广播");
                });

        // 方式3：Android 10+ 额外插入MediaStore（兜底）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Video.Media.DISPLAY_NAME, videoFile.getName());
            values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
            values.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES);
            values.put(MediaStore.Video.Media.SIZE, videoFile.length());
            values.put(MediaStore.Video.Media.IS_PENDING, 0);
            MainActivity.this.getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
            Log.d("ScanHuaWei", "Android 10+ 插入MediaStore完成");
        }
    }
    /**
     * 获取公共「我的视频」目录路径（图库可见）
     */
    private File getPublicVideoFile() {
        // 步骤1：获取系统公共「我的视频」目录
        File publicVideoDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES);
        // 也可使用 DIRECTORY_VIDEO（部分手机显示为「视频」文件夹）
        // File publicVideoDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_VIDEO);

        // 步骤2：确保目录存在
        if (!publicVideoDir.exists()) {
            publicVideoDir.mkdirs();
        }

        // 步骤3：生成唯一文件名
        String fileName = "VIDEO_" + System.currentTimeMillis() + ".mp4";

        // 步骤4：返回完整文件路径
         videoFile = new File(publicVideoDir, fileName);
        Log.d("PublicVideoPath", "公共目录路径：" + videoFile.getAbsolutePath());
        return videoFile;
    }

}

