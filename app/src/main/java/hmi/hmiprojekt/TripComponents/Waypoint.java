package hmi.hmiprojekt.TripComponents;

import android.support.annotation.NonNull;

import com.google.android.gms.maps.model.LatLng;

import java.io.File;
import java.util.Date;

public class Waypoint implements Comparable {

    private File img;
    private String name;
    private String desc;
    private Date timestamp;
    private double[] coordinates;

    public Waypoint(File img
            , String name
            , String desc
            , Date timestamp
            , double[] coordinates) {
        this.img = img;
        this.name = name;
        this.desc = desc;
        this.timestamp = timestamp;
        this.coordinates = coordinates;
    }

    // getters
    public File getImg() {
        return img;
    }

    public String getName() {
        return name;
    }

    public String getDesc() {
        return desc;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public double[] getCoordinates() {return coordinates;}

    public LatLng getLatLng() {
        return new LatLng(coordinates[0], coordinates[1]);
    }

    @Override
    public int compareTo(@NonNull Object o) {
        if (!(o instanceof Waypoint)) return 0;
        Waypoint w = (Waypoint) o;
        int tmp = w.getTimestamp().compareTo(timestamp);
        if (tmp > 0) return -1;
        else if (tmp < 0) return 1;
        return tmp;
    }
}
