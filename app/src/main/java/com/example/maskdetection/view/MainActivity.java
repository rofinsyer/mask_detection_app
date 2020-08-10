package com.example.maskdetection.view;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;

import com.example.maskdetection.R;

public class MainActivity extends AppCompatActivity {
    // permissions
    private String[] permissions = {
            Manifest.permission.INTERNET, // check permission ?
            Manifest.permission.CAMERA,
            Manifest.permission.REQUEST_INSTALL_PACKAGES
    };
    private final int requestCodePermissions = 1000;
    private final int requestCodeInstallation = 2000;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        checkPermissions();
    }

    private void checkPermissions(){
        boolean isGranted = true;
        for(int i=0; i<permissions.length; i++){
            if(ContextCompat.checkSelfPermission(getApplicationContext(),permissions[i]) != PackageManager.PERMISSION_GRANTED){
                    isGranted = false;
                    break;
            }
        }
        if(isGranted == false){
            requestAppPermissions();
        }else{
            if(requestInstallUnknownSourceApp()){
                Toast.makeText(getApplicationContext(), "isGranted! Start!", Toast.LENGTH_SHORT).show();
                startCameraActivity();
            }
        }
    }
    private void requestAppPermissions(){
        if(Build.VERSION.SDK_INT > Build.VERSION_CODES.M){
            requestPermissions(permissions, requestCodePermissions);
        }
    }
    private boolean requestInstallUnknownSourceApp(){
        if(Build.VERSION.SDK_INT > Build.VERSION_CODES.O){
            if(getPackageManager().canRequestPackageInstalls() == false){
                Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, requestCodeInstallation);
                return false;
            }
        }
        return true;
    }

    private void startCameraActivity(){
        finish();
        Intent intent = new Intent(this, CameraActivity.class);
        startActivity(intent);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch(requestCode){
            case requestCodePermissions:
                if(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                    // start
                    if(requestInstallUnknownSourceApp()){
                        Toast.makeText(getApplicationContext(), "isGranted! Start!", Toast.LENGTH_SHORT).show();
                        startCameraActivity();
                    }
                }else{
                    // stop show toast
                }
                return;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch(requestCode){
            case requestCodeInstallation:
                if(resultCode == RESULT_OK){
                    startCameraActivity();
                }
                break;
        }
    }
}