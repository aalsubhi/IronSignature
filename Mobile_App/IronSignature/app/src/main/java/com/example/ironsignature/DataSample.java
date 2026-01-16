package com.example.ironsignature;

public class DataSample {
    public final String rawData;
    public final double pressure;
    public final long timestamp;

    public DataSample(String rawData, double pressure, long timestamp) {
        this.rawData = rawData;
        this.pressure = pressure;
        this.timestamp = timestamp;
    }
}
