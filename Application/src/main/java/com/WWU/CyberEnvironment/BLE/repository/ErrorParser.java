package com.WWU.CyberEnvironment.BLE.repository;

public class ErrorParser {

    static String parse(String error) {
        String message = error.substring(error.indexOf(":") + 1);
        return message.replaceAll("[\\[\\]{}\"]", "").replaceAll(",", " ").replaceAll("email:", "");
    }
}
