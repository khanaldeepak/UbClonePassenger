package com.example.islam.POJO;

import com.example.islam.concepts.RideLocation;
import com.google.android.gms.maps.model.LatLng;
import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Created by islam on 11/15/16.
 */
public class DriversResponse {


    @SerializedName(value = "status")
    public int status;

    @SerializedName(value = "drivers")
    public List<RideLocation> drivers;


    public List<RideLocation> getDrivers() {
        return drivers;
    }

    public void setDrivers(List<RideLocation> drivers) {
        this.drivers = drivers;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

}
