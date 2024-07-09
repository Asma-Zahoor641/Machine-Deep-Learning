package com.example.chinup_pushup;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.widget.ProgressBar;

import androidx.appcompat.app.AppCompatActivity;


public class MainActivity extends AppCompatActivity {

    private ProgressBar loader;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        loader = findViewById(R.id.loader);

        // Start loading animation
        simulateLoading();    }

    private void simulateLoading() {
        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            int progress = 0;

            @Override
            public void run() {
                progress += 10;
                loader.setProgress(progress);
                if (progress < 100) {
                    handler.postDelayed(this, 300); // Delay in milliseconds
                } else {
                    // Move to next activity when loading completes
                    startActivity(new Intent(MainActivity.this, ChooseActivity.class));
                    finish(); // Finish this activity to prevent returning back here
                }
            }
        }, 300); // Initial delay in milliseconds
    }
}
