package com.example.maskdetection.view;

import android.app.Activity;
import android.os.Bundle;

import com.example.maskdetection.R;

import androidx.annotation.Nullable;

public class CameraActivity extends Activity {
    private static final String TAG = "CameraActivity";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);
        getFragmentManager().beginTransaction().replace(R.id.container, Camera2Fragment.newInstance()).commit();
    }
}
