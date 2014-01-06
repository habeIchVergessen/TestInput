package org.habeIchVergessen.testinput;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Picture;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.text.Html;
import android.text.SpannableString;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AnimationUtils;
import android.widget.GridLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.ViewFlipper;

import java.util.HashMap;


public class MainActivity extends Activity {

    protected final static String LOG_TAG = "TestInput";

    protected RelativeLayout mOptionWidget;
    protected RelativeLayout mOptionWidgetGrid;
    protected GridLayout mPhoneWidget;
    protected RelativeLayout mMenuWidget;
    protected TextView mCallerName;
    protected TextView mCallerNumber;
    protected View mAcceptButton;
    protected View mAcceptSlide;
    protected View mRejectButton;
    protected View mRejectSlide;
    protected TextView mtfTest;
    protected View mbTest;

    protected OptionOverlay mOptionOverlay = null;

    //this action will let us toggle the flashlight
    public static final String TOGGLE_FLASHLIGHT = "net.cactii.flash2.TOGGLE_FLASHLIGHT";
    boolean torchIsOn = false;

    private ImageView torchButton = null;

    protected boolean mWiredHeadSetPlugged = false;

    private boolean mViewNeedsReset = false;
    // drawing stuff
    private final static int mButtonMargin = 15; // designer use just 10dp; ode rendering issue
    private final static int mRedrawOffset = 10; // move min 10dp before redraw

    private int mActivePointerId = -1;

    // camera
    private CameraHelper cameraHelper = null;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            // headset (un-)plugged
            if (action.equals(Intent.ACTION_HEADSET_PLUG)) {
                mWiredHeadSetPlugged = ((intent.getIntExtra("state", -1) == 1));
                Log.d("TestInput", "ACTION_HEADSET_PLUG: plugged " + mWiredHeadSetPlugged + " (" + intent.getIntExtra("state", -1) + ")");
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i("TestInput", "onCreate enter");
        super.onCreate(savedInstanceState);

        //Remove title bar
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);

