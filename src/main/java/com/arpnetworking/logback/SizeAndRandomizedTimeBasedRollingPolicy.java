package com.arpnetworking.logback;

import ch.qos.logback.core.rolling.TimeBasedRollingPolicy;

/**
 * Implementation of SizeAndTimeBasedRollingPolicy which leverages SizeAndRandomizedTimeBasedFNATP from ArpNetworking
 *
 * @author Ryan Ascheman (rascheman at groupon dot com)
 */
public class SizeAndRandomizedTimeBasedRollingPolicy<E> extends TimeBasedRollingPolicy<E> {

    String maxFileSizeAsString;

    @Override
    public void start() {
        SizeAndRandomizedTimeBasedFNATP<E> sizeAndRandomizedTimeBasedFNATP = new SizeAndRandomizedTimeBasedFNATP<E>();
        if(maxFileSizeAsString == null) {
            addError("MaxFileSizeAsString property must be set");
            return;
        } else {
            addInfo("Achive files will be limied to ["+maxFileSizeAsString+"] each.");
        }

        sizeAndRandomizedTimeBasedFNATP.setMaxFileSize(maxFileSizeAsString);
        setTimeBasedFileNamingAndTriggeringPolicy(sizeAndRandomizedTimeBasedFNATP);

        // most work is done by the parent
        super.start();
    }

    public void setMaxFileSize(String maxFileSize) {
        this.maxFileSizeAsString = maxFileSize;
    }

    @Override
    public String toString() {
        return "com.arpnetworking.logback.SizeAndRandomizedTimeBasedRollingPolicy@"+this.hashCode();
    }
}
