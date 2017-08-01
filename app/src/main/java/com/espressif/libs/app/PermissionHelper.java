package com.espressif.libs.app;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

public class PermissionHelper {
    private final int mRequestCode;

    private Activity mActivity;

    private OnPermissionsListener mListener;

    public PermissionHelper(@NonNull Activity activity, int requestCode) {
        mActivity = activity;
        mRequestCode = requestCode;
    }

    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == mRequestCode) {
            for (int i = 0; i <permissions.length; i++) {
                String permission = permissions[i];
                boolean granted = grantResults[i] == PackageManager.PERMISSION_GRANTED;
                if (mListener != null) {
                    mListener.onPermissonsChange(permission, granted);
                }
            }
        }
    }

    public void requestAuthorities(@NonNull String[] permissions) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            final List<String> requirePermissionList = new ArrayList<>();

            for (String permission : permissions) {
                if (!isPermissionGranted(permission)) {
                    requirePermissionList.add(permission);
                } else {
                    if (mListener != null) {
                        mListener.onPermissonsChange(permission, true);
                    }
                }
            }

            if (!requirePermissionList.isEmpty()) {
                String[] requirePermissionArray = new String[requirePermissionList.size()];
                for (int i = 0; i < requirePermissionList.size(); i++) {
                    requirePermissionArray[i] = requirePermissionList.get(i);
                }
                // request permission one by one
                ActivityCompat.requestPermissions(mActivity, requirePermissionArray, mRequestCode);
            }
        } else {
            if (mListener != null) {
                for (String permission : permissions) {
                    mListener.onPermissonsChange(permission, true);
                }
            }
        }
    }

    public boolean isPermissionGranted(String permission) {
        return ContextCompat.checkSelfPermission(mActivity, permission) == PackageManager.PERMISSION_GRANTED;
    }

    public void setOnPermissionsListener(OnPermissionsListener listener) {
        mListener = listener;
    }

    public interface OnPermissionsListener {
        void onPermissonsChange(String permission, boolean permited);
    }
}
