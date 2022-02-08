package com.animegan_mobile;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.util.Base64;
import android.util.Log;
import android.util.SparseIntArray;
import android.widget.ImageView;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;


import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Arrays;

import ai.onnxruntime.reactnative.TensorHelper;

public class ModelDataHandler extends  ReactContextBaseJavaModule{
    private static ReactApplicationContext reactContext;
    ModelDataHandler(ReactApplicationContext context){
        super(context);
        reactContext=context;
    }
    @Override
    public String getName() {
        return "ModelDataHandler";
    }

    @ReactMethod
    public void getLocalModelPath(Promise promise) {
        Log.d("status:","yes it is working");
        try {
            String modelPath = copyFile(reactContext, "faceModel.ort");
            promise.resolve(modelPath);
        } catch (Exception e) {
            promise.reject("Can't get a model", e);
        }

    }

    @ReactMethod
    public void preprocess(String uri,Integer width, Promise promise) {
        try {
            WritableMap inputDataMap = preprocess(uri,width);
            promise.resolve(inputDataMap);

        } catch (Exception e) {
            promise.reject("Can't process an image", e);
        }
    }

    @ReactMethod
    public void postprocess(ReadableMap result, Promise promise) {
        try {
            String uri = postprocess(result);
            promise.resolve(uri);
        } catch (Exception e) {
            promise.reject("Can't process a inference result", e);
        }
    }

    // It gets raw input data, which can be uri or byte array and others,
    // returns cooked data formatted as input of a model by promise.
    private WritableMap preprocess(String uri,Integer width) throws Exception {
        final int batchSize = 1;

        InputStream is = MainApplication.getAppContext().getContentResolver().openInputStream(Uri.parse(uri));
        BufferedInputStream bis = new BufferedInputStream(is);
        byte[] imageArray = new byte[bis.available()];
        bis.read(imageArray);
        Bitmap bitmap = BitmapFactory.decodeByteArray(imageArray, 0, imageArray.length);
        if (bitmap == null) {
            throw new Exception("Can't decode image: " + uri);
        }
        int imageHeight = bitmap.getHeight();
        int imageWidth = bitmap.getWidth();
        int tem = (int) ((width*1.f /imageWidth)*imageHeight);

        // Resize bitmap to 28x28
       bitmap = Bitmap.createScaledBitmap(bitmap, width, tem, false);
        imageHeight = bitmap.getHeight();
        imageWidth = bitmap.getWidth();

        ByteBuffer imageByteBuffer = ByteBuffer.allocate(imageHeight * imageWidth * 4*3).order(ByteOrder.nativeOrder());
        FloatBuffer imageFloatBuffer = imageByteBuffer.asFloatBuffer();

        for (int h = 0; h < imageHeight; ++h) {
            for (int w = 0; w < imageWidth; ++w) {
                int pixel = bitmap.getPixel(w, h);
                imageFloatBuffer.put((float) Color.red(pixel)/255*2-1);
//                imageFloatBuffer.put((float) Color.green(pixel)/255*2-1);
//                imageFloatBuffer.put((float) Color.blue(pixel)/255*2-1);
            }
        }
        for (int h = 0; h < imageHeight; ++h) {
            for (int w = 0; w < imageWidth; ++w) {
                int pixel = bitmap.getPixel(w, h);
//                imageFloatBuffer.put((float) Color.red(pixel)/255*2-1);
                imageFloatBuffer.put((float) Color.green(pixel)/255*2-1);
//                imageFloatBuffer.put((float) Color.blue(pixel)/255*2-1);
            }
        }
        for (int h = 0; h < imageHeight; ++h) {
            for (int w = 0; w < imageWidth; ++w) {
                int pixel = bitmap.getPixel(w, h);
//                imageFloatBuffer.put((float) Color.red(pixel)/255*2-1);
//                imageFloatBuffer.put((float) Color.green(pixel)/255*2-1);
                imageFloatBuffer.put((float) Color.blue(pixel)/255*2-1);
            }
        }
        imageByteBuffer.rewind();

        WritableMap inputDataMap = Arguments.createMap();

        // dims
        WritableMap inputTensorMap = Arguments.createMap();

        WritableArray dims = Arguments.createArray();
        dims.pushInt(batchSize);
        dims.pushInt(3);
        dims.pushInt(imageHeight);
        dims.pushInt(imageWidth);



        inputTensorMap.putArray("dims", dims);

        // type
        inputTensorMap.putString("type", TensorHelper.JsTensorTypeFloat);

        // data encoded as Base64
        imageByteBuffer.rewind();
        String data = Base64.encodeToString(imageByteBuffer.array(), Base64.DEFAULT);
        inputTensorMap.putString("data", data);
        inputDataMap.putMap("input", inputTensorMap);



        return inputDataMap;
    }
    private String postprocess(ReadableMap result) throws Exception {


        ReadableMap outputTensor = result.getMap("output");
        ReadableMap dims = result.getMap("dims");

        String outputData = outputTensor.getString("data");
        String[] dimens = dims.getString("data").split(",");

        int[] dimensions = new int[2];

        dimensions[0]= Integer.parseInt(dimens[1]);
        dimensions[1]= Integer.parseInt(dimens[0]);
        FloatBuffer buffer =ByteBuffer.wrap(Base64.decode(outputData, Base64.DEFAULT)).order(ByteOrder.nativeOrder()).asFloatBuffer();

        int[] pixels = new int[dimensions[0] * dimensions[1]]; // Set your expected output's height and width
//        ArrayList<Float> dataArray = new ArrayList<>();
        float[] dataArray = new float[dimensions[0] * dimensions[1]*3];
        int count = 0;
        while (buffer.hasRemaining()) {
            dataArray[count]=buffer.get();
            count++;
        }

        int constant = dimensions[0] * dimensions[1];
        for (int i = 0; i < constant; i++) {
            float r = (dataArray[i] + 1) / 2 * 255.0f;
            float g = (dataArray[i + constant] + 1) / 2 * 255.0f;
            float b = (dataArray[i + constant * 2] + 1) / 2 * 255.0f;
            pixels[i] =  (((int) r) << 16) | (((int) g) << 8) | ((int) b);
        }

        Bitmap bitmap = Bitmap.createBitmap(pixels, dimensions[0], dimensions[1], Bitmap.Config.RGB_565);
        Log.d("bitmap", Arrays.toString(pixels));

        try (FileOutputStream out = reactContext.openFileOutput("temp.jpeg",0) ) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out); // bmp is your Bitmap instance
            out.flush();

        } catch (IOException e) {
            e.printStackTrace();
        }



        return  reactContext.getFileStreamPath("temp.jpeg").toURI().toString();

    }


    private static String copyFile(Context context, String filename) throws Exception {
        File file = new File(context.getExternalFilesDir(null), filename);
        if (!file.exists()) {
            try (InputStream in = context.getAssets().open(filename)) {
                try (OutputStream out = new FileOutputStream(file)) {
                    byte[] buffer = new byte[1024];
                    int read = in.read(buffer);
                    while (read != -1) {
                        out.write(buffer, 0, read);
                        read = in.read(buffer);
                    }
                }
            }
        }

        return file.toURI().toString();
    }
}
