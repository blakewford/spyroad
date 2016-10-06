package org.starlo.spyroad;

import android.webkit.*;

public class Native
{
    private float mLatitude;
    private float mLongitude;
    private WebView mMapView;

    public Native(WebView mapView){ mMapView = mapView; }
    public void reset(){ mMapView = null; mLatitude = 0.0f; mLongitude = 0.0f; }

    public native void engineInit();
    public native void loadProgram(String binary);
    public native int fetchN(int n);
    public native int speedToRPM(int n);

    public void post(float latitude, float longitude, float speed)
    {
        mLatitude = latitude;
        mLongitude = longitude;
    }

    public void postUI()
    {
        if(mMapView != null)
        {
            mMapView.post(new Runnable(){
                public void run()
                {
                    mMapView.loadUrl("javascript:drawMap("+mLatitude+","+mLongitude+")");
                }
            });
        }
    }

    public float getLatitude()
    {
        return mLatitude;
    }

    public float getLongitude()
    {
        return mLongitude;
    }

    static { System.loadLibrary("spyroad"); }

}
