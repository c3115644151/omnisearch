package com.cy311.omnisearch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class OmnisearchLogger {
    private static final Logger LOGGER = LoggerFactory.getLogger("Omnisearch");
    public static void info(String msg) { LOGGER.info(msg); }
    public static void info(String msg, Object arg) { LOGGER.info(msg, arg); }
    public static void error(String msg, Throwable t) { LOGGER.error(msg, t); }
}