package io.wazo.callkeep;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.util.Log;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import static io.wazo.callkeep.Constants.ACTION_ANSWER_CALL;
import static io.wazo.callkeep.Constants.ACTION_END_CALL;
import static io.wazo.callkeep.Constants.EXTRA_CALLER_NAME;
import static io.wazo.callkeep.Constants.EXTRA_CALL_NUMBER;
import static io.wazo.callkeep.Constants.EXTRA_CALL_UUID;
import static io.wazo.callkeep.Constants.EXTRA_PAYLOAD;

import android.widget.FrameLayout;
import java.util.HashMap;

public class IncomingCallActivity extends Activity {
    private static final String TAG = "IncomingCallActivity";
    private String callUUID;
    private String callerName;
    private String callerNumber;
    private BroadcastReceiver receiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
            KeyguardManager keyguardManager = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
            if (keyguardManager != null) {
                keyguardManager.requestDismissKeyguard(this, null);
            }
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }

        // WakeLock to activate screen and wake CPU
        try {
            android.os.PowerManager pm = (android.os.PowerManager) getApplicationContext().getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                android.os.PowerManager.WakeLock wakeLock = pm.newWakeLock(
                    android.os.PowerManager.SCREEN_BRIGHT_WAKE_LOCK | 
                    android.os.PowerManager.FULL_WAKE_LOCK | 
                    android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP,
                    "CallKeep:PowerManager"
                );
                wakeLock.acquire(15000); // 15 seconds
            }
        } catch (Exception e) {
            Log.e(TAG, "[IncomingCallActivity] Failed to acquire WakeLock", e);
        }

        callUUID = getIntent().getStringExtra(EXTRA_CALL_UUID);
        callerName = getIntent().getStringExtra(EXTRA_CALLER_NAME);
        callerNumber = getIntent().getStringExtra(EXTRA_CALL_NUMBER);

        // Payload Extras
        String bgColor = "#1E88E5"; // Default Blue
        String avatarUrl = null;
        
        Bundle payload = getIntent().getBundleExtra(EXTRA_PAYLOAD);
        Log.d(TAG, "[IncomingCallActivity] onCreate uuid: " + callUUID + " payload: " + (payload != null ? payload.toString() : "null"));

        if (payload != null) {
            // Read display properties from the outer level first (android-level props)
            if (payload.containsKey("backgroundColor")) {
                bgColor = payload.getString("backgroundColor");
            }
            if (payload.containsKey("avatarUrl")) {
                avatarUrl = payload.getString("avatarUrl");
            }

            // Also check inner payload for backward compatibility
            Bundle innerPayload = payload.getBundle("payload");
            if (innerPayload != null) {
                if (innerPayload.containsKey("backgroundColor") && !payload.containsKey("backgroundColor")) {
                    bgColor = innerPayload.getString("backgroundColor");
                }
                if (innerPayload.containsKey("avatarUrl") && !payload.containsKey("avatarUrl")) {
                    avatarUrl = innerPayload.getString("avatarUrl");
                }
            }
        }

        // --- MODERN PROGRAMMATIC UI ---
        android.widget.RelativeLayout rootLayout = new android.widget.RelativeLayout(this);
        rootLayout.setLayoutParams(new android.widget.RelativeLayout.LayoutParams(
                android.widget.RelativeLayout.LayoutParams.MATCH_PARENT,
                android.widget.RelativeLayout.LayoutParams.MATCH_PARENT));

        int backgroundColorInt;
        try {
            backgroundColorInt = Color.parseColor(bgColor);
        } catch (Exception e) {
            backgroundColorInt = Color.parseColor("#1E88E5");
        }
        rootLayout.setBackgroundColor(backgroundColorInt);

        // Top content (Avatar + Name)
        LinearLayout topContent = new LinearLayout(this);
        topContent.setOrientation(LinearLayout.VERTICAL);
        topContent.setGravity(Gravity.CENTER_HORIZONTAL);
        android.widget.RelativeLayout.LayoutParams topParams = new android.widget.RelativeLayout.LayoutParams(
                android.widget.RelativeLayout.LayoutParams.MATCH_PARENT,
                android.widget.RelativeLayout.LayoutParams.WRAP_CONTENT);
        topParams.addRule(android.widget.RelativeLayout.CENTER_HORIZONTAL);
        topParams.setMargins(0, 150, 0, 0); // Move it up from the top
        topContent.setLayoutParams(topParams);

        // Avatar Container
        android.widget.FrameLayout avatarContainer = new android.widget.FrameLayout(this);
        LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        avatarContainer.setLayoutParams(avatarParams);



        // Circle 1
        View circle1 = new View(this);
        android.graphics.drawable.GradientDrawable c1bg = new android.graphics.drawable.GradientDrawable();
        c1bg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        c1bg.setColor(Color.argb(30, 255, 255, 255));
        circle1.setBackground(c1bg);
        FrameLayout.LayoutParams c1p = new FrameLayout.LayoutParams(400, 400, Gravity.CENTER);
        avatarContainer.addView(circle1, c1p);

        // Circle 2
        View circle2 = new View(this);
        android.graphics.drawable.GradientDrawable c2bg = new android.graphics.drawable.GradientDrawable();
        c2bg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        c2bg.setColor(Color.argb(50, 255, 255, 255));
        circle2.setBackground(c2bg);
        FrameLayout.LayoutParams c2p = new FrameLayout.LayoutParams(300, 300, Gravity.CENTER);
        avatarContainer.addView(circle2, c2p);

        // Fallback TextView
        TextView avatarText = new TextView(this);
        avatarText.setText(callerName != null && callerName.length() > 0 ? callerName.substring(0, 1).toUpperCase() : "U");
        avatarText.setTextSize(60);
        avatarText.setTextColor(Color.WHITE);
        avatarText.setGravity(Gravity.CENTER);
        android.graphics.drawable.GradientDrawable avatarBg = new android.graphics.drawable.GradientDrawable();
        avatarBg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        avatarBg.setColor(Color.parseColor("#3B82F6"));
        avatarText.setBackground(avatarBg);
        FrameLayout.LayoutParams textParams = new FrameLayout.LayoutParams(200, 200, Gravity.CENTER);
        avatarContainer.addView(avatarText, textParams);

        // ImageView
        android.widget.ImageView avatarImage = new android.widget.ImageView(this);
        avatarImage.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            avatarImage.setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override
                public void getOutline(View view, android.graphics.Outline outline) {
                    outline.setOval(0, 0, view.getWidth(), view.getHeight());
                }
            });
            avatarImage.setClipToOutline(true);
        }
        avatarContainer.addView(avatarImage, textParams);

        if (avatarUrl != null && !avatarUrl.isEmpty()) {
            final String urlToLoad = avatarUrl;
            new Thread(() -> {
                try {
                    java.net.URL url = new java.net.URL(urlToLoad);
                    java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
                    connection.setDoInput(true);
                    connection.connect();
                    java.io.InputStream input = connection.getInputStream();
                    final android.graphics.Bitmap myBitmap = android.graphics.BitmapFactory.decodeStream(input);
                    if (myBitmap != null) {
                        runOnUiThread(() -> avatarImage.setImageBitmap(myBitmap));
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();
        }

        topContent.addView(avatarContainer);

        // Caller Name
        TextView nameText = new TextView(this);
        nameText.setText(callerName != null ? callerName : "Incoming Call");
        nameText.setTextSize(36);
        nameText.setTextColor(Color.WHITE);
        nameText.setGravity(Gravity.CENTER);
        nameText.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        nameParams.setMargins(0, 40, 0, 0);
        nameText.setLayoutParams(nameParams);
        topContent.addView(nameText);

        // Caller Number
        TextView numberText = new TextView(this);
        numberText.setText(callerNumber != null ? callerNumber : "Unknown Number");
        numberText.setTextSize(20);
        numberText.setTextColor(Color.parseColor("#E2E8F0")); // Lighter Gray
        numberText.setGravity(Gravity.CENTER);
        numberText.setPadding(0, 20, 0, 0);
        topContent.addView(numberText);

        rootLayout.addView(topContent);

        // Bottom Action Buttons (Accept & Decline)
        LinearLayout buttonsLayout = new LinearLayout(this);
        buttonsLayout.setOrientation(LinearLayout.HORIZONTAL);
        buttonsLayout.setGravity(Gravity.CENTER);
        android.widget.RelativeLayout.LayoutParams btnContainerParams = new android.widget.RelativeLayout.LayoutParams(
                android.widget.RelativeLayout.LayoutParams.MATCH_PARENT,
                android.widget.RelativeLayout.LayoutParams.WRAP_CONTENT);
        btnContainerParams.addRule(android.widget.RelativeLayout.ALIGN_PARENT_BOTTOM);
        btnContainerParams.setMargins(0, 0, 0, 150);
        buttonsLayout.setLayoutParams(btnContainerParams);

        // Decline Button
        FrameLayout declineContainer = new FrameLayout(this);
        android.graphics.drawable.GradientDrawable declineBg = new android.graphics.drawable.GradientDrawable();
        declineBg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        declineBg.setColor(Color.parseColor("#EF4444")); // Red
        declineContainer.setBackground(declineBg);
        declineContainer.setOnClickListener(v -> declineCall());

        TextView declineIcon = new TextView(this);
        declineIcon.setText("✕");
        declineIcon.setTextColor(Color.WHITE);
        declineIcon.setTextSize(32);
        declineIcon.setGravity(Gravity.CENTER);
        declineContainer.addView(declineIcon, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // Answer Button
        FrameLayout answerContainer = new FrameLayout(this);
        android.graphics.drawable.GradientDrawable answerBg = new android.graphics.drawable.GradientDrawable();
        answerBg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        answerBg.setColor(Color.parseColor("#10B981")); // Green
        answerContainer.setBackground(answerBg);
        answerContainer.setOnClickListener(v -> answerCall());

        TextView answerIcon = new TextView(this);
        answerIcon.setText("✓");
        answerIcon.setTextColor(Color.WHITE);
        answerIcon.setTextSize(36);
        answerIcon.setGravity(Gravity.CENTER);
        answerContainer.addView(answerIcon, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(160, 160);
        btnParams.setMargins(80, 0, 80, 0);

        buttonsLayout.addView(declineContainer, btnParams);
        buttonsLayout.addView(answerContainer, btnParams);

        rootLayout.addView(buttonsLayout);

        setContentView(rootLayout);

        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (ACTION_END_CALL.equals(action) || ACTION_ANSWER_CALL.equals(action)) {
                    finish();
                }
            }
        };
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "[IncomingCallActivity] onStart called");
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_END_CALL);
        filter.addAction(ACTION_ANSWER_CALL);
        LocalBroadcastManager.getInstance(this).registerReceiver(receiver, filter);
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "[IncomingCallActivity] onStop called");
    }

    private void answerCall() {
        Intent intent = new Intent(ACTION_ANSWER_CALL);
        HashMap<String, String> attributeMap = new HashMap<>();
        attributeMap.put(EXTRA_CALL_UUID, callUUID);
        attributeMap.put(EXTRA_CALL_NUMBER, callerNumber);
        attributeMap.put(EXTRA_CALLER_NAME, callerName);
        
        Bundle extras = new Bundle();
        extras.putSerializable("attributeMap", attributeMap);

        // Forward the payload back to React Native
        Bundle originalExtras = getIntent().getExtras();
        if (originalExtras != null && originalExtras.containsKey(EXTRA_PAYLOAD)) {
            extras.putBundle(EXTRA_PAYLOAD, originalExtras.getBundle(EXTRA_PAYLOAD));
        }

        intent.putExtras(extras);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        
        try {
            VoiceConnectionService.updateToOngoing(this, callUUID);
        } catch (Exception e) {}
        
        // Try to open the app
        Intent launchIntent = getPackageManager().getLaunchIntentForPackage(getPackageName());
        if (launchIntent != null) {
            startActivity(launchIntent);
        }
        finish();
    }

    private void declineCall() {
        Intent intent = new Intent(ACTION_END_CALL);
        HashMap<String, String> attributeMap = new HashMap<>();
        attributeMap.put(EXTRA_CALL_UUID, callUUID);
        attributeMap.put(EXTRA_CALL_NUMBER, callerNumber);
        attributeMap.put(EXTRA_CALLER_NAME, callerName);
        Bundle extras = new Bundle();
        extras.putSerializable("attributeMap", attributeMap);
        intent.putExtras(extras);
        try {
            android.telecom.Connection conn = VoiceConnectionService.getConnection(callUUID);
            if (conn != null) {
                conn.onDisconnect();
            } else {
                VoiceConnectionService.deinitConnection(callUUID);
            }
        } catch (Exception e) {}
        
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (receiver != null) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver);
        }
    }
}
