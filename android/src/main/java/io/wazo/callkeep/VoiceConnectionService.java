/*
 * Copyright (c) 2016-2019 The CallKeep Authors (see the AUTHORS file)
 * SPDX-License-Identifier: ISC, MIT
 *
 * Permission to use, copy, modify, and distribute this software for any
 * purpose with or without fee is hereby granted, provided that the above
 * copyright notice and this permission notice appear in all copies.
 *
 * THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
 * WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR
 * ANY SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
 * WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
 * ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF
 * OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
 */

package io.wazo.callkeep;

import static io.wazo.callkeep.Constants.ACTION_ANSWER_CALL;
import static io.wazo.callkeep.Constants.ACTION_END_CALL;

import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningTaskInfo;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Activity;
import android.content.res.Resources;
import android.content.Intent;
import android.content.Context;
import android.content.ComponentName;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.Voice;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.telecom.CallAudioState;
import android.telecom.Connection;
import android.telecom.ConnectionRequest;
import android.telecom.ConnectionService;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.util.Log;
import android.database.Cursor;
import android.provider.ContactsContract;
import android.graphics.Bitmap;
import android.provider.MediaStore;
import java.io.IOException;

import com.facebook.react.HeadlessJsTaskService;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static io.wazo.callkeep.Constants.ACTION_AUDIO_SESSION;
import static io.wazo.callkeep.Constants.ACTION_ONGOING_CALL;
import static io.wazo.callkeep.Constants.ACTION_CHECK_REACHABILITY;
import static io.wazo.callkeep.Constants.ACTION_WAKE_APP;
import static io.wazo.callkeep.Constants.EXTRA_CALLER_NAME;
import static io.wazo.callkeep.Constants.EXTRA_CALL_NUMBER;
import static io.wazo.callkeep.Constants.EXTRA_CALL_NUMBER_SCHEMA;
import static io.wazo.callkeep.Constants.EXTRA_CALL_UUID;
import static io.wazo.callkeep.Constants.EXTRA_DISABLE_ADD_CALL;
import static io.wazo.callkeep.Constants.FOREGROUND_SERVICE_TYPE_MICROPHONE;
import static io.wazo.callkeep.Constants.ACTION_ON_CREATE_CONNECTION_FAILED;
import static io.wazo.callkeep.Constants.EXTRA_PAYLOAD;

// @see https://github.com/kbagchiGWC/voice-quickstart-android/blob/9a2aff7fbe0d0a5ae9457b48e9ad408740dfb968/exampleConnectionService/src/main/java/com/twilio/voice/examples/connectionservice/VoiceConnectionService.java
@TargetApi(Build.VERSION_CODES.M)
public class VoiceConnectionService extends ConnectionService {
    private static Boolean isAvailable = false;
    private static Boolean isInitialized = false;
    private static Boolean isReachable = false;
    private static Boolean canMakeMultipleCalls = true;
    private static String notReachableCallUuid;
    private static ConnectionRequest currentConnectionRequest;
    private static PhoneAccountHandle phoneAccountHandle;
    private static String TAG = "RNCallKeep";
    private static int NOTIFICATION_ID = -4567;

    // Delay events sent to RNCallKeepModule when there is no listener available
    private static List<Bundle> delayedEvents = new ArrayList<Bundle>();

    public static Map<String, VoiceConnection> currentConnections = new HashMap<>();
    public static Boolean hasOutgoingCall = false;
    public static VoiceConnectionService currentConnectionService = null;

    public static Connection getConnection(String connectionId) {
        if (currentConnections.containsKey(connectionId)) {
            return currentConnections.get(connectionId);
        }
        return null;
    }

