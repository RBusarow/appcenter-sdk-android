/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License.
 */

package com.microsoft.appcenter.storage;

import com.microsoft.appcenter.storage.models.TokenResult;
import com.microsoft.appcenter.utils.AppCenterLog;
import com.microsoft.appcenter.utils.storage.SharedPreferencesManager;

import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;
import java.util.TimeZone;

/**
 * Token cache service.
 */
public class TokenManager {

    /**
     * Shared token manager instance.
     */
    private static TokenManager sInstance;

    private TokenManager() {
    }

    /**
     * Get token manager instance.
     *
     * @return Shared token manager instance.
     */
    public static TokenManager getInstance() {
        if (sInstance == null) {
            sInstance = new TokenManager();
        }
        return sInstance;
    }

    /**
     * List all cached tokens' partition names.
     *
     * @return Set of cached tokens' partition name.
     */
    private Set<String> getPartitionNames() {
        Set<String> partitionNames = SharedPreferencesManager.getStringSet(Constants.PARTITION_NAMES);
        return partitionNames == null ? new HashSet<String>() : partitionNames;
    }

    /**
     * Get the cached token access to given partition.
     *
     * @param partitionName The partition name for get the token.
     * @return Cached token.
     */
    public TokenResult getCachedToken(String partitionName) {
        TokenResult token = Utils.getGson().fromJson(SharedPreferencesManager.getString(partitionName), TokenResult.class);
        if (token != null) {
            Calendar utcCalendar = Calendar.getInstance(TimeZone.getTimeZone("GMT"));

            /* Token result was cached but status was not 'succeed'. */
            if (!token.status().equals(Constants.TOKEN_RESULT_SUCCEED)) {
                AppCenterLog.warn(Constants.LOG_TAG, String.format("Cached token result was failed for partition '%s'", partitionName));
                return null;
            }

            /* The token is expired. */
            if (utcCalendar.getTime().compareTo(token.expiresOn()) > 0) {
                AppCenterLog.warn(Constants.LOG_TAG, String.format("Cached token result is expired for partition '%s'", partitionName));
                removeCachedToken(partitionName);
                return null;
            }
            AppCenterLog.debug(Constants.LOG_TAG, String.format("Retrieved token from cache for partition '%s'", partitionName));
            return token;
        }
        AppCenterLog.warn(Constants.LOG_TAG, String.format("Failed to retrieve token or none found in cache for partition '%s'", partitionName));
        return null;
    }

    /**
     * Set the token to cache.
     *
     * @param tokenResult The token to be cached.
     */
    public synchronized void setCachedToken(TokenResult tokenResult) {
        Set<String> partitionNamesSet = getPartitionNames();
        if (!partitionNamesSet.contains(tokenResult.partition())) {
            partitionNamesSet.add(tokenResult.partition());
            SharedPreferencesManager.putStringSet(Constants.PARTITION_NAMES, partitionNamesSet);
        }
        SharedPreferencesManager.putString(tokenResult.partition(), Utils.getGson().toJson(tokenResult));
    }

    /**
     * Remove the cached token access to specific partition.
     *
     * @param partitionName The partition name used to access the token.
     */
    private synchronized void removeCachedToken(String partitionName) {
        Set<String> partitionNamesSet = getPartitionNames();
        partitionNamesSet.remove(partitionName);
        SharedPreferencesManager.putStringSet(Constants.PARTITION_NAMES, partitionNamesSet);
        SharedPreferencesManager.remove(partitionName);
        AppCenterLog.info(Constants.LOG_TAG, String.format("Removed token for partition '%s'", partitionName));
    }

    /**
     * Remove all the cached access tokens for all partition names.
     */
    @SuppressWarnings("WeakerAccess")
    public synchronized void removeAllCachedTokens() {
        Set<String> partitionNamesSet = getPartitionNames();
        for (String partitionName : partitionNamesSet) {
            if (partitionName.equals(Constants.READONLY)) {
                continue;
            }
            SharedPreferencesManager.remove(partitionName);
        }
        partitionNamesSet.clear();
        SharedPreferencesManager.putStringSet(Constants.PARTITION_NAMES, partitionNamesSet);
        AppCenterLog.info(Constants.LOG_TAG, String.format("Removed all tokens in all partitions"));
    }
}
