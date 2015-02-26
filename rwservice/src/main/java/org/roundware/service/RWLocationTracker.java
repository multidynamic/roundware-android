/**
 * Roundware Android code is released under the terms of the GNU General Public License.
 * See COPYRIGHT.txt, AUTHORS.txt, and LICENSE.txt in the project root directory for details.
 */
package org.roundware.service;

import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import java.util.Observable;


/**
 * Singleton class providing GPS/Location functionality. It extends Observable
 * so it can be observed to get updates on location changes. For testing
 * purposes, amongst others, it can be fixed at a specific location.
 * <p/>
 * For this singleton class you need to call the init method first, and then
 * can call the startLocationUpdates method to active the updates. Use the
 * stopLocationUpdates to cancel the updates (and save the battery).
 *
 * @author Rob Knapen
 */

// Some code adapted from https://github.com/googlesamples/android-SpeedTracker/blob/master/Wearable/src/main/java/com/example/android/wearable/speedtracker/WearableMainActivity.java
// Apache License

public class RWLocationTracker extends Observable implements GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener, com.google.android.gms.location.LocationListener {

    // debugging
    private final static String TAG = "RWLocationTracker";
    private final static boolean D = false;

    private static RWLocationTracker mSingleton;
    private Context mContext;

    private Location mLastLocation;
    private long mLastUpdateMs = -1;

    private boolean mFixedLocation;


    //Google recommends a minimum of 5 seconds
    private static final long UPDATE_INTERVAL_MS = 5 * 1000;
    private static final long FASTEST_INTERVAL_MS = 5 * 1000;

    private static final int LARGEST_INACCURACY_M = 150;
    private static final float SMALLEST_DISPLACEMENT_M = 0.01f;
    private static final float VERY_FAST_WALK_MPS = 2.0f;
    private GoogleApiClient mGoogleApiClient;

    /**
     * Accesses the singleton instance.
     * 
     * @return singleton instance of the class
     */
    public synchronized static RWLocationTracker instance() {
        if (mSingleton == null) {
            mSingleton = new RWLocationTracker();
        }
        return mSingleton;
    }


    /**
     * Hidden constructor for the singleton class.
     */
    private RWLocationTracker() {
        mFixedLocation = false;
    }


    /**
     * Checks if a fixed location has been set for the location tracker. When
     * set all location updates will be ignored.
     * 
     * @return true is a fixed location is set
     */
    public boolean isUsingFixedLocation() {
        return mFixedLocation;
    }


    /**
     * Sets the specified fixed location for the location tracker. Location
     * updates will be ignored until the fixed location is released again.
     * 
     * @param latitude of the fixed location
     * @param longitude of the fixed location
     */
    public void fixLocationAt(Double latitude, Double longitude) {
        Location l = new Location(LocationManager.PASSIVE_PROVIDER);
        l.setLatitude(latitude);
        l.setLongitude(longitude);
        fixLocationAt(l);
    }


    /**
     * Sets the specified fixed location for the location tracker. Location
     * updates will be ignored until the fixed location is released again.
     * 
     * @param location to use as fixed location
     */
    public void fixLocationAt(Location location) {
        updateWithNewLocation(location);
        mFixedLocation = true;
    }


    /**
     * Releases the fixed location and returns to using regular location
     * updates.
     */
    public void releaseFixedLocation() {
        mFixedLocation = false;
    }


    /**
     * Checks if the GPS is enabled and available on the device.
     * 
     * @return true when GPS is available
     */
    public boolean isGpsEnabled() {
        LocationManager lm = (LocationManager) mContext.getSystemService(Context.LOCATION_SERVICE);
        if (lm != null) {
            return lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
        }
        return false;
    }


    /**
     * Checks if the Network location provider is enabled on the device.
     * 
     * @return true when Network location is available
     */
    public boolean isNetworkLocationEnabled() {
        LocationManager lm = (LocationManager) mContext.getSystemService(Context.LOCATION_SERVICE);
        if (lm != null) {
            return lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        }
        return false;
    }


