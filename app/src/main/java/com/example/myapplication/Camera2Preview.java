package com.example.myapplication;

import static android.graphics.ImageFormat.JPEG;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Arrays;

public class Camera2Preview extends AppCompatActivity {

    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;

    private HandlerThread httpHandlerThread;

    private Handler httpBackgroundHandler;

    private Handler mainHandler;
    private ImageReader imageReader;

    private ImageView imageView;

    private Socket socket;

    boolean haveSendHeader;

    boolean isSenDing = false;
    byte[] latestImageBytes;
    boolean fresh = false;
    Thread thread = new Thread(new Runnable() {
        @Override
        public void run() {
            while (true){
                if (latestImageBytes != null && fresh){
                    fresh = false;
                    sendImage(latestImageBytes);
                }else {
                    Thread.yield();
                }
            }
        }
    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        imageView = findViewById(R.id.my_image_view);
        startBackgroundThread();
        thread.start();
        // 创建主线程的Handler
        mainHandler = new Handler(Looper.getMainLooper());
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermission(Manifest.permission.CAMERA, 100);
        }
        setUpCamera();
    }

    private void setSocket(){
        String serverIpAddress = "192.168.18.208"; // 服务器IP地址
        int serverPort = 8080; // 服务器端口
        try {
            socket = new Socket(serverIpAddress, serverPort);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    private void setLatestImage(byte[] latestImageBytes){
        this.latestImageBytes = latestImageBytes;
        this.fresh = true;

    }

    private void sendImage1(byte[] bytes){
        try {
            long start = System.currentTimeMillis();
            isSenDing = true;
            String CRLF = "\r\n";
            OutputStream outputStream = null;
            String serverIpAddress = "192.168.18.208"; // 服务器IP地址
            int serverPort = 8080; // 服务器端口
            try {
                socket = new Socket(serverIpAddress, serverPort);
                socket.setTcpNoDelay(true);
            } catch (IOException e) {
            }

            outputStream = socket.getOutputStream();
            StringBuilder builder = new StringBuilder();
            builder.append("POST /upload HTTP/1.1").append(CRLF);
            builder.append("Host: 192.168.18.208").append(CRLF);
            builder.append("Transfer-Encoding: chunked").append(CRLF);
            builder.append("Connection: keep-alive").append(CRLF).append(CRLF);
            outputStream.write(builder.toString().getBytes());
            outputStream.flush();

            int len = bytes.length;
            len = len+3;
            String lenHex = Integer.toHexString(len);

            outputStream.write(lenHex.getBytes());
            outputStream.write(CRLF.getBytes());
            outputStream.write(bytes);
            outputStream.write("end".getBytes());
            outputStream.write(CRLF.getBytes());
            outputStream.flush();
            outputStream.close();
            socket.close();
            long end = System.currentTimeMillis();
            Log.i("send image ", "发送成功,图片大小"+bytes.length/1024+" kb"+" 耗时："+(end  - start));
            isSenDing = false;
        } catch (IOException e) {

        }
    }

    private void sendImage(byte[] bytes){
        try {
            long start = System.currentTimeMillis();
            isSenDing = true;
            String CRLF = "\r\n";
            OutputStream outputStream;
            if(!haveSendHeader){
                setSocket();
                outputStream = socket.getOutputStream();
                socket.setSendBufferSize(70*1024);
                socket.setTcpNoDelay(true);
                StringBuilder builder = new StringBuilder();
                builder.append("POST /upload HTTP/1.1").append(CRLF);
                builder.append("Host: 192.168.18.208").append(CRLF);
                builder.append("Transfer-Encoding: chunked").append(CRLF);
                builder.append("Connection: keep-alive").append(CRLF).append(CRLF);
                outputStream.write(builder.toString().getBytes());
                outputStream.flush();
                haveSendHeader = true;
            }else{
                outputStream = socket.getOutputStream();
            }
            int len = bytes.length;
            len = len+3;
            String lenHex = Integer.toHexString(len);

            outputStream.write(lenHex.getBytes());
            outputStream.write(CRLF.getBytes());
            outputStream.write(bytes);
            outputStream.write("end".getBytes());
            outputStream.write(CRLF.getBytes());
            outputStream.flush();

            long end = System.currentTimeMillis();
            Log.i("send image ", "发送成功,图片大小"+bytes.length/1024+" kb"+" 耗时："+(end  - start));
            isSenDing = false;
        } catch (IOException e) {
        }
    }

    SocketChannel socketChannel;
    private void sendImage3(byte[] bytes){
        try {
            long start = System.currentTimeMillis();
            isSenDing = true;
            String CRLF = "\r\n";
            if(!haveSendHeader){
                socketChannel = SocketChannel.open();
                // 连接到服务器
                socketChannel.connect(new InetSocketAddress("192.168.18.208", 8080));
                socketChannel.socket().setTcpNoDelay(true);
                // 设置接收缓冲区大小
//                socketChannel.socket().setReceiveBufferSize(1024); // 设置为1MB
//                socketChannel.socket().setSendBufferSize(512); // 设置为1MB

                StringBuilder builder = new StringBuilder();
                builder.append("POST /upload HTTP/1.1").append(CRLF);
                builder.append("Host: 192.168.18.208").append(CRLF);
                builder.append("Transfer-Encoding: chunked").append(CRLF);
                builder.append("Connection: keep-alive").append(CRLF).append(CRLF);

                // 将字符串转换为字节
                byte[] messageBytes = builder.toString().getBytes();
                // 将字节数组转换为ByteBuffer
                ByteBuffer headBuffer = ByteBuffer.wrap(messageBytes);

                // 循环写入直到ByteBuffer中没有剩余字节
                while (headBuffer.hasRemaining()) {
                    socketChannel.write(headBuffer);
                }
                haveSendHeader = true;
            }
            int len = bytes.length;
            len = len+3;

            String lenHex = Integer.toHexString(len);

            int size = len+lenHex.getBytes().length+2+2;
            ByteBuffer buffer = ByteBuffer.allocate(size);

            buffer.put(lenHex.getBytes());
            buffer.put(CRLF.getBytes());
            buffer.put(bytes);
            buffer.put("end".getBytes());
            buffer.put(CRLF.getBytes());

            buffer.flip();
            while (buffer.hasRemaining()) {
                socketChannel.write(buffer);
            }

            long end = System.currentTimeMillis();
            System.out.println("send image 发送成功,图片大小"+bytes.length/1024+" kb"+" 耗时："+(end  - start));
            isSenDing = false;
        } catch (IOException e) {
        }
    }


    private void requestPermission(String permission, int requestCode) {
        ActivityCompat.requestPermissions(this, new String[]{permission}, requestCode);
    }

    private void setUpCamera() {
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            String cameraId = manager.getCameraIdList()[0];
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            Size[] jpegSizes = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(JPEG);

            // 选择一个较小的尺寸，例如VGA
            Size lowResSize = new Size(640, 480);
//            imageReader = ImageReader.newInstance(jpegSizes[0].getWidth(), jpegSizes[0].getHeight(), JPEG, 1);
            imageReader = ImageReader.newInstance(lowResSize.getWidth(), lowResSize.getHeight(), ImageFormat.JPEG, 1);
            imageReader.setOnImageAvailableListener(reader -> {
                Image image = reader.acquireLatestImage();
                if (image != null) {
                    Image.Plane[] planes = image.getPlanes();
                    ByteBuffer buffer = planes[0].getBuffer();
                    byte[] bytes = new byte[buffer.remaining()];
                    buffer.get(bytes);
                    buffer.rewind();
                    image.close();
                    setLatestImage(bytes);
//                    sendImage(bytes);
//                    httpBackgroundHandler.post(new Runnable() {
//                        @Override
//                        public void run() {
//                            try {
//                                sendImage(bytes);
//                            }catch (Exception e){
//                                e.printStackTrace();
//                            }
//                        }
//                    });
                    // 将bytes转换为Bitmap
                    Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                    // 处理Bitmap，例如显示或保存
                    // 将处理后的Bitmap发送回主线程更新UI
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            imageView.setImageBitmap(bitmap);
                        }
                    });
                }
            }, backgroundHandler);

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                requestPermission(Manifest.permission.CAMERA, 100);
                return;
            }
            manager.openCamera(cameraId, deviceStateCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private final CameraDevice.StateCallback deviceStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            cameraDevice = camera;
            startPreview();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            camera.close();
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            camera.close();
            cameraDevice = null;
        }
    };

    private void startPreview() {
        try {
            Surface surface = imageReader.getSurface();
            CaptureRequest.Builder previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewRequestBuilder.addTarget(surface);

            // 设置JPEG质量为较低的值，例如50
            previewRequestBuilder.set(CaptureRequest.JPEG_QUALITY, (byte)1);
            previewRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, 90);
            cameraDevice.createCaptureSession(Arrays.asList(surface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    captureSession = session;
                    try {
                        captureSession.setRepeatingRequest(previewRequestBuilder.build(), null, backgroundHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                }
            }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void startPreview2() {
        try {
            Surface surface = imageReader.getSurface();
            CaptureRequest.Builder captureRequest = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequest.addTarget(surface);

            cameraDevice.createCaptureSession(Arrays.asList(surface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    captureSession = session;
                    try {
                        captureSession.setRepeatingRequest(captureRequest.build(), null, backgroundHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                }
            }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void startBackgroundThread() {
        backgroundThread = new HandlerThread("CameraBackground");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());

        httpHandlerThread = new HandlerThread("httpBackgroundThread");
        httpHandlerThread.start();
        httpBackgroundHandler = new Handler(httpHandlerThread.getLooper());
    }

    @Override
    protected void onPause() {
        stopBackgroundThread();
        super.onPause();
    }

    private void stopBackgroundThread() {
        backgroundThread.quitSafely();
        try {
            backgroundThread.join();
            backgroundThread = null;
            backgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}