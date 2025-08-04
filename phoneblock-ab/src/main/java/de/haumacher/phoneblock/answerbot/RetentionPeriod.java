/*
 * Copyright (c) 2023 Bernhard Haumacher et al. All Rights Reserved.
 */
package de.haumacher.phoneblock.answerbot;

/**
 * Enumeration of available retention periods for automatic call cleanup.
 */
public enum RetentionPeriod {
    /** Never delete calls automatically */
    NEVER(0),
    
    /** Delete calls older than one week */
    WEEK(7),
    
    /** Delete calls older than one month */
    MONTH(30),
    
    /** Delete calls older than three months */
    QUARTER(90),
    
    /** Delete calls older than one year */
    YEAR(365);
    
    private final int days;
    
    RetentionPeriod(int days) {
        this.days = days;
    }
    
    /**
     * The number of days after which calls should be deleted.
     * 
     * @return Number of days, 0 means never delete.
     */
    public int getDays() {
        return days;
    }
    
    /**
     * Whether this retention period enables automatic deletion.
     */
    public boolean isEnabled() {
        return days > 0;
    }
    
    /**
     * Calculate the cutoff timestamp for this retention period.
     * 
     * @return Timestamp before which calls should be deleted, 0 if retention is disabled.
     */
    public long getCutoffTime() {
        if (!isEnabled()) {
            return 0;
        }
        return System.currentTimeMillis() - (days * 24L * 60 * 60 * 1000);
    }
}