    /**
     * Initializes the singleton instance for the specified context. This
     * context is used to access the location providers. The method checks
     * if at least one location provider is available and returns true if
     * it is, false otherwise (no location data available).
     * 
     * @param context to be used
     * @return true if successful and a location provider is available
     */
    public void init(Context context) {

        mGoogleApiClient = new GoogleApiClient.Builder(context)
                .addApi(LocationServices.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();
        mGoogleApiClient.connect();

        mContext = context;
    }

    /**
     * Gets the last known location information. Note that this is not
     * Necessarily up-to-date.
     * 
     * @return Location with last know position
     */
    public Location getLastLocation() {
        return mLastLocation;
    }


    /**
     * Updates internal state according to the specified location.
     * 
     * @param location with new position data
     */
    private void updateWithNewLocation(Location location) {
        if (mFixedLocation) {
            return;
        }

        mLastLocation = location;
        mLastUpdateMs = System.currentTimeMillis();

        if (D) {
            if (location != null) {
                String msg = String.format("%s: (%.6f, %.6f) %.1fm", location.getProvider(),
                        location.getLatitude(), location.getLongitude(), location.getAccuracy());

                Toast.makeText(mContext, msg, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(mContext, "No location info", Toast.LENGTH_SHORT).show();
            }
        }

        setChanged();
        notifyObservers();

    }


    /**
     * Starts receiving location updates from the devices' location providers.
     * Initially the Network location will be used and a listener started to
     * wait for GPS availability and first coordinate fix, which then will be
     * switched to.
     *
     * @param minTime (msec) allowed between location updates
     * @param minDistance (m) for location updates
     * @param useGps when available on the device
     */
    public void startLocationUpdates(long minTime, float minDistance, boolean useGps) {
        //ignore this!
    }


    /**
     * Stops receiving location updates. Call this method when location info
     * is no longer needed, to reduce power consumption.
     */
    public void stopLocationUpdates() {
        if(mGoogleApiClient != null) {
            if (mGoogleApiClient.isConnected()) {
                LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
            }
            mGoogleApiClient.disconnect();
        }
    }


    @Override
    public void onConnected(Bundle bundle) {
        LocationRequest locationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setSmallestDisplacement(SMALLEST_DISPLACEMENT_M)
                .setInterval(UPDATE_INTERVAL_MS)
                .setFastestInterval(FASTEST_INTERVAL_MS);

        LocationServices.FusedLocationApi
                .requestLocationUpdates(mGoogleApiClient, locationRequest, this)
                .setResultCallback(new ResultCallback<Status>() {

                    @Override
                    public void onResult(Status status) {
                        if (status.getStatus().isSuccess()) {
                            if (Log.isLoggable(TAG, Log.DEBUG)) {
                                Log.d(TAG, "Successfully requested location updates");
                            }
                        } else {
                            Log.e(TAG,
                                    "Failed in requesting location updates, "
                                            + "status code: "
                                            + status.getStatusCode() + ", message: " + status
                                            .getStatusMessage());
                        }
                    }
                });
    }

    @Override
    public void onConnectionSuspended(int i) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "onConnectionSuspended(): connection to location client suspended");
        }
        LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.e(TAG, "onConnectionFailed(): connection to location client failed");
    }

    @Override
    public void onLocationChanged(Location location) {
        if( location.getAccuracy() < LARGEST_INACCURACY_M ) {
            if (location.getSpeed() > VERY_FAST_WALK_MPS) {
                Log.w(TAG, "Location speed is fast: " + location.getSpeed());
                //panic
                return;
            }
            if (mLastLocation != null && mLastUpdateMs != -1) {
                float calcSpeed = mLastLocation.distanceTo(location) / (System.currentTimeMillis() - mLastUpdateMs);
                if (calcSpeed > VERY_FAST_WALK_MPS) {
                    Log.w(TAG, "Calculated speed is fast: " + calcSpeed);
                    //panic
                    return;
                }
            }

            updateWithNewLocation(location);
        }
    }

}
