package com.example.myphonebook;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class to implement rate limiting for security-sensitive actions.
 * Prevents brute-force attacks on authentication and limits frequent sync requests.
 */
public class RateLimiter {
    private static final String PREF_NAME = "RateLimiterPrefs";
    private static final long AUTH_WINDOW_MS = 15 * 60 * 1000; // 15 minutes
    private static final int MAX_AUTH_ATTEMPTS = 5;
    private static final long GENERAL_COOLDOWN = 2000; // 2 seconds
    private static long lastActionTime = 0;

    /**
     * Checks if a new authentication attempt is allowed.
     * Limits to 5 attempts within a rolling 15-minute window.
     *
     * @param context Application context for accessing SharedPreferences.
     * @return true if the attempt is allowed, false otherwise.
     */
    public static boolean canAttemptAuth(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String attemptsStr = prefs.getString("auth_attempts", "");
        long currentTime = System.currentTimeMillis();
        
        List<Long> attempts = parseAttempts(attemptsStr);
        List<Long> validAttempts = new ArrayList<>();
        
        for (Long time : attempts) {
            if (currentTime - time < AUTH_WINDOW_MS) {
                validAttempts.add(time);
            }
        }
        
        if (validAttempts.size() >= MAX_AUTH_ATTEMPTS) {
            return false;
        }
        
        validAttempts.add(currentTime);
        saveAttempts(prefs, validAttempts);
        return true;
    }

    private static List<Long> parseAttempts(String s) {
        List<Long> list = new ArrayList<>();
        if (s.isEmpty()) return list;
        String[] parts = s.split(",");
        for (String p : parts) {
            try {
                list.add(Long.parseLong(p));
            } catch (NumberFormatException ignored) {}
        }
        return list;
    }

    private static void saveAttempts(SharedPreferences prefs, List<Long> list) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            sb.append(list.get(i));
            if (i < list.size() - 1) sb.append(",");
        }
        prefs.edit().putString("auth_attempts", sb.toString()).apply();
    }

    /**
     * General rate limiting for UI actions (e.g., sync button).
     * Prevents rapid consecutive triggers by enforcing a 2-second cooldown.
     *
     * @return true if the action should be throttled (blocked), false if allowed.
     */
    public static boolean shouldThrottle() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastActionTime < GENERAL_COOLDOWN) {
            return true;
        }
        lastActionTime = currentTime;
        return false;
    }
}
