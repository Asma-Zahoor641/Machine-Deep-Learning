package com.example.chinup_pushup;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
/** @noinspection CallToPrintStackTrace*/

public class PushUp extends AppCompatActivity {
    private ActivityResultLauncher<Intent> cameraLauncher;
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 100;

    private Interpreter tflite;
    private ImageView selectedImage;
    private TextView resultTextView;
    private Button submitButton;
    private TextView correctCountTextView;
    private TextView wrongCountTextView;
    private int correctCount = 0;
    private int wrongCount = 0;

    private final String[] class_names = {
            "1-Correct_starting_position",
            "2-Correct_intermediate_position",
            "3-Correct_ending_position",
            "4-Wrong_starting_position",
            "5-Wrong_intermediate_position",
            "6-Wrong_ending_position"
    };

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_push_up);

        selectedImage = findViewById(R.id.e_selectedImage);
        resultTextView = findViewById(R.id.g_resultTextView);
        submitButton = findViewById(R.id.f_submitImageButton);
        correctCountTextView = findViewById(R.id.c_correctCountTextView1);
        wrongCountTextView = findViewById(R.id.d_wrongCountTextView1);

        try {
            tflite = new Interpreter(loadModelFile());
        } catch (IOException e) {
            e.printStackTrace();
        }

        Button galleryButton = findViewById(R.id.c_galleryButton);
        galleryButton.setOnClickListener(view -> chooseImageFromGallery());

        Button cameraButton = findViewById(R.id.d_cameraButton);
        cameraButton.setOnClickListener(view -> captureImageFromCamera());

        submitButton.setOnClickListener(view -> processImageWithModel());

        Button previousButton = findViewById(R.id.h_previousButton);
        previousButton.setOnClickListener(view -> navigateToChooseActivity());

        cameraLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                this::onActivityResult);  //Working: Registers the activity result launcher for camera intents.

                                        //    Purpose: Sets up the launcher to handle camera intents and their results.

        Button refreshButton = findViewById(R.id.i_refreshButton);
        refreshButton.setOnClickListener(view -> refreshUI());

        if (selectedImage.getDrawable() == null) {
            resultTextView.setText("Please select an image");
        }
    }

    private MappedByteBuffer loadModelFile() throws IOException {
        AssetFileDescriptor fileDescriptor = getAssets().openFd("model.tflite");
        try (FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor())) {
            FileChannel fileChannel = inputStream.getChannel();
            long startOffset = fileDescriptor.getStartOffset();
            long declaredLength = fileDescriptor.getDeclaredLength();
            return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
        }
    }

    private void chooseImageFromGallery() {
        Intent galleryIntent = new Intent(Intent.ACTION_PICK);
        galleryIntent.setType("image/*");
        cameraLauncher.launch(galleryIntent);
    }

    private void captureImageFromCamera() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST_CODE);
            return;
        }

        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            cameraLauncher.launch(takePictureIntent);
        } else {
            Toast.makeText(this, "No camera app found", Toast.LENGTH_SHORT).show();
        }
    }

    @SuppressLint("SetTextI18n")
    private void processImageWithModel() {
        submitButton.setEnabled(false);

        BitmapDrawable drawable = (BitmapDrawable) selectedImage.getDrawable();
        if (drawable == null) {
            resultTextView.setText("Please select an image");
            submitButton.setEnabled(true);
            return;
        }
        Bitmap bitmap = drawable.getBitmap();

        if (tflite != null) {
            try {
                Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, 224, 224, true);
                ByteBuffer inputBuffer = ByteBuffer.allocateDirect(224 * 224 * 3 * 4);
                inputBuffer.order(ByteOrder.nativeOrder());
                int[] intValues = new int[224 * 224];
                resizedBitmap.getPixels(intValues, 0, //The index in the array where the first pixel data will be written
                        resizedBitmap.getWidth(), 0, 0,resizedBitmap.getWidth(), resizedBitmap.getHeight());
                int pixel = 0;
                for (int i = 0; i < 224; ++i) {
                    for (int j = 0; j < 224; ++j) {
                        final int val = intValues[pixel++];
                        inputBuffer.putFloat(((val >> 16) & 0xFF) / 255.0f); // Red  16 positions to the right,

                        inputBuffer.putFloat(((val >> 8) & 0xFF) / 255.0f); // Green  val >> 8 shifts the bits 8 positions to the right to isolate the green component, and & 0xFF extracts the green component.
                        inputBuffer.putFloat((val & 0xFF) / 255.0f); // Blue
                    }
                }
/*Pixel Value: 0xFFAABBCC
                This 32-bit integer represents a pixel in ARGB format:

                Alpha (A) = 0xFF
                Red (R) = 0xAA
                Green (G) = 0xBB
                Blue (B) = 0xCC*/
                float[][] output = new float[1][6];  //creates a two-dimensional array of floats with 1 row and 6 columns.
                tflite.run(inputBuffer, output);

                int predictedClassIndex = findIndexOfMaxValue(output[0]);
                Log.d("PushUp", "Predicted class index: " + predictedClassIndex);
                for (int i = 0; i < class_names.length; i++) {
                    Log.d("PushUp", class_names[i] + ": " + output[0][i]);
                }

                if (predictedClassIndex >= 0 && predictedClassIndex < class_names.length) {
                    String predictedClassName = class_names[predictedClassIndex];
                    resultTextView.setText("Predicted class: " + predictedClassName);
                    // Update counts based on prediction
                    if (predictedClassName.startsWith("1-") || predictedClassName.startsWith("2-") || predictedClassName.startsWith("3-")) {
                        correctCount++;
                        correctCountTextView.setText("Correct: " + correctCount);
                    } else {
                        wrongCount++;
                        wrongCountTextView.setText("Wrong: " + wrongCount);
                    }
                } else {
                    resultTextView.setText("Unknown class");
                }
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(this, "Failed to process image with model", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "Model not loaded, please check", Toast.LENGTH_SHORT).show();
        }

        submitButton.setEnabled(true);
    }



    private void refreshUI() {
        selectedImage.setImageDrawable(null);
        resultTextView.setText("");

    }

    private void onActivityResult(ActivityResult result) {
        if (result.getResultCode() == RESULT_OK) {
            Intent data = result.getData();
            if (data != null) {
                Bundle extras = data.getExtras();
                if (extras != null && extras.get("data") != null) {
                    // Image captured from camera (bitmap in extras)

                    Bitmap imageBitmap = (Bitmap) extras.get("data");
                    selectedImage.setImageBitmap(imageBitmap);
                    selectedImage.setVisibility(View.VISIBLE);
                    // Image selected from gallery (URI in data)

                } else if (data.getData() != null) {
                    Uri selectedImageUri = data.getData();   //Uri is used to reference the location of an image that has been captured by the camera or selected from the device's gallery.
                    try {
                        Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), selectedImageUri);
                        selectedImage.setImageBitmap(bitmap);
                        selectedImage.setVisibility(View.VISIBLE);
                    } catch (IOException e) {
                        e.printStackTrace();
                        Toast.makeText(this, "Failed to load image from gallery", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(this, "Failed to capture image", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "No data received", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void navigateToChooseActivity() {
        Intent intent = new Intent(this, ChooseActivity.class);
        startActivity(intent);
    }

    private int findIndexOfMaxValue(float[] array) {
        int maxIndex = 0;
        float maxValue = array[0];
        for (int i = 1; i < array.length; i++) {
            if (array[i] > maxValue) {
                maxValue = array[i];
                maxIndex = i;
            }
        }
        return maxIndex;
    }
}
/*224 * 224 * 3 * 4: This is the capacity of the buffer, calculated based on the specific requirements of the image data you are processing.

        224: The width of the image in pixels.
        224: The height of the image in pixels.
        3: The number of color channels (Red, Green, Blue) per pixel.
        4: The number of bytes per color channel. Since each color channel is represented as a 32-bit floating-point number (4 bytes), the total number of bytes for one pixel is 3 * 4 = 12.
        So, the capacity of the buffer is calculated as 224 * 224 * 3 * 4, which equals 602,112 bytes.*/