    public VoiceConnectionService() {
        super();
        Log.d(TAG, "[VoiceConnectionService] Constructor");
        currentConnectionRequest = null;
        currentConnectionService = this;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "[VoiceConnectionService] onStartCommand called. intent: " + intent);
        if (intent != null && intent.getAction() != null) {
            String action = intent.getAction();
            Log.d(TAG, "[VoiceConnectionService] onStartCommand action: " + action);
            if (ACTION_ANSWER_CALL.equals(action) || ACTION_END_CALL.equals(action)) {
                Log.d(TAG, "[VoiceConnectionService] Forwarding notification action: " + action);
                Intent localIntent = new Intent(action);
                if (intent.getExtras() != null) {
                    localIntent.putExtras(intent.getExtras());
                }
                if (ACTION_ANSWER_CALL.equals(action)) {
                    boolean result = LocalBroadcastManager.getInstance(this).sendBroadcast(localIntent);
                    Log.d(TAG, "[VoiceConnectionService] Broadcast sent, result: " + result);
                }

                if (ACTION_ANSWER_CALL.equals(action)) {
                    String uuid = null;
                    if (intent.getExtras() != null) {
                        if (intent.getExtras().containsKey("attributeMap")) {
                            HashMap<String, String> map = (HashMap<String, String>) intent.getExtras().getSerializable("attributeMap");
                            if (map != null) uuid = map.get(EXTRA_CALL_UUID);
                        } else {
                            uuid = intent.getExtras().getString(EXTRA_CALL_UUID);
                        }
                    }
                    if (uuid != null) {
                        try {
                            updateToOngoing(this, uuid);
                        } catch (Exception e) {}
                    }
                }
                
                if (ACTION_END_CALL.equals(action)) {
                    Log.d(TAG, "[VoiceConnectionService] Ending call from notification, stopping foreground service");
                    String uuid = null;
                    if (intent.getExtras() != null) {
                        if (intent.getExtras().containsKey("attributeMap")) {
                            HashMap<String, String> map = (HashMap<String, String>) intent.getExtras().getSerializable("attributeMap");
                            if (map != null) uuid = map.get(EXTRA_CALL_UUID);
                        } else {
                            uuid = intent.getExtras().getString(EXTRA_CALL_UUID);
                        }
                    }
                    if (uuid != null) {
                        try {
                            android.telecom.Connection conn = getConnection(uuid);
                            if (conn != null) {
                                conn.onDisconnect();
                            } else {
                                deinitConnection(uuid);
                            }
                        } catch (Exception e) {}
                    }
                }
            }
        }
        return super.onStartCommand(intent, flags, startId);
    }

    public static void setPhoneAccountHandle(PhoneAccountHandle phoneAccountHandle) {
        VoiceConnectionService.phoneAccountHandle = phoneAccountHandle;
    }

    public static void setAvailable(Boolean value) {
        Log.d(TAG, "[VoiceConnectionService] setAvailable: " + (value ? "true" : "false"));
        if (value) {
            setInitialized(true);
        }

        isAvailable = value;
    }

    public static WritableMap getSettings(@Nullable Context context) {
       WritableMap settings = RNCallKeepModule.getSettings(context);
       return settings;
    }

    public static ReadableMap getForegroundSettings(@Nullable Context context) {
       WritableMap settings = VoiceConnectionService.getSettings(context);
       if (settings == null) {
          return null;
       }

       return settings.getMap("foregroundService");
    }

    public static void setCanMakeMultipleCalls(Boolean value) {
        Log.d(TAG, "[VoiceConnectionService] setCanMakeMultipleCalls: " + (value ? "true" : "false"));

        VoiceConnectionService.canMakeMultipleCalls = value;
    }

    public static void setReachable() {
        Log.d(TAG, "[VoiceConnectionService] setReachable");
        isReachable = true;
        VoiceConnectionService.currentConnectionRequest = null;
    }

    public static void setInitialized(boolean value) {
        Log.d(TAG, "[VoiceConnectionService] setInitialized: " + (value ? "true" : "false"));

        isInitialized = value;
    }

    public static void deinitConnection(String connectionId) {
        Log.d(TAG, "[VoiceConnectionService] deinitConnection:" + connectionId);
        VoiceConnectionService.hasOutgoingCall = false;

        currentConnectionService.stopForegroundService();

        if (currentConnections.containsKey(connectionId)) {
            currentConnections.remove(connectionId);
        }


    }

    public static void updateToOngoing(Context context, String uuid) {
        if (currentConnectionService != null) {
            Connection conn = getConnection(uuid);
            if (conn != null) {
                com.facebook.react.bridge.WritableMap settings = VoiceConnectionService.getSettings(context);
                boolean isSelfManaged = false;
                try {
                    isSelfManaged = settings != null && settings.hasKey("selfManaged") && settings.getBoolean("selfManaged");
                } catch (Exception e) {}
                
                if (isSelfManaged) {
                    currentConnectionService.updateForegroundServiceToOngoing(uuid, conn);
                }
            }
        }
    }

    public static void setState(String uuid, int state) {
        Connection conn = VoiceConnectionService.getConnection(uuid);
        if (conn == null) {
            Log.w(TAG, "[VoiceConnectionService] setState ignored because no connection found, uuid: " + uuid);
            return;
        }

        switch (state) {
            case Connection.STATE_ACTIVE:
                conn.setActive();
                VoiceConnectionService.updateToOngoing(null, uuid);
                break;
            case Connection.STATE_DIALING:
                conn.setDialing();
                break;
            case Connection.STATE_HOLDING:
                conn.setOnHold();
                break;
            case Connection.STATE_INITIALIZING:
                conn.setInitializing();
                break;
            case Connection.STATE_RINGING:
                conn.setRinging();
                break;
        }
    }

    @Override
    public Connection onCreateIncomingConnection(PhoneAccountHandle connectionManagerPhoneAccount, ConnectionRequest request) {
        final Bundle extra = request.getExtras();
        Uri number = request.getAddress();
        String name = extra.getString(EXTRA_CALLER_NAME);
        String callUUID = extra.getString(EXTRA_CALL_UUID);
        Boolean isForeground = VoiceConnectionService.isRunning(this.getApplicationContext());
        WritableMap settings = this.getSettings(this);
        Integer timeout = settings.hasKey("displayCallReachabilityTimeout") ? settings.getInt("displayCallReachabilityTimeout") : null;

        Log.d(TAG, "[VoiceConnectionService] onCreateIncomingConnection, name:" + name + ", number" + number +
            ", isForeground: " + isForeground + ", isReachable:" + isReachable + ", timeout: " + timeout);

        Connection incomingCallConnection = createConnection(request);
        incomingCallConnection.setRinging();
        incomingCallConnection.setInitialized();

        Bundle payload = extra.getBundle(EXTRA_PAYLOAD);
        startForegroundService(true, name, number != null ? number.getSchemeSpecificPart() : null, callUUID, payload);

        if (timeout != null) {
            this.checkForAppReachability(callUUID, timeout);
        }

        return incomingCallConnection;
    }

    @Override
    public Connection onCreateOutgoingConnection(PhoneAccountHandle connectionManagerPhoneAccount, ConnectionRequest request) {
        VoiceConnectionService.hasOutgoingCall = true;

        Bundle extras = request.getExtras();
        String callUUID = extras.getString(EXTRA_CALL_UUID);

        if(callUUID == null || callUUID == ""){
          callUUID = UUID.randomUUID().toString();
        }

        Log.d(TAG, "[VoiceConnectionService] onCreateOutgoingConnection, uuid:"  + callUUID);

        if (!isInitialized && !isReachable) {
            this.notReachableCallUuid = callUUID;
            this.currentConnectionRequest = request;
            this.checkReachability();
        }

        return this.makeOutgoingCall(request, callUUID, false);
    }

    private Connection makeOutgoingCall(ConnectionRequest request, String uuid, Boolean forceWakeUp) {
        Bundle extras = request.getExtras();
        Connection outgoingCallConnection = null;
        String number = request.getAddress().getSchemeSpecificPart();
        String extrasNumber = extras.getString(EXTRA_CALL_NUMBER);
        String displayName = extras.getString(EXTRA_CALLER_NAME);
        Boolean isForeground = VoiceConnectionService.isRunning(this.getApplicationContext());

        Log.d(TAG, "[VoiceConnectionService] makeOutgoingCall, uuid:" + uuid + ", number: " + number + ", displayName:" + displayName);

        // Wakeup application if needed
        if (!isForeground || forceWakeUp) {
            Log.d(TAG, "[VoiceConnectionService] onCreateOutgoingConnection: Waking up application");
            this.wakeUpApplication(uuid, number, displayName);
        } else if (!this.canMakeOutgoingCall() && isReachable) {
            Log.d(TAG, "[VoiceConnectionService] onCreateOutgoingConnection: not available");
            return Connection.createFailedConnection(new DisconnectCause(DisconnectCause.LOCAL));
        }

        // TODO: Hold all other calls
        if (extrasNumber == null || !extrasNumber.equals(number)) {
            extras.putString(EXTRA_CALL_UUID, uuid);
            extras.putString(EXTRA_CALLER_NAME, displayName);
            extras.putString(EXTRA_CALL_NUMBER, number);
        }

        if (!canMakeMultipleCalls) {
            Log.d(TAG, "[VoiceConnectionService] onCreateOutgoingConnection: disabling multi calls");
            extras.putBoolean(EXTRA_DISABLE_ADD_CALL, true);
        }

        outgoingCallConnection = createConnection(request);
        outgoingCallConnection.setDialing();
        outgoingCallConnection.setAudioModeIsVoip(true);
        outgoingCallConnection.setCallerDisplayName(displayName, TelecomManager.PRESENTATION_ALLOWED);

        startForegroundService(false, displayName, number, uuid, null);

        // ‍️Weirdly on some Samsung phones (A50, S9...) using `setInitialized` will not display the native UI ...
        // when making a call from the native Phone application. The call will still be displayed correctly without it.
        if (!Build.MANUFACTURER.equalsIgnoreCase("Samsung")) {
            Log.d(TAG, "[VoiceConnectionService] onCreateOutgoingConnection: initializing connection on non-Samsung device");
            outgoingCallConnection.setInitialized();
        }

        HashMap<String, String> extrasMap = this.bundleToMap(extras);

        sendCallRequestToActivity(ACTION_ONGOING_CALL, extrasMap, true);
        sendCallRequestToActivity(ACTION_AUDIO_SESSION, extrasMap, true);

        Log.d(TAG, "[VoiceConnectionService] onCreateOutgoingConnection: done");

        return outgoingCallConnection;
    }

    private void startForegroundService(boolean isIncomingCall, String callerName, String callerNumber, String callUUID, @Nullable Bundle payload) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            // Foreground services not required before SDK 28
            return;
        }
        Log.d(TAG, "[VoiceConnectionService] startForegroundService");
        ReadableMap foregroundSettings = getForegroundSettings(null);

        if (!this.isForegroundServiceConfigured()) {
            Log.w(TAG, "[VoiceConnectionService] Not creating foregroundService because not configured");
            return;
        }

        WritableMap settings = getSettings(null);
        boolean isSelfManaged = false;
        try {
            isSelfManaged = settings != null && settings.hasKey("selfManaged") && settings.getBoolean("selfManaged");
        } catch (Exception e) {}

        String resolvedContactName = callerName;
        Bitmap contactPhoto = null;

        if (isSelfManaged && isIncomingCall && callerNumber != null) {
            try {
                Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(callerNumber));
                String[] projection = new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME, ContactsContract.PhoneLookup.PHOTO_URI};
                Cursor cursor = getContentResolver().query(uri, projection, null, null, null);
                if (cursor != null) {
                    if (cursor.moveToFirst()) {
                        String name = cursor.getString(0);
                        if (name != null && !name.isEmpty()) {
                            resolvedContactName = name;
                        }
                        String photoUriString = cursor.getString(1);
                        if (photoUriString != null) {
                            contactPhoto = MediaStore.Images.Media.getBitmap(getContentResolver(), Uri.parse(photoUriString));
                        }
                    }
                    cursor.close();
                }
            } catch (Exception e) {
                Log.w(TAG, "[VoiceConnectionService] Failed to lookup contact: " + e.toString());
            }
        }

        String NOTIFICATION_CHANNEL_ID = foregroundSettings.getString("channelId");
        if (isSelfManaged && isIncomingCall) {
            NOTIFICATION_CHANNEL_ID += "_incoming_call";
        }
        String channelName = foregroundSettings.getString("channelName");
        int importance = (isSelfManaged && isIncomingCall) ? NotificationManager.IMPORTANCE_HIGH : NotificationManager.IMPORTANCE_NONE;
        
        NotificationChannel chan = new NotificationChannel(NOTIFICATION_CHANNEL_ID, channelName, importance);
        if (isSelfManaged && isIncomingCall) {
            chan.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            chan.enableLights(true);
            chan.enableVibration(true);
            chan.setVibrationPattern(new long[]{0, 1000, 500, 1000, 500});
            Uri ringtoneUri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_RINGTONE);
            chan.setSound(ringtoneUri, new android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
        } else {
            chan.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        }

        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        assert manager != null;
        manager.createNotificationChannel(chan);

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID);
        notificationBuilder.setOngoing(isSelfManaged);
        notificationBuilder.setAutoCancel(false);
        notificationBuilder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
        notificationBuilder.setDefaults(NotificationCompat.DEFAULT_VIBRATE);

        android.widget.RemoteViews customView = null;
        android.widget.RemoteViews customSmallView = null;

        if (isSelfManaged && isIncomingCall) {
            String packageName = getPackageName();
            int layoutId = getResources().getIdentifier("layout_custom_notification", "layout", packageName);
            int smallLayoutId = getResources().getIdentifier("layout_custom_small_ex_notification", "layout", packageName);
            
            if (layoutId != 0 && smallLayoutId != 0) {
                customView = new android.widget.RemoteViews(packageName, layoutId);
                customSmallView = new android.widget.RemoteViews(packageName, smallLayoutId);
                
                String displayName = resolvedContactName != null ? resolvedContactName : foregroundSettings.getString("notificationTitle");
                
                customView.setTextViewText(getResources().getIdentifier("tvNameCaller", "id", packageName), displayName);
                customSmallView.setTextViewText(getResources().getIdentifier("tvNameCaller", "id", packageName), displayName);
                
                if (callerNumber != null) {
                    customView.setTextViewText(getResources().getIdentifier("tvNumber", "id", packageName), callerNumber);
                    customSmallView.setTextViewText(getResources().getIdentifier("tvNumber", "id", packageName), callerNumber);
                }
                if (contactPhoto != null) {
                    customView.setImageViewBitmap(getResources().getIdentifier("ivAvatar", "id", packageName), contactPhoto);
                    customView.setViewVisibility(getResources().getIdentifier("ivAvatar", "id", packageName), android.view.View.VISIBLE);
                    customSmallView.setImageViewBitmap(getResources().getIdentifier("ivAvatar", "id", packageName), contactPhoto);
                    customSmallView.setViewVisibility(getResources().getIdentifier("ivAvatar", "id", packageName), android.view.View.VISIBLE);
                }

                notificationBuilder.setCustomContentView(customSmallView);
                notificationBuilder.setCustomBigContentView(customView);
                notificationBuilder.setCustomHeadsUpContentView(customSmallView);
            } else {
                notificationBuilder.setContentTitle(resolvedContactName != null ? resolvedContactName : foregroundSettings.getString("notificationTitle"))
                    .setContentText("Incoming call...");
                if (contactPhoto != null) {
                    notificationBuilder.setLargeIcon(contactPhoto);
                }
            }
            notificationBuilder.setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_CALL);
        } else {
            notificationBuilder.setContentTitle(foregroundSettings.getString("notificationTitle"))
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setCategory(NotificationCompat.CATEGORY_SERVICE);
        }

        final int flag = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE : PendingIntent.FLAG_UPDATE_CURRENT;

        if (isSelfManaged && isIncomingCall) {
            Intent fullScreenIntent = new Intent(this, IncomingCallActivity.class);
            fullScreenIntent.putExtra(EXTRA_CALL_UUID, callUUID);
            fullScreenIntent.putExtra(EXTRA_CALLER_NAME, resolvedContactName != null ? resolvedContactName : foregroundSettings.getString("notificationTitle"));
            fullScreenIntent.putExtra(EXTRA_CALL_NUMBER, callerNumber);
            if (payload != null) {
                fullScreenIntent.putExtra(EXTRA_PAYLOAD, payload);
            }
            fullScreenIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            
            PendingIntent fullScreenPendingIntent = PendingIntent.getActivity(this, NOTIFICATION_ID, fullScreenIntent, flag);
            
            android.app.KeyguardManager keyguardManager = (android.app.KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
            android.os.PowerManager powerManager = (android.os.PowerManager) getSystemService(Context.POWER_SERVICE);
            boolean isScreenOn = powerManager != null && powerManager.isInteractive();
            boolean isLocked = keyguardManager != null && keyguardManager.isKeyguardLocked();
            
            notificationBuilder.setFullScreenIntent(fullScreenPendingIntent, true);
            notificationBuilder.setContentIntent(fullScreenPendingIntent);

            Intent answerIntent = new Intent(this, VoiceConnectionService.class);
            answerIntent.setAction(ACTION_ANSWER_CALL);
            Bundle answerExtras = new Bundle();
            HashMap<String, String> answerMap = new HashMap<>();
            answerMap.put(EXTRA_CALL_UUID, callUUID);
            answerMap.put(EXTRA_CALL_NUMBER, callerNumber);
            answerMap.put(EXTRA_CALLER_NAME, resolvedContactName);
            answerExtras.putSerializable("attributeMap", answerMap);
            if (payload != null) {
                answerExtras.putBundle(EXTRA_PAYLOAD, payload);
            }
            answerIntent.putExtras(answerExtras);
            PendingIntent answerPendingIntent = PendingIntent.getService(this, 1, answerIntent, flag);

            Intent declineIntent = new Intent(this, VoiceConnectionService.class);
            declineIntent.setAction(ACTION_END_CALL);
            Bundle declineExtras = new Bundle();
            HashMap<String, String> declineMap = new HashMap<>();
            declineMap.put(EXTRA_CALL_UUID, callUUID);
            declineMap.put(EXTRA_CALL_NUMBER, callerNumber);
            declineMap.put(EXTRA_CALLER_NAME, resolvedContactName);
            declineExtras.putSerializable("attributeMap", declineMap);
            declineIntent.putExtras(declineExtras);
            PendingIntent declinePendingIntent = PendingIntent.getService(this, 2, declineIntent, flag);
            
            notificationBuilder.setDeleteIntent(declinePendingIntent);

            if (customView != null) {
                String packageName = getPackageName();
                customView.setOnClickPendingIntent(getResources().getIdentifier("llAccept", "id", packageName), answerPendingIntent);
                customView.setOnClickPendingIntent(getResources().getIdentifier("llDecline", "id", packageName), declinePendingIntent);
                
                customSmallView.setOnClickPendingIntent(getResources().getIdentifier("llAccept", "id", packageName), answerPendingIntent);
                customSmallView.setOnClickPendingIntent(getResources().getIdentifier("llDecline", "id", packageName), declinePendingIntent);
                
                final String avatarUrl = payload != null ? payload.getString("avatarUrl") : null;
                if (avatarUrl != null && !avatarUrl.isEmpty() && contactPhoto == null) {
                    final android.app.NotificationManager notificationManager = (android.app.NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                    final android.widget.RemoteViews finalCustomView = customView;
                    final android.widget.RemoteViews finalCustomSmallView = customSmallView;
                    final NotificationCompat.Builder finalBuilder = notificationBuilder;
                    new Thread(() -> {
                        try {
                            java.net.URL url = new java.net.URL(avatarUrl);
                            java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
                            connection.setDoInput(true);
                            connection.connect();
                            java.io.InputStream input = connection.getInputStream();
                            android.graphics.Bitmap myBitmap = android.graphics.BitmapFactory.decodeStream(input);
                            if (myBitmap != null) {
                                finalCustomView.setImageViewBitmap(getResources().getIdentifier("ivAvatar", "id", packageName), myBitmap);
                                finalCustomView.setViewVisibility(getResources().getIdentifier("ivAvatar", "id", packageName), android.view.View.VISIBLE);
                                finalCustomSmallView.setImageViewBitmap(getResources().getIdentifier("ivAvatar", "id", packageName), myBitmap);
                                finalCustomSmallView.setViewVisibility(getResources().getIdentifier("ivAvatar", "id", packageName), android.view.View.VISIBLE);
                                
                                Notification updatedNotification = finalBuilder.build();
                                notificationManager.notify(NOTIFICATION_ID, updatedNotification);
                            }
                        } catch (Exception e) {
                            Log.w(TAG, "[VoiceConnectionService] Failed to load avatar for notification", e);
                        }
                    }).start();
                }
            } else {
                notificationBuilder.addAction(0, "Decline", declinePendingIntent);
                notificationBuilder.addAction(0, "Accept", answerPendingIntent);
            }

            Log.d(TAG, "[VoiceConnectionService] notification buttons added using flutter_callkit exact logic");

            // The OS will automatically launch the full screen intent if the screen is locked
            // or show the heads-up notification if the screen is unlocked.
        } else {
            Activity currentActivity = RNCallKeepModule.instance != null ? RNCallKeepModule.instance.getCurrentReactActivity() : null;
            if (currentActivity != null) {
                Intent notificationIntent = new Intent(this, currentActivity.getClass());
                notificationIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
                PendingIntent pendingIntent = PendingIntent.getActivity(this, NOTIFICATION_ID, notificationIntent, flag);
                notificationBuilder.setContentIntent(pendingIntent);
            }
        }

        if (foregroundSettings.hasKey("notificationIcon")) {
            Context context = this.getApplicationContext();
            Resources res = context.getResources();
            String smallIcon = foregroundSettings.getString("notificationIcon");
            notificationBuilder.setSmallIcon(res.getIdentifier(smallIcon, "mipmap", context.getPackageName()));
        }

        Log.d(TAG, "[VoiceConnectionService] Starting foreground service");

        Notification notification = notificationBuilder.build();
        if (isSelfManaged && isIncomingCall) {
            notification.flags |= Notification.FLAG_INSISTENT;
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
            if (isSelfManaged && isIncomingCall) {
                manager.notify(NOTIFICATION_ID, notification);
            }
        } catch (Exception e) {
            Log.w(TAG, "[VoiceConnectionService] Can't start foreground service : " + e.toString());
            if (isSelfManaged && isIncomingCall && manager != null) {
                manager.notify(NOTIFICATION_ID, notification);
            }
        }
    }

    public void updateForegroundServiceToOngoing(String uuid, Connection conn) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        ReadableMap foregroundSettings = getForegroundSettings(null);
        if (foregroundSettings == null || !foregroundSettings.hasKey("channelId")) return;

        String NOTIFICATION_CHANNEL_ID = foregroundSettings.getString("channelId");
        String channelName = foregroundSettings.getString("channelName");
        int importance = NotificationManager.IMPORTANCE_HIGH;

        NotificationChannel chan = new NotificationChannel(NOTIFICATION_CHANNEL_ID, channelName, importance);
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.createNotificationChannel(chan);

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID);
        notificationBuilder.setOngoing(true);
        notificationBuilder.setAutoCancel(false);

        Bundle extras = conn.getExtras();
        String callerName = extras != null ? extras.getString(EXTRA_CALLER_NAME) : foregroundSettings.getString("notificationTitle");
        String callerNumber = extras != null ? extras.getString(EXTRA_CALL_NUMBER) : "";

        String packageName = getPackageName();
        int layoutId = getResources().getIdentifier("layout_custom_ongoing_notification", "layout", packageName);
        int smallLayoutId = getResources().getIdentifier("layout_custom_small_ongoing_notification", "layout", packageName);

        final int flag = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE : PendingIntent.FLAG_UPDATE_CURRENT;

        Intent declineIntent = new Intent(this, VoiceConnectionService.class);
        declineIntent.setAction(ACTION_END_CALL);
        Bundle declineExtras = new Bundle();
        HashMap<String, String> declineMap = new HashMap<>();
        declineMap.put(EXTRA_CALL_UUID, uuid);
        declineExtras.putSerializable("attributeMap", declineMap);
        declineIntent.putExtras(declineExtras);
        PendingIntent declinePendingIntent = PendingIntent.getService(this, 2, declineIntent, flag);

        if (layoutId != 0 && smallLayoutId != 0) {
            android.widget.RemoteViews customView = new android.widget.RemoteViews(packageName, layoutId);
            android.widget.RemoteViews customSmallView = new android.widget.RemoteViews(packageName, smallLayoutId);

            customView.setTextViewText(getResources().getIdentifier("tvNameCaller", "id", packageName), callerName != null ? callerName : "Ongoing call");
            customSmallView.setTextViewText(getResources().getIdentifier("tvNameCaller", "id", packageName), callerName != null ? callerName : "Ongoing call");
            
            if (callerNumber != null && !callerNumber.isEmpty()) {
                customView.setTextViewText(getResources().getIdentifier("tvNumber", "id", packageName), callerNumber);
                customSmallView.setTextViewText(getResources().getIdentifier("tvNumber", "id", packageName), callerNumber);
            }

            customView.setOnClickPendingIntent(getResources().getIdentifier("llHangup", "id", packageName), declinePendingIntent);
            int tvHangupId = getResources().getIdentifier("tvHangUp", "id", packageName);
            if (tvHangupId != 0) {
                customView.setTextViewText(tvHangupId, "End Call");
            }

            // Avatar loading
            final String avatarUrl = extras != null && extras.containsKey(EXTRA_PAYLOAD) && extras.getBundle(EXTRA_PAYLOAD) != null 
                    ? extras.getBundle(EXTRA_PAYLOAD).getString("avatarUrl") : null;
            
            if (avatarUrl != null && !avatarUrl.isEmpty()) {
                final android.app.NotificationManager notificationManager = (android.app.NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                final android.widget.RemoteViews finalCustomView = customView;
                final android.widget.RemoteViews finalCustomSmallView = customSmallView;
                final NotificationCompat.Builder finalBuilder = notificationBuilder;
                new Thread(() -> {
                    try {
                        java.net.URL url = new java.net.URL(avatarUrl);
                        java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
                        connection.setDoInput(true);
                        connection.connect();
                        java.io.InputStream input = connection.getInputStream();
                        android.graphics.Bitmap myBitmap = android.graphics.BitmapFactory.decodeStream(input);
                        if (myBitmap != null) {
                            finalCustomView.setImageViewBitmap(getResources().getIdentifier("ivAvatar", "id", packageName), myBitmap);
                            finalCustomView.setViewVisibility(getResources().getIdentifier("ivAvatar", "id", packageName), android.view.View.VISIBLE);
                            finalCustomSmallView.setImageViewBitmap(getResources().getIdentifier("ivAvatar", "id", packageName), myBitmap);
                            finalCustomSmallView.setViewVisibility(getResources().getIdentifier("ivAvatar", "id", packageName), android.view.View.VISIBLE);
                            
                            Notification updatedNotification = finalBuilder.build();
                            notificationManager.notify(NOTIFICATION_ID, updatedNotification);
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "[VoiceConnectionService] Failed to load avatar for ongoing notification", e);
                    }
                }).start();
            }

            notificationBuilder.setStyle(new NotificationCompat.DecoratedCustomViewStyle());
            notificationBuilder.setCustomContentView(customSmallView);
            notificationBuilder.setCustomBigContentView(customView);
        } else {
            notificationBuilder.setContentTitle(callerName != null && !callerName.isEmpty() ? callerName : foregroundSettings.getString("notificationTitle"))
                .setContentText("Ongoing call")
                .addAction(0, "End Call", declinePendingIntent);
        }

        notificationBuilder.setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SERVICE);

        if (foregroundSettings.hasKey("notificationIcon")) {
            Context context = this.getApplicationContext();
            String smallIcon = foregroundSettings.getString("notificationIcon");
            int iconId = context.getResources().getIdentifier(smallIcon, "mipmap", context.getPackageName());
            if (iconId != 0) {
                notificationBuilder.setSmallIcon(iconId);
            }
        }

        Activity currentActivity = RNCallKeepModule.instance != null ? RNCallKeepModule.instance.getCurrentReactActivity() : null;
        if (currentActivity != null) {
            Intent notificationIntent = new Intent(this, currentActivity.getClass());
            notificationIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent pendingIntent = PendingIntent.getActivity(this, NOTIFICATION_ID, notificationIntent, flag);
            notificationBuilder.setContentIntent(pendingIntent);
        }

        Notification notification = notificationBuilder.build();
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE);
            } else {
                stopForeground(true);
            }
            startForeground(NOTIFICATION_ID, notification);
        } catch (Exception e) {
            if (manager != null) {
                manager.notify(NOTIFICATION_ID, notification);
            }
        }
    }

    public void stopForegroundService() {
        Log.d(TAG, "[VoiceConnectionService] stopForegroundService");
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE);
            } else {
                stopForeground(true);
            }
            android.app.NotificationManager notificationManager = (android.app.NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (notificationManager != null) {
                notificationManager.cancel(NOTIFICATION_ID);
            }
        } catch (Exception e) {
            Log.w(TAG, "[VoiceConnectionService] can't stop foreground service :" + e.toString());
        }
    }

    private boolean isForegroundServiceConfigured() {
        ReadableMap foregroundSettings = getForegroundSettings(null);
        try {
            return foregroundSettings != null && foregroundSettings.hasKey("channelId");
        } catch (Exception e) {
            // Fix ArrayIndexOutOfBoundsException thrown by ReadableNativeMap.hasKey
            Log.w(TAG, "[VoiceConnectionService] Not creating foregroundService due to configuration retrieval error" + e.toString());
            return false;
        }
    }

    private void wakeUpApplication(String uuid, String number, String displayName) {
         Log.d(TAG, "[VoiceConnectionService] wakeUpApplication, uuid:" + uuid + ", number :" + number + ", displayName:" + displayName);

        // Avoid to call wake up the app again in wakeUpAfterReachabilityTimeout.
        this.currentConnectionRequest = null;

        try {
            Intent headlessIntent = new Intent(
                this.getApplicationContext(),
                RNCallKeepBackgroundMessagingService.class
            );
            headlessIntent.putExtra("callUUID", uuid);
            headlessIntent.putExtra("name", displayName);
            headlessIntent.putExtra("handle", number);

            ComponentName name = this.getApplicationContext().startService(headlessIntent);
            if (name != null) {
              Log.d(TAG, "[VoiceConnectionService] wakeUpApplication, acquiring lock for application:" + name);
              HeadlessJsTaskService.acquireWakeLockNow(this.getApplicationContext());
            }
        } catch (Exception e) {
          Log.w(TAG, "[VoiceConnectionService] wakeUpApplication, error" + e.toString());
        }
    }

    private void wakeUpAfterReachabilityTimeout(ConnectionRequest request) {
        if (this.currentConnectionRequest == null) {
            return;
        }
        Bundle extras = request.getExtras();
        String number = request.getAddress().getSchemeSpecificPart();
        String displayName = extras.getString(EXTRA_CALLER_NAME);
        Log.d(TAG, "[VoiceConnectionService] checkReachability timeout, force wakeup, number :" + number + ", displayName: " + displayName);

        wakeUpApplication(this.notReachableCallUuid, number, displayName);

        VoiceConnectionService.currentConnectionRequest = null;
    }

    private void checkReachability() {
        Log.d(TAG, "[VoiceConnectionService] checkReachability");

        final VoiceConnectionService instance = this;
        sendCallRequestToActivity(ACTION_CHECK_REACHABILITY, null, true);

        new android.os.Handler().postDelayed(
            new Runnable() {
                public void run() {
                    instance.wakeUpAfterReachabilityTimeout(instance.currentConnectionRequest);
                }
            }, 2000);
    }

    private Boolean canMakeOutgoingCall() {
        return isAvailable;
    }

    private Connection createConnection(ConnectionRequest request) {
        Bundle extras = request.getExtras();
        if (request.getAddress() == null) {
            return null;
        }
        HashMap<String, String> extrasMap = this.bundleToMap(extras);

        String callerNumber = request.getAddress().toString();
        Log.d(TAG, "[VoiceConnectionService] createConnection, callerNumber:" + callerNumber);

        if (callerNumber.contains(":")) {
            //CallerNumber contains a schema which we'll separate out
            int schemaIndex = callerNumber.indexOf(":");
            String number = callerNumber.substring(schemaIndex + 1);
            String schema = callerNumber.substring(0, schemaIndex);

            extrasMap.put(EXTRA_CALL_NUMBER, number);
            extrasMap.put(EXTRA_CALL_NUMBER_SCHEMA, schema);
        } else {
            extrasMap.put(EXTRA_CALL_NUMBER, callerNumber);
        }

        VoiceConnection connection = new VoiceConnection(this, extrasMap);
        connection.setConnectionCapabilities(Connection.CAPABILITY_MUTE | Connection.CAPABILITY_SUPPORT_HOLD);

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Context context = getApplicationContext();
            TelecomManager telecomManager = (TelecomManager) context.getSystemService(context.TELECOM_SERVICE);
            try {
                PhoneAccount phoneAccount = telecomManager.getPhoneAccount(request.getAccountHandle());
                if(phoneAccount != null && (phoneAccount.getCapabilities() & PhoneAccount.CAPABILITY_SELF_MANAGED) == PhoneAccount.CAPABILITY_SELF_MANAGED) {
                    Log.d(TAG, "[VoiceConnectionService] PhoneAccount is SELF_MANAGED, so connection will be too");
                    connection.setConnectionProperties(Connection.PROPERTY_SELF_MANAGED);
                } else {
                    Log.d(TAG, "[VoiceConnectionService] PhoneAccount is not SELF_MANAGED, so connection won't be either");
                }
            } catch (SecurityException e) {
                Log.w(TAG, "[VoiceConnectionService] SecurityException when getting phone account. Assuming SELF_MANAGED.", e);
                connection.setConnectionProperties(Connection.PROPERTY_SELF_MANAGED);
            }
        }

        connection.setInitializing();
        connection.setExtras(extras);
        currentConnections.put(extras.getString(EXTRA_CALL_UUID), connection);

        // Get other connections for conferencing
        Map<String, VoiceConnection> otherConnections = new HashMap<>();
        for (Map.Entry<String, VoiceConnection> entry : currentConnections.entrySet()) {
            if(!(extras.getString(EXTRA_CALL_UUID).equals(entry.getKey()))) {
                otherConnections.put(entry.getKey(), entry.getValue());
            }
        }
        List<Connection> conferenceConnections = new ArrayList<Connection>(otherConnections.values());
        connection.setConferenceableConnections(conferenceConnections);

        return connection;
    }

    @Override
    public void onConference(Connection connection1, Connection connection2) {
        Log.d(TAG, "[VoiceConnectionService] onConference");
        super.onConference(connection1, connection2);
        VoiceConnection voiceConnection1 = (VoiceConnection) connection1;
        VoiceConnection voiceConnection2 = (VoiceConnection) connection2;

        VoiceConference voiceConference = new VoiceConference(phoneAccountHandle);
        voiceConference.addConnection(voiceConnection1);
        voiceConference.addConnection(voiceConnection2);

        connection1.onUnhold();
        connection2.onUnhold();

        this.addConference(voiceConference);
    }

    public void onCreateIncomingConnectionFailed(PhoneAccountHandle connectionManagerPhoneAccount, ConnectionRequest request) {
        super.onCreateIncomingConnectionFailed(connectionManagerPhoneAccount, request);
        Log.w(TAG, "[VoiceConnectionService] onCreateIncomingConnectionFailed: " + request);

        Bundle extras = request.getExtras();
        HashMap<String, String> extrasMap = this.bundleToMap(extras);

        String callerNumber = request.getAddress().toString();
        if (callerNumber.contains(":")) {
            //CallerNumber contains a schema which we'll separate out
            int schemaIndex = callerNumber.indexOf(":");
            String number = callerNumber.substring(schemaIndex + 1);
            String schema = callerNumber.substring(0, schemaIndex);

            extrasMap.put(EXTRA_CALL_NUMBER, number);
            extrasMap.put(EXTRA_CALL_NUMBER_SCHEMA, schema);
        } else {
            extrasMap.put(EXTRA_CALL_NUMBER, callerNumber);
        }

        sendCallRequestToActivity(ACTION_ON_CREATE_CONNECTION_FAILED, extrasMap, true);
    }

    // When a listener is available for `sendCallRequestToActivity`, send delayed events.
    public static void startObserving() {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
            // Run this in a Looper to avoid : java.lang.RuntimeException: Can't create handler inside thread Thread
                int count = delayedEvents.size();
                Log.d(TAG, "[VoiceConnectionService] startObserving, event count: " + count);

                for (Bundle event : delayedEvents) {
                    String action = event.getString("action");
                    HashMap attributeMap = (HashMap) event.getSerializable("attributeMap");

                    currentConnectionService.sendCallRequestToActivity(action, attributeMap, false);
                }

                delayedEvents = new ArrayList<Bundle>();
            }
        });
    }

    /*
     * Send call request to the RNCallKeepModule
     */
    private void sendCallRequestToActivity(final String action, @Nullable final HashMap attributeMap, final boolean retry) {
        final VoiceConnectionService instance = this;
        final Handler handler = new Handler();

        Log.d(TAG, "[VoiceConnectionService] sendCallRequestToActivity, action:" + action);

        handler.post(new Runnable() {
            @Override
            public void run() {
                Intent intent = new Intent(action);
                Bundle extras = new Bundle();
                extras.putString("action", action);

                if (attributeMap != null) {
                    extras.putSerializable("attributeMap", attributeMap);
                    intent.putExtras(extras);
                }

                boolean result = LocalBroadcastManager.getInstance(instance).sendBroadcast(intent);
                if (!result && retry) {
                    // Event will be sent later when a listener will be available.
                    delayedEvents.add(extras);
                }
            }
        });
    }

    private HashMap<String, String> bundleToMap(Bundle extras) {
        HashMap<String, String> extrasMap = new HashMap<>();
        Set<String> keySet = extras.keySet();
        Iterator<String> iterator = keySet.iterator();

        while(iterator.hasNext()) {
            String key = iterator.next();
            if (extras.get(key) != null) {
                extrasMap.put(key, extras.get(key).toString());
            }
        }
        return extrasMap;
    }

    /**
     * https://stackoverflow.com/questions/5446565/android-how-do-i-check-if-activity-is-running
     *
     * @param context Context
     * @return boolean
     */
    public static boolean isRunning(Context context) {
        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        List<RunningTaskInfo> tasks = activityManager.getRunningTasks(Integer.MAX_VALUE);

        for (RunningTaskInfo task : tasks) {
            if (context.getPackageName().equalsIgnoreCase(task.baseActivity.getPackageName())) {
                return true;
            }
        }

        Log.d(TAG, "[VoiceConnectionService] isRunning: no running package found.");

        return false;
    }

    private void checkForAppReachability(final String callUUID, final Integer timeout) {
        final VoiceConnectionService instance = this;

        new android.os.Handler().postDelayed(new Runnable() {
            public void run() {
                if (instance.isReachable) {
                    return;
                }
                Connection conn = VoiceConnectionService.getConnection(callUUID);
                Log.w(TAG, "[VoiceConnectionService] checkForAppReachability timeout after " + timeout + " ms, isReachable:" + instance.isReachable + ", uuid: " + callUUID);

                if (conn == null) {
                    Log.w(TAG, "[VoiceConnectionService] checkForAppReachability timeout, no connection to close with uuid: " + callUUID);

                    return;
                }
                conn.onDisconnect();
            }
        }, timeout);
    }
}
