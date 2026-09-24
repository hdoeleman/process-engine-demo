package com.example.processengine.demo;

/** One advisory situation, shaped for the demo UI's warning banner -- {@code detail} is only ever
 *  non-null for a compensation failure (the compensating handler's own error message). */
public record DemoWarning(String type, String orderId, String processKey, String stepKey, String detail) {}