        //Remove notification bar
        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL);

        setContentView(R.layout.activity_main);

        mPhoneWidget = (GridLayout)findViewById(R.id.phone_widget);
        mCallerName = (TextView)findViewById(R.id.caller_name);
        mCallerNumber = (TextView)findViewById(R.id.caller_number);
        mAcceptButton = findViewById(R.id.call_accept_button);
        mAcceptSlide = findViewById(R.id.call_accept_slide);
        mRejectButton = findViewById(R.id.call_reject_button);
        mRejectSlide = findViewById(R.id.call_reject_slide);
        mtfTest = (TextView)findViewById(R.id.tfTest);
        mbTest = findViewById(R.id.bTest);

        mbTest.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
            resetPhoneWidgetMakeVisible();
            setIncomingNumber(mtfTest.getText().toString());
            }
        });

        //add screen on and alarm fired intent receiver
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_HEADSET_PLUG);
        registerReceiver(receiver, filter);

        torchButton = (ImageView) findViewById(R.id.option_04);

        mOptionOverlay = (OptionOverlay)findViewById(R.id.option_overlay_menu);
        mOptionOverlay.registerOnMenuActionListener(new OptionOverlay.OnMenuActionListener() {
            @Override
            public void onMenuAction(OptionOverlay.MenuOption menuOption) {
                Log.d(LOG_TAG, "onMenuAction: " + getResources().getResourceName(menuOption.getOptionId()));
                View view;
                switch (menuOption.getOptionId()) {
                    case R.id.menu_torch:
                        sendToggleTorch(menuOption);
                        break;
                    case R.id.menu_camera:
                        sendFireCamera(menuOption);
                        break;
                    case R.id.menu_camera_capture:
                        view = findViewById(R.id.camera_capture);
                        view.callOnClick();
                        break;
                    case R.id.menu_camera_close:
                        view = findViewById(R.id.camera_close);
                        view.callOnClick();
                        break;
                    case R.id.menu_demo:
                        break;
                }
            }

            @Override
            public void onMenuOpen() {
                if (findViewById(R.id.camera_widget) != null)
                    mOptionOverlay.setupMenuForView(R.id.camera_widget);
                else
                    mOptionOverlay.setupMenuForView(R.id.phone_widget);
            }
        });

        // register menu
        // phone widget
        mOptionOverlay.registerMenuOption(R.id.phone_widget, R.id.menu_torch, R.drawable.ic_appwidget_torch_off);
        mOptionOverlay.registerMenuOption(R.id.phone_widget, R.id.menu_camera, R.drawable.ic_camera);
        // camera widget
        mOptionOverlay.registerMenuOption(R.id.camera_widget, R.id.menu_camera_capture, R.drawable.ic_notification);
        mOptionOverlay.registerMenuOption(R.id.camera_widget, R.id.menu_camera_close, R.drawable.ic_menu_trash_holo_light);

        Log.i("TestInput", "onCreate leave");
    }

    @Override
    public void onPause() {
        super.onPause();
        // release the camera immediately on pause event
        if (cameraHelper != null)
            cameraHelper.releaseCamera();
    }

    @Override
    public void onDestroy()
    {
        unregisterReceiver(receiver);

        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onTouchEvent(MotionEvent motionEvent)
    {
        boolean result = true;

        //if (!TouchEventProcessor.isTracking())
            mOptionOverlay.onTouchEvent_OptionWidget(motionEvent);

        if (!mOptionOverlay.isOpen())
            onTouchEvent_PhoneWidget(motionEvent);

        return result;
    }

    private void testRestartDefaultActivity() {
        Intent sendTextToSpeech = new Intent();
        sendTextToSpeech.setAction("org.durka.hallmonitor.restartDefaultActivity");
        sendBroadcast(sendTextToSpeech);
    }

    public boolean isSpeakerOn() {
        AudioManager audioManager = (AudioManager)getSystemService(AUDIO_SERVICE);
        return audioManager.isSpeakerphoneOn();
    }

    public void setSpeakerOn(boolean state) {
        AudioManager audioManager = (AudioManager)getSystemService(AUDIO_SERVICE);

        if (audioManager.isSpeakerphoneOn() != state)
            audioManager.setSpeakerphoneOn(state);
    }

    public boolean isAirPlaneMode() {
        return Settings.Global.getInt(getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
    }

    // camera
//    public void sendFireCamera(View view) {
    public void sendFireCamera(OptionOverlay.MenuOption menuOption) {
        if (cameraHelper == null && (cameraHelper = new CameraHelper(this)) != null) {
            cameraHelper.startPreview();
            //reset the lock timer to 30 seconds
            //Functions.Actions.setLockTimer(getApplicationContext(), 30000);
        }
    }

    //toggle the torch
//    public void sendToggleTorch(View view) {
    public void sendToggleTorch(OptionOverlay.MenuOption menuOption) {
        Intent intent = new Intent(TOGGLE_FLASHLIGHT);
        intent.putExtra("strobe", false);
        intent.putExtra("period", 100);
        intent.putExtra("bright", false);
        sendBroadcast(intent);
        torchIsOn = !torchIsOn;
        menuOption.setImageId((torchIsOn ? R.drawable.ic_appwidget_torch_on : R.drawable.ic_appwidget_torch_off));
    }

    /**
     * phone widget
     */
    private boolean onTouchEvent_PhoneWidget(MotionEvent motionEvent) {
        float maxSwipe = 150;
        float swipeTolerance = 0.95f;
        int defaultOffset = 10;

        // point handling
        MotionEvent.PointerCoords pointerCoords = new MotionEvent.PointerCoords();
        final int actionIndex = motionEvent.getActionIndex();
        final int actionMasked = motionEvent.getActionMasked();
        final int pointerId = actionIndex;
        int pointerIndex = -1;
        motionEvent.getPointerCoords(actionIndex, pointerCoords);

        switch (actionMasked) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                if (!TouchEventProcessor.isTracking()) {
                    // check accept button
                    if (mAcceptButton.getVisibility() == View.VISIBLE && TouchEventProcessor.pointerInRect(pointerCoords, mAcceptButton) && !TouchEventProcessor.isTracking())
                        TouchEventProcessor.startTracking(mAcceptButton);

                    // check reject button
                    if (mRejectButton.getVisibility() == View.VISIBLE && TouchEventProcessor.pointerInRect(pointerCoords, mRejectButton) && !TouchEventProcessor.isTracking())
                        TouchEventProcessor.startTracking(mRejectButton);

                    if (TouchEventProcessor.isTracking()) {
                        mActivePointerId = pointerId;
                        Log.d("TestInput", "DOWN|POINTER_DOWN: " + mActivePointerId);
                    }
                }
                break;
            case MotionEvent.ACTION_MOVE:
                for (int idx=0; idx < motionEvent.getPointerCount(); idx++)
                    if (motionEvent.getPointerId(idx) == mActivePointerId) {
                        pointerIndex = idx;
                        break;
                    }

                // process tracking
                if (TouchEventProcessor.isTracking() && pointerIndex != -1) {
                    motionEvent.getPointerCoords(pointerIndex, pointerCoords);

                    float dist = TouchEventProcessor.getHorizontalDistance(pointerCoords.x);

                    // check accept
                    if (TouchEventProcessor.isTrackedObj(mAcceptButton) && dist >= maxSwipe * swipeTolerance) {
                        callAcceptedPhoneWidget();
                        TouchEventProcessor.stopTracking();
                        mActivePointerId = -1;
                    } else
                        // animate accept
                        if (TouchEventProcessor.isTrackedObj(mAcceptButton) && dist > 0 && dist < maxSwipe)
                            moveCallButton(mAcceptButton, defaultOffset + Math.round(dist));

                    // modify negative dist
                    dist = Math.abs(dist);
                    // check rejected
                    if (TouchEventProcessor.isTrackedObj(mRejectButton) && dist >= maxSwipe * swipeTolerance) {
                        callRejectedPhoneWidget();
                        TouchEventProcessor.stopTracking();
                        mActivePointerId = -1;
                    } else
                        // animate rejected
                        if (TouchEventProcessor.isTrackedObj(mRejectButton) && dist > 0 && dist < maxSwipe)
                            moveCallButton(mRejectButton, defaultOffset + Math.round(dist));
                }
                break;
            case MotionEvent.ACTION_POINTER_UP:
                if (mActivePointerId == -1 || (mActivePointerId != -1 && motionEvent.findPointerIndex(mActivePointerId) != actionIndex))
                    break;
            case MotionEvent.ACTION_UP:
                if (TouchEventProcessor.isTracking()) {
                    resetPhoneWidget();
                    TouchEventProcessor.stopTracking();
                    mActivePointerId = -1;
                }
                break;
            case MotionEvent.ACTION_CANCEL:
                Log.d("TestInput", "CANCEL");
                callRejectedPhoneWidget();
                TouchEventProcessor.stopTracking();
                mActivePointerId = -1;
                break;
            default:
                Log.d("TestInput", "unhandled motionEvent: " + motionEvent.getAction() + ", index: " + actionIndex + ", masked: " + actionMasked);
                break;
        }

        return true;
    }

    private boolean setIncomingNumber(String incomingNumber) {
        Log.d("CallActivity", "incomingNumber: " + incomingNumber);

        boolean result = false;

        if (incomingNumber == null || incomingNumber.equals(""))
            return result;

        mCallerName.setText(incomingNumber);
        result = setDisplayNameByIncomingNumber(incomingNumber);

        return result;
    }

    private boolean setDisplayNameByIncomingNumber(String incomingNumber) {
        String name = null, type = null, phonetic = null;
        Cursor contactLookup = null;

        try {
            contactLookup = getContentResolver().query(
                    Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(incomingNumber))
                    ,	new String[]{ ContactsContract.PhoneLookup.DISPLAY_NAME, ContactsContract.PhoneLookup.TYPE, ContactsContract.PhoneLookup.LABEL }
                    ,	null
                    ,	null
                    , 	null);

            if (contactLookup != null && contactLookup.getCount() > 0) {

                contactLookup.moveToFirst();
                name = contactLookup.getString(contactLookup.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME));
                type = contactLookup.getString(contactLookup.getColumnIndex(ContactsContract.PhoneLookup.TYPE));
                phonetic = contactLookup.getString(contactLookup.getColumnIndex(ContactsContract.PhoneLookup.LABEL));
            }

            Log.d("TestInput", "got name " + name + " for number " + incomingNumber);
            if (name != null) {
                String typeString = (String)ContactsContract.CommonDataKinds.Phone.getTypeLabel(this.getResources(), Integer.parseInt(type), "");

                mCallerName.setText(name);
                mCallerNumber.setText((typeString == null ? incomingNumber : typeString));

                Log.d("TestInput", "displayName: " + name + " aka " + phonetic + " (" + type + " -> " + typeString + ")");
                testTextToSpeech(name + (typeString != null ? " " + typeString : ""));
                testRestartDefaultActivity();
            }
        } finally {
            if (contactLookup != null) {
                contactLookup.close();
            }
        }

        return (name != null);
    }

    public void callAcceptedPhoneWidget() {
        Log.d("TestInput", "callAcceptedPhoneWidget");
        mAcceptButton.setVisibility(View.INVISIBLE);
        mAcceptSlide.setVisibility(View.INVISIBLE);

        resetPhoneWidget();
    }

    public void callRejectedPhoneWidget() {
        Log.d("TestInput", "callRejectedPhoneWidget");
        resetPhoneWidgetMakeVisible();
    }

    public void moveCallButton(View button, int offset) {
        if (!mAcceptButton.equals(button) && !mRejectButton.equals(button))
            return;

        RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(button.getLayoutParams());

        if (button.equals(mAcceptButton)) {
            // don't draw yet
            if (offset > mButtonMargin && offset <= lp.leftMargin + mRedrawOffset)
                return;

            lp.setMargins(offset, 0, 0, 0);
            lp.addRule(RelativeLayout.ALIGN_PARENT_START);
        }

        if (button.equals(mRejectButton)) {
            // don't draw yet
            if (offset > mButtonMargin && offset <= lp.rightMargin + mRedrawOffset)
                return;

            lp.setMargins(0, 0, offset, 0);
            lp.addRule(RelativeLayout.ALIGN_PARENT_END);
        }

        lp.addRule(RelativeLayout.CENTER_VERTICAL);
        button.setLayoutParams(lp);

        mViewNeedsReset = true;
    }

    public void resetPhoneWidget() {
        if (!mViewNeedsReset)
            return;

        Log.d("TestInput", "resetPhoneWidget");
        moveCallButton(mAcceptButton, mButtonMargin);
        moveCallButton(mRejectButton, mButtonMargin);

        mViewNeedsReset = false;
    }

    public void resetPhoneWidgetMakeVisible() {
        Log.d("TestInput", "resetPhoneWidgetMakeVisible");
        resetPhoneWidget();
        mAcceptButton.setVisibility(View.VISIBLE);
        mAcceptSlide.setVisibility(View.VISIBLE);
        mRejectButton.setVisibility(View.VISIBLE);
        mRejectSlide.setVisibility(View.VISIBLE);

        mCallerName.setText("");
        mCallerNumber.setText("");
    }

    public void setRingMode(int ringMode) {
        AudioManager audioManager = (AudioManager)getSystemService(AUDIO_SERVICE);

        if (ringMode != audioManager.getRingerMode() && (ringMode == AudioManager.RINGER_MODE_NORMAL || ringMode == AudioManager.RINGER_MODE_VIBRATE || ringMode == AudioManager.RINGER_MODE_SILENT)) {
            audioManager.setRingerMode(ringMode);
        }
    }

    /**
     * phone widget (end)
     */

    /**
     * TextToSpeech
     */

    private void testTextToSpeech(String text) {
        Intent sendTextToSpeech = new Intent();
        sendTextToSpeech.setAction("org.durka.hallmonitor.sendTextToSpeech");
        sendTextToSpeech.putExtra("sendTextToSpeech", text);
        sendBroadcast(sendTextToSpeech);
    }

    /**
     * TextToSpeech (end)
     */

    public static class TouchEventProcessor
    {
        private static View mTrackObj = null;
        private static Point mTrackStartPoint;

        private final static int mHitRectBoost = 20;

        public static boolean pointerInRect(MotionEvent.PointerCoords pointer, View view) {
            return pointerInRect(pointer, view, mHitRectBoost);
        }

        public static boolean pointerInRect(MotionEvent.PointerCoords pointer, View view, int hitRectBoost) {
            Rect rect = new Rect();
            view.getGlobalVisibleRect(rect);
            // circle is tangent to edges
            double radius = rect.centerX() - rect.left;

            int extraSnap = (isTrackedObj(view) || !isTracking() ? hitRectBoost : 0);
            //return (pointer.x >= rect.left - extraSnap && pointer.x <= rect.right + extraSnap && pointer.y >= rect.top - extraSnap && pointer.y <= rect.bottom + extraSnap);
            return (Math.sqrt(Math.pow(rect.centerX() - pointer.x, 2) + Math.pow(rect.centerY() - pointer.y, 2)) <= radius + extraSnap);
        }

        public static boolean isTracking() {
            //Log.d("TestInput", "TouchEventProcessor.isTracking: " + (mTrackObj != null));
            return (mTrackObj != null);
        }

        public static boolean isTrackedObj(View view) {
            //Log.d("TestInput", "TouchEventProcessor.isTrackedObj: " + (isTracking() && mTrackObj.equals(view)));
            return (isTracking() && mTrackObj.equals(view));
        }

        public static void startTracking(View view) {
            //Log.d("TestInput", "TouchEventProcessor.startTracking: " + view.getId());
            mTrackObj = view;

            Rect mRect = new Rect();
            view.getGlobalVisibleRect(mRect);

            mTrackStartPoint = new Point(mRect.centerX(), mRect.centerY());
        }

        public static void stopTracking() {
            //Log.d("TestInput", "TouchEventProcessor.stopTracking");
            mTrackObj = null;
            mTrackStartPoint = null;
        }

        public static float getHorizontalDistance(float currentX) {
            return currentX - mTrackStartPoint.x;
        }
    }
}
