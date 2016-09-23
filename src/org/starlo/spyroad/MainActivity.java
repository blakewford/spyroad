package org.starlo.spyroad;

import java.io.*;
import java.text.*;
import java.util.*;

import android.os.*;
import android.app.*;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import android.content.*;
import android.view.View;
import android.graphics.*;
import android.location.*;

public class MainActivity extends Activity
{
    private final static boolean DEBUG = false;

    enum Mode
    {
        READY,
        RUNNING,
        STOPPED
    }

    final String HEADER = "\"RouteId\",\"Latitude\",\"Longitude\",\"DateTime\",\"Heading\",\"Speed\",\"Distance\",\"Elevation\",\"Accuracy\"";

    private UUID mUUID;
    private Mode mMode = null;
    private Location mPrevious = null;

    private WebView mMapView = null;
    private Button mReplay = null;
    private ImageButton mShare = null;
    private ImageButton mRefresh = null;
    private Button mStopStart = null;
    private TextView mLatitude = null;
    private TextView mLongitude = null;
    private TextView mPlaceHolder = null;
    private LocationManager mManager = null;
    private SharedPreferences.Editor mEditor = null;
    private LocationListener mLocationListener = null;
    private ProgressDialog mProgress = null;
    boolean mCancel = false;

    //Debug only!
    String mDebug = null;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        //WebView.setWebContentsDebuggingEnabled(true);

        WebView local = (WebView)findViewById(R.id.local);
        local.getSettings().setJavaScriptEnabled(true);
        local.loadUrl("file:///android_asset/mobile.html");

        mMapView = (WebView)findViewById(R.id.web);
        mMapView.getSettings().setJavaScriptEnabled(true);
        mMapView.loadUrl(MapKey.MAPLINK);
        mMapView.setOnTouchListener(new View.OnTouchListener()
        {
            @Override
            public boolean onTouch(View v, MotionEvent event)
            {
                return true;
            }
        });

        mEditor = getSharedPreferences("log", Context.MODE_PRIVATE).edit();

