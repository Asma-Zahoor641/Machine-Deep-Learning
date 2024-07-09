package com.example.chinup_pushup;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;


/** @noinspection deprecation*/
public class ChooseActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_choose);

        Button pushUpButton = findViewById(R.id.pushUpButton);
        pushUpButton.setBackgroundColor(getResources().getColor(android.R.color.black));
        pushUpButton.setTextColor(getResources().getColor(android.R.color.white));

        Button pushUpButton2 = findViewById(R.id.pushUpButton_2);
        pushUpButton2.setBackgroundColor(getResources().getColor(android.R.color.black));
        pushUpButton2.setTextColor(getResources().getColor(android.R.color.white));




        pushUpButton.setOnClickListener(view -> {
            // Handle push up button click
            startActivity(new Intent(ChooseActivity.this, PushUp.class));
        });

        pushUpButton2.setOnClickListener(view -> {
            // Handle chin up button click
            startActivity(new Intent(ChooseActivity.this, push_up2.class));
        });
    }
}
