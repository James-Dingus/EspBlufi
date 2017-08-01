package com.espressif.libs.net;

public class EspHttpHeader {
    private String mName;

    private String mValue;

    private boolean mMesh = false;

    public EspHttpHeader(String name, String value) {
        mName = name;
        mValue = value;
    }

    public String getName() {
        return mName;
    }

    public String getValue() {
        return mValue;
    }

    public void setMesh(boolean isMesh) {
        mMesh = isMesh;
    }

    public boolean isMesh() {
        return mMesh;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }

        if (!(obj instanceof EspHttpHeader)) {
            return false;
        }

        EspHttpHeader compareObj = (EspHttpHeader) obj;
        if (mName == null || mValue == null || compareObj.getName() == null || compareObj.getValue() == null) {
            return false;
        }

        return mName.equals(compareObj.getName()) && mValue.equals(compareObj.getValue()) && mMesh == compareObj.isMesh();
    }

    @Override
    public String toString() {
        return String.format("name=%s, value=%s, mesh=%b", mName, mValue, mMesh);
    }

}
