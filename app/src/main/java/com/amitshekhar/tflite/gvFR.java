package com.amitshekhar.tflite;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Bitmap;

import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;
import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class gvFR {
    private Interpreter interpreter;
    private int inputSize;
    public static final int SUCCESS = 0; //执行接口返回成功
    public static final int ERROR_INVALID_PARAM = -1; //非法参数
    public static final int ERROR_TOO_MANY_REQUESTS = -2; //太多请求
    public static final int ERROR_NOT_EXIST = -3; //不存在
    public static final int ERROR_FAILURE = -4; // 执行接口返回失败

    private static final int MAX_RESULTS = 3;
    private static final int BATCH_SIZE = 1;
    private static final int PIXEL_SIZE = 3;
    private static final float THRESHOLD = 0.1f;
    private static final int INPUT_SIZE = 224;
    private static final int IMAGE_MEAN = 256;
    private static final float IMAGE_STD = 256.0f;


    private boolean quant;
    private long startTime;
    private long endTime;
    private long frTime;
    final ArrayList<Classifier.Recognition> recognitions = new ArrayList<>();


    static gvFR CreateFR(AssetManager assetManager, String modelPath) throws IOException {
        // load model
        gvFR model = new gvFR();
        model.interpreter = new Interpreter(model.loadModelFile( assetManager, modelPath), new Interpreter.Options());
        model.inputSize = INPUT_SIZE;
        return model;
    }

    boolean SetInfo(int iCmd,int value){  //, void *pData
        // set info
        return false;
    }

    int GetFeature(Image image, float[] feature, List faceinfos, int[] res) {
        Mat ImageMat = new Mat(image.matAddrframe);
        Bitmap resultBitmap = Bitmap.createBitmap(ImageMat.cols(),  ImageMat.rows(),Bitmap.Config.ARGB_8888);;
        Utils.matToBitmap(ImageMat, resultBitmap);


        Bitmap resizeBitmap = Bitmap.createScaledBitmap(resultBitmap, INPUT_SIZE, INPUT_SIZE, false);
        ByteBuffer byteBuffer = convertBitmapToByteBuffer(resizeBitmap);
        float[][] embeddings = new float[1][512];
        startTime = new Date().getTime();
        interpreter.run(byteBuffer, embeddings);
        System.arraycopy(embeddings[0], 0, feature, 0, embeddings[0].length);
        endTime = new Date().getTime();
        res[0] = (int) (endTime - startTime);
        return 0;
    }

    int Compare( float[] origin, float[] chose, float[] score ){
        double sum = 0;
        for(int i=0;i<512;i++){
            sum += Math.pow(origin[i] - chose[i],2);
        }
        score[0] = (float) ((1.00 - (Math.sqrt(sum)*0.50 - 0.20))*100);
        if(score[0]>100) score[0] = 100;
        return 0;
    }

    boolean ReleaseFR() {
        // release model
        interpreter.close();
        interpreter = null;
        return true;
    }

    private MappedByteBuffer loadModelFile(AssetManager assetManager, String modelPath) throws IOException {
        AssetFileDescriptor fileDescriptor = assetManager.openFd(modelPath);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    private ByteBuffer convertBitmapToByteBuffer(Bitmap bitmap) {
        ByteBuffer byteBuffer;

        if(quant) {
            byteBuffer = ByteBuffer.allocateDirect(BATCH_SIZE * inputSize * inputSize * PIXEL_SIZE);
        } else {
            byteBuffer = ByteBuffer.allocateDirect(4 * BATCH_SIZE * inputSize * inputSize * PIXEL_SIZE);
        }

        byteBuffer.order(ByteOrder.nativeOrder());
        int[] intValues = new int[inputSize * inputSize];
        bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
        int pixel = 0;
        for (int i = 0; i < inputSize; ++i) {
            for (int j = 0; j < inputSize; ++j) {
                final int val = intValues[pixel++];
                if(quant){
                    byteBuffer.put((byte) ((val >> 16) & 0xFF));
                    byteBuffer.put((byte) ((val >> 8) & 0xFF));
                    byteBuffer.put((byte) (val & 0xFF));
                } else {
                    byteBuffer.putFloat((((val >> 16) & 0xFF)-IMAGE_MEAN)/IMAGE_STD);
                    byteBuffer.putFloat((((val >> 8) & 0xFF)-IMAGE_MEAN)/IMAGE_STD);
                    byteBuffer.putFloat((((val) & 0xFF)-IMAGE_MEAN)/IMAGE_STD);
                }

            }
        }
        return byteBuffer;
    }
}
