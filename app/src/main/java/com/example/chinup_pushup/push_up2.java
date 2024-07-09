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
    public class push_up2 extends AppCompatActivity {
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

        // Define the class names
        private final String[] class_names = {
                "1-Correct_starting_position",
                "2-Correct_ending_position",
                "3-Wrong_starting_position",
                "4-Wrong_ending_position"
        };

        @SuppressLint("SetTextI18n")
        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_push_up2);

            selectedImage = findViewById(R.id.selectedImage);
            resultTextView = findViewById(R.id.resultTextView);
            submitButton = findViewById(R.id.submitImageButton);
            correctCountTextView = findViewById(R.id.correctCountTextView);
            wrongCountTextView = findViewById(R.id.wrongCountTextView);

            // Load the TensorFlow Lite model when the activity is created
            try {
                tflite = new Interpreter(loadModelFile());
            } catch (IOException e) {
                e.printStackTrace();
            }

            Button galleryButton = findViewById(R.id.galleryButton);
            galleryButton.setOnClickListener(view -> chooseImageFromGallery());

            Button cameraButton = findViewById(R.id.cameraButton);
            cameraButton.setOnClickListener(view -> captureImageFromCamera());

            submitButton.setOnClickListener(view -> processImageWithModel());

            Button previousButton = findViewById(R.id.previousButton);
            previousButton.setOnClickListener(view -> navigateToChooseActivity());

            // Initialize ActivityResultLauncher for camera intent
            cameraLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    this::onActivityResult);

            // Refresh button
            Button refreshButton = findViewById(R.id.refreshButton);
            refreshButton.setOnClickListener(view -> refreshUI());

            // Show "Please select an image" message initially if no image is selected
            if (selectedImage.getDrawable() == null) {
                resultTextView.setText("Please select an image");
            }
        }

        // Load the TensorFlow Lite model when the activity is created
        private MappedByteBuffer loadModelFile() throws IOException {
            AssetFileDescriptor fileDescriptor = getAssets().openFd("model1.tflite");
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
            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                // Request camera permission if not granted
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST_CODE);
                return;
            }

            // Proceed with camera intent if permission is granted
            Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
                cameraLauncher.launch(takePictureIntent);
            } else {
                Toast.makeText(this, "No camera app found", Toast.LENGTH_SHORT).show();
            }
        }

        @SuppressLint("SetTextI18n")
        private void processImageWithModel() {
            // Disable the submit button during inference to prevent multiple requests
            submitButton.setEnabled(false);

            // Get the bitmap image from the ImageView
            BitmapDrawable drawable = (BitmapDrawable) selectedImage.getDrawable();
            if (drawable == null) {
                // Show a message if no image is selected
                resultTextView.setText("Please select an image");
                submitButton.setEnabled(true); // Re-enable the submit button
                return;
            }
            Bitmap bitmap = drawable.getBitmap();

            // Proceed with image processing
            if (tflite != null) {
                try {
                    // Prepare the input image for the model
                    Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, 224, 224, true);
                    ByteBuffer inputBuffer = ByteBuffer.allocateDirect(224 * 224 * 3 * 4);
                    inputBuffer.order(ByteOrder.nativeOrder());
                    int[] intValues = new int[224 * 224];
                    resizedBitmap.getPixels(intValues, 0, resizedBitmap.getWidth(), 0, 0, resizedBitmap.getWidth(), resizedBitmap.getHeight());
                    int pixel = 0;
                    for (int i = 0; i < 224; ++i) {
                        for (int j = 0; j < 224; ++j) {
                            final int val = intValues[pixel++];
                            inputBuffer.putFloat(((val >> 16) & 0xFF) / 255.0f);
                            inputBuffer.putFloat(((val >> 8) & 0xFF) / 255.0f);
                            inputBuffer.putFloat((val & 0xFF) / 255.0f);
                        }
                    }

                    // Run inference
                    float[][] output = new float[1][4]; // Adjust the size for 4 classes
                    tflite.run(inputBuffer, output);

                    // Log the predicted class index and probabilities
                    int predictedClassIndex = findIndexOfMaxValue(output[0]);
                    Log.d("PushUp2", "Predicted class index: " + predictedClassIndex);
                    for (int i = 0; i < class_names.length; i++) {
                        Log.d("PushUp2", class_names[i] + ": " + output[0][i]);
                    }

                    // Determine the predicted class
                    if (predictedClassIndex >= 0 && predictedClassIndex < class_names.length) {
                        String predictedClassName = class_names[predictedClassIndex];
                        resultTextView.setText("Predicted class: " + predictedClassName);

                        // Update counts based on prediction
                        if (predictedClassName.startsWith("1-") || predictedClassName.startsWith("2-")) {
                            correctCount++;
                            correctCountTextView.setText("Correct: " + correctCount);
                        } else {
                            wrongCount++;
                            wrongCountTextView.setText(" Wrong: " + wrongCount);
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

            // Re-enable the submit button after inference is completed
            submitButton.setEnabled(true);
        }

        // Find the index of the maximum value in the array
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

        private void refreshUI() {
            // Clear the selected image and result text
            selectedImage.setImageDrawable(null);
            resultTextView.setText("");
        }

        private void onActivityResult(ActivityResult result) {
            if (result.getResultCode() == RESULT_OK) {
                Intent data = result.getData();
                if (data != null) {
                    Bundle extras = data.getExtras();
                    if (extras != null && extras.get("data") != null) {
                        Bitmap imageBitmap = (Bitmap) extras.get("data");
                        selectedImage.setImageBitmap(imageBitmap);
                        selectedImage.setVisibility(View.VISIBLE);
                    } else if (data.getData() != null) {
                        Uri selectedImageUri = data.getData();
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
    }