        Typeface Hundo = Typeface.createFromAsset(getAssets(), "Hundo.ttf");
        mPlaceHolder = (TextView)findViewById(R.id.placeholder);
        mPlaceHolder.setTypeface(Hundo);
        mLatitude = (TextView)findViewById(R.id.latitude);
        mLatitude.setTypeface(Hundo);
        mLongitude = (TextView)findViewById(R.id.longitude);
        mLongitude.setTypeface(Hundo);
        mStopStart = (Button)findViewById(R.id.stopstart);
        mStopStart.setTypeface(Hundo);
        mStopStart.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                StartLogging();
            }
        });
        mStopStart.setOnLongClickListener(new View.OnLongClickListener()
        {
            @Override
            public boolean onLongClick(View view)
            {
                mProgress.show();
                StopLogging();
                return true;
            }
        });
        mReplay = (Button)findViewById(R.id.replay);
        mReplay.setTypeface(Typeface.createFromAsset(getAssets(), "Hundo.ttf"));
        mReplay.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                mCancel = false;
                new Thread(new Runnable()
                {
                    public void run()
                    {
                        Native wrapper = new Native(mMapView);
                        wrapper.engineInit();
                        wrapper.loadProgram(getDriveString());
                        wrapper.fetchN(1); //Pull off Header
                        while(!mCancel && wrapper.fetchN(10) > 0)
                        {
                            //10x speed
                            try{ Thread.sleep(1000); }catch(Exception e){}
                            wrapper.postUI();
                        }
                        mCancel = true;
                    }
                }).start();
            }
        });
        mRefresh = (ImageButton)findViewById(R.id.refresh);
        mRefresh.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                changeButtonMode(Mode.READY);
            }
        });
        mShare = (ImageButton)findViewById(R.id.share);
        mShare.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                Intent sendIntent = new Intent();
                sendIntent.setAction(Intent.ACTION_SEND);
                sendIntent.putExtra(Intent.EXTRA_TEXT, getDriveString());
                sendIntent.putExtra(Intent.EXTRA_SUBJECT, new Date().toString()+".csv");
                sendIntent.setType("text/plain");
                startActivity(sendIntent);
            }
        });
        mManager = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
        mLocationListener = new LocationListener()
        {
            @Override
            public void onLocationChanged(Location loc)
            {
                String time = new Long(loc.getTime()).toString();
                String distance = mPrevious == null ? "0.0": new Float(loc.distanceTo(mPrevious)).toString();
                String latitude = new Double(loc.getLatitude()).toString();
                String longitude = new Double(loc.getLongitude()).toString();
                SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS-0400");

                StringBuilder builder = new StringBuilder();
                builder.append("\""+new Integer(Integer.parseInt(mUUID.toString().substring(9, 13), 16)).toString()+"\",");
                builder.append("\""+latitude+"\",");
                builder.append("\""+longitude+"\",");
                builder.append("\""+formatter.format(new Date(loc.getTime()))+"\",");
                builder.append("\""+new Float(loc.getBearing()).toString()+"\",");
                builder.append("\""+new Float(loc.getSpeed()*2.23694f).toString()+"\",");
                builder.append("\""+distance+"\",");
                builder.append("\""+new Float(loc.getAltitude()).toString()+"\",");
                builder.append("\""+new Float(loc.getAccuracy()).toString()+"\"");

                mEditor.putString(time, builder.toString());
                mLatitude.setText(latitude);
                mLongitude.setText(longitude);
                mEditor.apply();

                mPrevious = loc;
            }

            @Override
            public void onProviderDisabled(String provider)
            {
            }

            @Override
            public void onProviderEnabled(String provider)
            {
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras)
            {
            }
        };
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        mProgress = new ProgressDialog(this, android.R.style.Theme_Material_Dialog_Alert);
        mProgress.setCancelable(false);
        mProgress.setMessage("Loading...");
        if(DEBUG)
        {
            getDriveString(); //Build cache
        }
        changeButtonMode(Mode.READY);
    }

    private void StartLogging()
    {
        if(mMode == Mode.READY)
        {
            mPrevious = null;
            mEditor.clear();
            mEditor.commit();
            mUUID = UUID.randomUUID();
            mManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, mLocationListener);
            changeButtonMode(Mode.RUNNING);
        }
    }

    private void StopLogging()
    {
        if(mMode == Mode.RUNNING)
        {
            mManager.removeUpdates(mLocationListener);
            changeButtonMode(Mode.STOPPED);
            if(mPrevious != null)
             {
                double latitude = 0.0f;
                double longitude = 0.0f;
                if(DEBUG)
                {
                    Native wrapper = new Native(null);
                    wrapper.engineInit();
                    wrapper.loadProgram(getDriveString());
                    wrapper.fetchN(1); //Pull off Header
                    wrapper.fetchN(1000);
                    //Ignore actual location, pull last coordinate from local data file
                    latitude = wrapper.getLatitude();
                    longitude = wrapper.getLongitude();
                }
                else
                {
                    latitude = mPrevious.getLatitude();
                    longitude = mPrevious.getLongitude();
                }
                mMapView.loadUrl("javascript:drawMap("+latitude+","+longitude+")");

                mCancel = false;
                new Thread(new Runnable(){
                    public void run(){
                        validateDrive();
                        mCancel = true;
                    }
                }).start();
            }
            else
            {
                mProgress.dismiss();
            }
        }
        else
        {
            mProgress.dismiss();
        }
    }

    private void changeButtonMode(Mode newMode)
    {
        mMode = newMode;
        switch(mMode)
        {
            case READY:
                mCancel = true;
                mPlaceHolder.setVisibility(View.VISIBLE);
                mMapView.setVisibility(View.GONE);
                mStopStart.setVisibility(View.VISIBLE);
                mReplay.setVisibility(View.GONE);
                mRefresh.setVisibility(View.GONE);
                mShare.setVisibility(View.GONE);
                mStopStart.setText(getResources().getString(R.string.start_log));
                break;
            case RUNNING:
                mStopStart.setText(getResources().getString(R.string.stop_log));
                break;
            case STOPPED:
                mPlaceHolder.setVisibility(View.GONE);
                mMapView.setVisibility(View.VISIBLE);
                mStopStart.setVisibility(View.GONE);
                mReplay.setVisibility(View.VISIBLE);
                mRefresh.setVisibility(View.VISIBLE);
                mShare.setVisibility(View.VISIBLE);
                break;
        }
    }

    public String getDriveString()
    {
        if(DEBUG)
        {
            return useLocalData();
        }
        else
        {
            Map<String, String> log = (Map<String, String>)getSharedPreferences("log", Context.MODE_PRIVATE).getAll();
            TreeMap sorted = new TreeMap<String, String>(new Comparator<String>()
            {
                public int compare(String s1, String s2)
                {
                    int value = 0;
                    long first = Long.parseLong(s1);
                    long second = Long.parseLong(s2);
                    if(first < second) value--;
                    if(first > second) value++;

                    return value;
                }
            });
            sorted.putAll(log);
            String[] keys = new String[sorted.size()];
            sorted.keySet().toArray(keys);

            StringBuilder builder = new StringBuilder();
            builder.append(HEADER+"\n");

            int i = 0;
            int length = keys.length;
            while(length-- > 0)
            {
                builder.append(sorted.get(keys[i++])+"\n");
            }

            return builder.toString();
        }
    }

    private String useLocalData()
    {
        if(mDebug == null)
        {
            StringBuilder builder = new StringBuilder();
            try
            {
                String line;
                BufferedReader reader = new BufferedReader(new InputStreamReader(getAssets().open("Small.csv")));
                while((line = reader.readLine()) != null)
                {
                    builder.append(line);
                    builder.append("\n");
                }
                reader.close();
            }catch(Exception e)
            {
            }

            mDebug = builder.toString();
        }

        return mDebug;
    }

    private void validateDrive()
    {
        Native wrapper = new Native(null);
        wrapper.engineInit();
        wrapper.loadProgram(getDriveString());
        wrapper.fetchN(1); //Pull off Header
        wrapper.fetchN(1);

        int Count = 1;
        float previousLatitude = wrapper.getLatitude();
        float previousLongitude = wrapper.getLongitude();
        while(!mCancel)
        {
            if(wrapper.fetchN(1) > 0)
            {
                Count++;
                try{ Thread.yield(); }catch(Exception e){}
            }
            else
            {
                mCancel = true;
                mProgress.dismiss();
            }
            if(Count % 5 == 0)
            {
                final float finalPrevLatitude = previousLatitude;
                final float finalPrevLongitude = previousLongitude;
                final float finalLatitude = wrapper.getLatitude();
                final float finalLongitude = wrapper.getLongitude();
                mMapView.post(new Runnable(){
                    public void run()
                    {
                        mMapView.loadUrl(
                            "javascript:drawPoly("
                           +finalPrevLatitude+","+finalPrevLongitude+","+finalLatitude+","+finalLongitude+")");
                    }
                });
                previousLatitude = finalLatitude;
                previousLongitude = finalLongitude;
            }
        }

        final int toastCount = Count;
        mMapView.post(new Runnable(){
            public void run()
            {
                Toast.makeText(getApplicationContext(), "Validated "+new Integer(toastCount)+" entries", Toast.LENGTH_SHORT).show();
            }
        });
    }

    public native void engineInit();
    public native void loadProgram(String binary);
    public native int fetchN(int n);
    public native int speedToRPM(int n);

    static { System.loadLibrary("spyroad"); }

}
