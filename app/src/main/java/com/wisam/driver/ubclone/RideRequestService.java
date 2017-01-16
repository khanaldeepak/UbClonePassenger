package com.wisam.driver.ubclone;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;

import com.wisam.driver.POJO.Driver;
import com.wisam.driver.POJO.DriverResponse;
import com.wisam.driver.concepts.Ride;
import com.wisam.driver.events.DriverAccepted;
import com.wisam.driver.events.DriverCanceled;
import com.wisam.driver.events.DriverCanceledUI;
import com.wisam.driver.events.DriverRejected;
import com.wisam.driver.events.DriverUpdatedStatus;
import com.wisam.driver.events.LogoutRequest;
import com.wisam.driver.events.RequestFinished;
import com.wisam.driver.events.RequestFinishedUI;
import com.wisam.driver.events.RideStarted;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class RideRequestService extends Service {
    private PrefManager prefManager;
    private final static String TAG = "RideRequestService";
    private Handler handler;
    int callCounter = 0;
    int validCode = 0;
    private Ride pendingRide;


    /**
     * Creates an IntentService.  Invoked by your subclass's constructor.
     *
     */
    public RideRequestService() {
        //super("RideRequestService");
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        prefManager = new PrefManager(this);
        pendingRide = new Ride(this);
        return START_STICKY;
    }

    @Subscribe
    public void onRideStarted(RideStarted rideStarted) {
        pendingRide.details = rideStarted.getDetails();
        if (!prefManager.isLoggedIn()){
            Log.i(TAG, "onHandleIntent: No ride is ongoing");
            return;
        }
        Log.d(TAG, "onRideStarted: Service ride id: "+ pendingRide.details.requestID);

//        Toast.makeText(this, "Inside service", Toast.LENGTH_SHORT).show();
        // Adding notification
        // Tapping the notification will open the specified Activity.
        Intent activityIntent = new Intent(this, MapsActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(), 0,
                activityIntent, PendingIntent.FLAG_UPDATE_CURRENT);



        // One request can be pending:
        pendingRide.details = prefManager.getCurrentRide();

        // This always shows up in the notifications area when this Service is running.
        final NotificationCompat.Builder builder = new NotificationCompat.Builder(this);
        builder.setSmallIcon(R.mipmap.ic_launcher);
        builder.setContentTitle(getText(R.string.app_name));
//        builder.setContentText("content text");

        Notification not = builder.build();
        startForeground(1, not);


        RestServiceConstants constants = new RestServiceConstants();
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(constants.getBaseUrl(this))
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        RestService service = retrofit.create(RestService.class);
        String email, password;
        if (!prefManager.isLoggedIn()){
            return;
        }
        email = prefManager.getUser().getEmail();

        password = prefManager.getUser().getPassword();

        if (pendingRide.details.pickup == null) Log.d(TAG, "isSet: Pickup is null");
        if (pendingRide.details.dest == null) Log.d(TAG, "isSet: dest is null");
        if (pendingRide.details.time == null) Log.d(TAG, "isSet: date is null");
        if (pendingRide.details.femaleOnly == null) Log.d(TAG, "isSet: femaleOnly is null");
        if (pendingRide.details.notes == null) Log.d(TAG, "isSet: notes is null");
        if (pendingRide.details.price == null) Log.d(TAG, "isSet: price is null");
        if (pendingRide.details.requestID.equals("-1")) Log.w(TAG, "isSet: requestID is -1");
        if (pendingRide.details.pickupText == null) Log.d(TAG, "isSet: pickupText is null");
        if (pendingRide.details.destText == null) Log.d(TAG, "isSet: destText is null");


        Call<DriverResponse> call = service.getDriver("Basic "+ Base64.encodeToString((email + ":" + password).getBytes(),Base64.NO_WRAP),
                pendingRide.details.pickup.toString(),
                pendingRide.details.dest.toString(),
                (pendingRide.details.now)?"now":String.valueOf(pendingRide.details.time.getTime().getTime()),
                pendingRide.details.femaleOnly,
                pendingRide.details.notes,
                pendingRide.details.price,
                pendingRide.details.requestID,
                pendingRide.details.pickupText,
                pendingRide.details.destText);



        handler = new Handler();
        Log.d(TAG, "run: Calling handler ");
        Log.d(TAG, "onHandleIntent: Got request_id: "+ pendingRide.details.requestID);
        callable(call, validCode);
    }
    public void callable(final Call<DriverResponse> call, final int mValidCode){
        Log.d(TAG, "callable: call: "+call.request().toString());
        // Check if user logged in: and if request hasn't been updated:
        if (!prefManager.isLoggedIn()){
            Log.i(TAG, "callable: user is not logged in");
            return;
        }
        if (mValidCode != validCode) {
            // Special case when the handler callback is removed but the request was ongoing. In its
            // response it will call this function with an outdated valid code
            Log.d(TAG, "callable: Not matching valid code: "+mValidCode+" ");
            return;
        }

        Log.d(TAG, "callable: posting delayed at valid code " + mValidCode);
        handler.postDelayed( new Runnable() {

            @Override
            public void run() {
                Log.d(TAG, "run: I am called for "+ callCounter++ +" service: "+call.request().toString());
                Log.d(TAG, "run: pending request id is  "+ pendingRide.details.requestID + " and valid code is " + mValidCode);
//                Toast.makeText(RideRequestService.this, "Request sent: " + callCounter, Toast.LENGTH_LONG).show();

                call.enqueue(new Callback<DriverResponse>() {
                   @Override
                   public void onResponse(Call<DriverResponse> call, Response<DriverResponse> response) {
                       if (response.isSuccessful()){
                           Log.d(TAG, "onResponse: is successful");

        // One request can be pending:
        pendingRide.details = prefManager.getCurrentRide();
//                           Toast.makeText(RideRequestService.this, "Successful. Status: "+response.body().getStatus(), Toast.LENGTH_LONG).show();
                           // There are 4 situations here:
                           // Status 0: When request is pending. request_id is returned.
                           // Status 1: No driver found
                           // Status 5: When this request has a non pending status. Return status in the error_msg
                           // Status 6: When a request is already accepted. Return request id in the error_msg
                           switch (response.body().getStatus()){
                               case 0:
                                   Log.i(TAG, "onResponse: status 0. Trying again in 30 seconds");
                                   callable(call.clone(), mValidCode);
                                   break;
                               case 3:
                                   Toast.makeText(RideRequestService.this, "Sorry, all drivers are busy. Try again later.", Toast.LENGTH_LONG).show();
                                   Log.i(TAG, "onResponse: status 1. No drivers available.");
                                   EventBus.getDefault().post(new RequestFinished(pendingRide.details.requestID));
                                   return;
                               case 5: // When this request has "completed" or "canceled" status.Return status in the error_msg
                                   EventBus.getDefault().post(new RequestFinished(pendingRide.details.requestID));
                                   prefManager.clearCurrentRide();
                                   return;
                               case 6: // When a request is already accepted.Return request id in the error_msg
                                   pendingRide.details.requestID = response.body().getErrorMessage();
                                   prefManager.setCurrentRide(pendingRide.details);
//                                   prefManager.setRideId(response.body().getErrorMessage());

                                   if (mValidCode != validCode) {
                                       // Special case when the handler callback is removed but the request was ongoing. In its
                                       // response it will call this function with an outdated valid code
                                       Log.d(TAG, "callable: valid code: "+mValidCode+" ");
                                       return;
                                   }
                                   EventBus.getDefault().post(new DriverAccepted(new Driver(
                                           "unknown",
                                           "unknown",
                                           "unknown",
                                           "unknown",
                                           "unknown"
                                   )));
                                   break;
                               case 1:
                                   EventBus.getDefault().post(new LogoutRequest());
                                   break;
                           }

                       } else {
                           Toast.makeText(RideRequestService.this, "Unknown error occurred", Toast.LENGTH_SHORT).show();
                       }

                   }

                   @Override
                   public void onFailure(Call<DriverResponse> call, Throwable t) {
                       Toast.makeText(RideRequestService.this, "Failed to connect. Trying again in 30 seconds", Toast.LENGTH_LONG).show();
                       Log.i(TAG, "onFailure: Failed to connect. Trying again in 30 seconds");
                       callable(call.clone(), validCode);
                   }
               });
            }
        }, 36000);
    }


    private void recursiveServerCall(final Call<DriverResponse> call, final int mValidCode, final String rideRequest){
        call.enqueue(new Callback<DriverResponse>() {
            @Override
            public void onResponse(Call<DriverResponse> call, Response<DriverResponse> response) {
                Log.d(TAG, "onResponse: " + response.raw());
                Log.d(TAG, "onResponse: " + response.toString());
                if (response.isSuccessful()){
                    // Toast.makeText(mapsActivity, "Successful. Status: "+response.body().getStatus(), Toast.LENGTH_SHORT).show();
                    // There are 4 situations here:
                    // Status 0: When request is pending. request_id is returned.
                    // Status 1: No driver found
                    // Status 5: When this request has a non pending status. Return status in the error_msg
                    // Status 6: When a request is already accepted. Return request id in the error_msg
                    switch (response.body().getStatus()){
                        case 0:
                            Log.i(TAG, "onResponse: status 0. Trying again in 30 seconds");
                            callable(call.clone(), validCode);
                            Log.d(TAG, "onDriverReject: Restarted successfully");
                            break;
                        case 3:
                            Toast.makeText(RideRequestService.this, "Sorry, all drivers are busy. Try again later.", Toast.LENGTH_LONG).show();
                            Log.i(TAG, "onResponse: status 1. No drivers available.");
                            EventBus.getDefault().post(new RequestFinished(pendingRide.details.requestID));
                            break;
                        case 5: // When this request has "completed" or "canceled" status.Return status in the error_msg
                            EventBus.getDefault().post(new RequestFinished(pendingRide.details.requestID));
                            prefManager.clearCurrentRide();
                            break;
                        case 6: // When a request is already accepted.Return request id in the error_msg
                            pendingRide.details.requestID = response.body().getErrorMessage();
                            prefManager.setCurrentRide(pendingRide.details);
//                                   prefManager.setRideId(response.body().getErrorMessage());

                            if (mValidCode != validCode) {
                                // Special case when the handler callback is removed but the request was ongoing. In its
                                // response it will call this function with an outdated valid code
                                Log.d(TAG, "callable: valid code: "+mValidCode+" ");
                                return;
                            }
                            EventBus.getDefault().post(new DriverAccepted(new Driver(
                                    "unknown",
                                    "unknown",
                                    "unknown",
                                    "unknown",
                                    "unknown"
                            )));
                            break;
                        case 1:
                            EventBus.getDefault().post(new LogoutRequest());
                            break;
                    }

                } else {
                    Toast.makeText(RideRequestService.this, R.string.unkown_error_occured, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<DriverResponse> call, Throwable t) {
                Toast.makeText(RideRequestService.this, R.string.failed_to_connect_to_the_server, Toast.LENGTH_SHORT).show();
                Log.i(TAG, "onFailure: Failed to connect. Trying again in 30 seconds");
                onDriverReject(new DriverRejected(rideRequest));
            }
        });
    }

    @Subscribe
    public void onDriverReject(final DriverRejected driverRejected){
        Log.d(TAG, "restartCallable: called. current id: " + prefManager.getCurrentRide().requestID + " Rejected id: " + driverRejected.getRequestID());

        if (!prefManager.getCurrentRide().requestID.equals(driverRejected.getRequestID())) {
            Log.e(TAG, "onDriverReject: Driver reject a not-current-ride" );
            return;
        }
        // One request can be pending:
        pendingRide.details = prefManager.getCurrentRide();

        if (!prefManager.isLoggedIn() || !pendingRide.details.getStatus().equals(PrefManager.FINDING_DRIVER) ){
            Log.w(TAG, "onDriverReject: Ride status is not finding driver");
            return;
        }


        try {
            handler.removeCallbacksAndMessages(null);
        }catch (NullPointerException e){
            Log.i(TAG, "onRequestCanceled: handler messages removed");
        }
        validCode++;
        Log.d(TAG, "onDriverReject: new Valid code is " + validCode);


        RestServiceConstants constants = new RestServiceConstants();
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(constants.getBaseUrl(this))
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        String email, password;

        email = prefManager.getUser().getEmail();
        password = prefManager.getUser().getPassword();
        RestService service = retrofit.create(RestService.class);
        Call<DriverResponse> call = service.getDriver("Basic "+ Base64.encodeToString((email + ":" + password).getBytes(),Base64.NO_WRAP),
                pendingRide.details.pickup.toString(),
                pendingRide.details.dest.toString(),
                (pendingRide.details.now)?"now":String.valueOf(pendingRide.details.time.getTime().getTime()),
                pendingRide.details.femaleOnly,
                pendingRide.details.notes,
                pendingRide.details.price,
                pendingRide.details.requestID,
                pendingRide.details.pickupText,
                pendingRide.details.destText);

        // Calling the function first here:
//        call.enqueue(new Callback<DriverResponse>() {
//            @Override
//            public void onResponse(Call<DriverResponse> call, Response<DriverResponse> response) {
//                Log.d(TAG, "onResponse: " + response.raw());
//                Log.d(TAG, "onResponse: " + response.toString());
//                if (response.isSuccessful()){
//                    // Toast.makeText(mapsActivity, "Successful. Status: "+response.body().getStatus(), Toast.LENGTH_SHORT).show();
//                    // There are 4 situations here:
//                    // Status 0: When request is pending. request_id is returned.
//                    // Status 1: No driver found
//                    // Status 5: When this request has a non pending status. Return status in the error_msg
//                    // Status 6: When a request is already accepted. Return request id in the error_msg
//                    switch (response.body().getStatus()){
//                        case 0:
//                            Log.i(TAG, "onResponse: status 0. Trying again in 30 seconds");
//                            callable(call.clone(), validCode);
//                            Log.d(TAG, "onDriverReject: Restarted successfully");
//                            break;
//                        case 3:
//                            Toast.makeText(RideRequestService.this, "Sorry, all drivers are busy. Try again later.", Toast.LENGTH_LONG).show();
//                            Log.i(TAG, "onResponse: status 1. No drivers available.");
//                            EventBus.getDefault().post(new RequestFinished(pendingRide.details.requestID));
//                            break;
//                        case 5: // When this request has "completed" or "canceled" status.Return status in the error_msg
//                        EventBus.getDefault().post(new RequestFinished(pendingRide.details.requestID));
//                        prefManager.clearCurrentRide();
//                        break;
//                        case 6: // When a request is already accepted.Return request id in the error_msg
//                            pendingRide.details.requestID = response.body().getErrorMessage();
//                            prefManager.setCurrentRide(pendingRide.details);
////                                   prefManager.setRideId(response.body().getErrorMessage());
//                            EventBus.getDefault().post(new DriverAccepted(new Driver(
//                                    "unknown",
//                                    "unknown",
//                                    "unknown",
//                                    "unknown",
//                                    "unknown"
//                            )));
//                            break;
//                        case 1:
//                            EventBus.getDefault().post(new LogoutRequest());
//                            break;
//                    }
//
//                } else {
//                    Toast.makeText(RideRequestService.this, R.string.unkown_error_occured, Toast.LENGTH_SHORT).show();
//                }
//            }
//
//            @Override
//            public void onFailure(Call<DriverResponse> call, Throwable t) {
//                Toast.makeText(RideRequestService.this, R.string.failed_to_connect_to_the_server, Toast.LENGTH_SHORT).show();
//                onDriverReject(new DriverRejected(driverRejected.getRequestID()));
//            }
//        });

//        callable(call, validCode);
        recursiveServerCall(call, validCode, driverRejected.getRequestID());
    }

    @Subscribe
    public void onDriverAccepted(DriverAccepted driverAccepted){
        Log.d(TAG, "onDriverAccepted: service id: " + prefManager.getCurrentRide().requestID + " driverAccepted request: " + driverAccepted.getRequestID());
        if (!prefManager.getCurrentRide().requestID.equals(driverAccepted.getRequestID())) {
            return;
        }
        validCode++;
        Log.d(TAG, "onDriverAccepted: A driver has accepted");
        Ride ride = new Ride(this);
        ride.details = prefManager.getRide(driverAccepted.getRequestID());
        ride.details.setStatus(PrefManager.DRIVER_ACCEPTED);
        ride.details.setDriver(driverAccepted.getDriver());

        prefManager.setCurrentRide(ride.details);
//        prefManager.setRideStatus(PrefManager.DRIVER_ACCEPTED);
//        prefManager.setRideDriver(driverAccepted.getDriver());
        try {
            handler.removeCallbacksAndMessages(null);
        }catch (NullPointerException e){
            Log.i(TAG, "onRequestCanceled: handler messages removed");
        }
    }

    @Subscribe
    public void onDriverUpdatedStatus(DriverUpdatedStatus driverUpdatedStatus){

        Ride ride = new Ride(this);
        ride.details = prefManager.getRide(driverUpdatedStatus.getRequestID());
        if (ride.details == null){
            Log.w(TAG, "onDriverUpdatedStatus: No ride found matching the request id: " + driverUpdatedStatus.getRequestID());
            return;
        }

        switch (driverUpdatedStatus.getMessage()){
            case RestServiceConstants.ON_THE_WAY:
                ride.details.setStatus(PrefManager.ON_THE_WAY);
//                prefManager.setRideStatus(PrefManager.ON_THE_WAY);
                break;

            case RestServiceConstants.ARRIVED_PICKUP:
                ride.details.setStatus(PrefManager.ARRIVED_PICKUP);
//                prefManager.setRideStatus(PrefManager.ARRIVED_PICKUP);
                break;

            case RestServiceConstants.PASSENGER_ONBOARD:
                ride.details.setStatus(PrefManager.PASSENGER_ONBOARD);
//                prefManager.setRideStatus(PrefManager.PASSENGER_ONBOARD);
                break;

            case RestServiceConstants.ARRIVED_DEST:
                ride.details.setStatus(PrefManager.ARRIVED_DEST);
//                prefManager.setRideStatus(PrefManager.ARRIVED_DEST);
                break;

            case RestServiceConstants.COMPLETED:
                ride.details.setStatus(PrefManager.COMPLETED);
//                prefManager.setRideStatus(PrefManager.COMPLETED);
                break;

        }
        if (prefManager.getCurrentRide().requestID.equals(driverUpdatedStatus.getRequestID())) {
            prefManager.setCurrentRide(ride.details);
        } else {
            prefManager.updateOngoingRide(ride.details);
        }

    }

    @Subscribe
    public void onRequestCanceled(RequestFinished requestCanceled){
        if (prefManager.getCurrentRide().requestID.equals(requestCanceled.getRequestID()))
            EventBus.getDefault().post(new RequestFinishedUI(requestCanceled.getRequestID()));

        Log.d(TAG, "onRequestCanceled: called");
        //PrefManagerRide ride = new Ride();
//        ride.details = prefManager.getRide(requestCanceled.getRequestID());
        prefManager.removeOngoingRide(requestCanceled.getRequestID());
        Log.d(TAG, "onRequestCanceled: removed: "+ requestCanceled.getRequestID());
//        prefManager.setRideDriver(new Driver(getString(R.string.dash),"",getString(R.string.dash),getString(R.string.dash),""));
//        prefManager.setRideStatus(PrefManager.NO_RIDE);
//        prefManager.setRideId("");
        validCode++;

        if (prefManager.getOngoingRides().size() == 0) stopForeground(true);
        try {
            handler.removeCallbacksAndMessages(null);
        }catch (NullPointerException e){
            Log.i(TAG, "onRequestCanceled: handler messages removed");
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        EventBus.getDefault().register(this);
    }

    @Override
    public void onDestroy() {
        EventBus.getDefault().unregister(this);
        super.onDestroy();
    }

    @Subscribe
    public void onDriverCanceled(DriverCanceled driverCanceled){
        if (prefManager.getCurrentRide().requestID.equals(driverCanceled.getRequestID()))
            EventBus.getDefault().post(new DriverCanceledUI(driverCanceled.getRequestID()));

        onRequestCanceled(new RequestFinished(driverCanceled.getRequestID()));
    }

    @Subscribe
    public void onLogoutRequest(LogoutRequest logoutRequest){
        prefManager.setIsLoggedIn(false);
        onRequestCanceled(new RequestFinished(pendingRide.details.requestID));
        stopSelf();
    }

}
