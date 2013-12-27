package org.habeIchVergessen.testinput;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.AnimationDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.provider.ContactsContract;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AnimationUtils;
import android.widget.GridLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.ViewFlipper;

import java.util.HashMap;


public class MainActivity extends Activity {

    protected final static String LOG_TAG = "TestInput";

    protected GridLayout mOptionWidget;
    protected GridLayout mOptionWidgetGrid;
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

    private boolean mOptionMenuOpened = false;
    private HashMap<Integer,FloatPoint> mOptionMenuTrack = new HashMap<Integer, FloatPoint>();

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

        mOptionWidget = (GridLayout)findViewById(R.id.option_widget);
        mOptionWidgetGrid = (GridLayout)findViewById(R.id.option_widget_grid);
        mPhoneWidget = (GridLayout)findViewById(R.id.phone_widget);
        mMenuWidget =  (RelativeLayout)findViewById(R.id.menu_widget);
        mCallerName = (TextView)findViewById(R.id.caller_name);
        mCallerNumber = (TextView)findViewById(R.id.caller_number);
        mAcceptButton = findViewById(R.id.call_accept_button);
        mAcceptSlide = findViewById(R.id.call_accept_slide);
        mRejectButton = findViewById(R.id.call_reject_button);
        mRejectSlide = findViewById(R.id.call_reject_slide);
        mtfTest = (TextView)findViewById(R.id.tfTest);
        mbTest = findViewById(R.id.bTest);

        Rect mRect = new Rect();
        mAcceptButton.getHitRect(mRect);
        Log.d("TestInput", "hitRect: " + mRect);
        Log.i("TestInput", "onCreate leave");

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

        // animate menu button
        ImageView menuButton = (ImageView)findViewById(R.id.menu_button);
        AnimationDrawable menuButtonAnim = (AnimationDrawable)menuButton.getBackground();
        menuButtonAnim.start();

        torchButton = (ImageView) findViewById(R.id.torchOption);
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

        onTouchEvent_OptionWidget(motionEvent);

        if (!mOptionMenuOpened)
            onTouchEvent_PhoneWidget(motionEvent);

        return result;
    }

    private void testRestartDefaultActivity() {
        Intent sendTextToSpeech = new Intent();
        sendTextToSpeech.setAction("org.durka.hallmonitor.restartDefaultActivity");
        sendBroadcast(sendTextToSpeech);
    }

    /**
     * option menu
     */
    private boolean onTouchEvent_OptionWidget(MotionEvent motionEvent) {

        // point handling
        final int actionIndex = motionEvent.getActionIndex();
        final int actionMasked = motionEvent.getActionMasked();
        final int pointerId = actionIndex;
        FloatPoint downPoint = null;
        double radius = 0;

        switch (actionMasked) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                // just handle 1 event
                if (mOptionMenuTrack.size() > 0)
                    break;

                Rect hitBox = new Rect();
                mMenuWidget.getGlobalVisibleRect(hitBox);

                final float dX = motionEvent.getX(actionIndex), rX = 50, cX = hitBox.centerX();
                final float dY = motionEvent.getY(actionIndex), rY = 30, cY = hitBox.centerY();

                // ellipse
                if (Math.pow(cX - dX, 2) / Math.pow(rX, 2) + Math.pow(cY - dY, 2)  / Math.pow(rY, 2) <= 1) {
                    mOptionMenuTrack.put(motionEvent.getPointerId(actionIndex), new FloatPoint(dX, dY));
                    openOptionMenu();
                    Log.d(LOG_TAG, "DOWN|POINTER_DOWN: added " + motionEvent.getPointerId(actionIndex) + " x: " + dX + ", y: " + dY + " #" + mOptionMenuTrack.size());
                }
                break;
//            case MotionEvent.ACTION_MOVE:
//                if ((downPoint = mOptionMenuTrack.get(motionEvent.getPointerId(actionIndex))) != null) {
//                    // find ImageView at coordinates
//                    Rect hitRect = new Rect();
//                    for (int idx=0; idx < mOptionWidgetGrid.getChildCount(); idx++) {
//                        View view = mOptionWidgetGrid.getChildAt(idx);
//                        view.getGlobalVisibleRect(hitRect);
//                        radius = hitRect.centerX() - hitRect.left;
////                        if (hitRect.contains((int)motionEvent.getX(actionIndex), (int)motionEvent.getY(actionIndex))) {
//                        if (Math.sqrt(Math.pow(hitRect.centerX() - motionEvent.getX(actionIndex), 2) + Math.pow(hitRect.centerY() - motionEvent.getY(actionIndex), 2)) <= radius) {
//                            view.setBackgroundColor(0x80FFFFFF);
//                        } else
//                            view.setBackgroundColor(0x00000000);
//                    }
//                }
//                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                if ((downPoint = mOptionMenuTrack.get(motionEvent.getPointerId(actionIndex))) != null) {
                    mOptionMenuTrack.remove(motionEvent.getPointerId(actionIndex));
                    closeOptionMenu();
                    Log.d(LOG_TAG, "UP|POINTER_UP: " + motionEvent.getPointerId(actionIndex) + " #" + mOptionMenuTrack.size() + ", views: #" + mOptionWidgetGrid.getChildCount());

                    // find ImageView at coordinates
                    Rect hitRect = new Rect();
                    for (int idx=0; idx < mOptionWidgetGrid.getChildCount(); idx++) {
                        View view = mOptionWidgetGrid.getChildAt(idx);
                        view.getGlobalVisibleRect(hitRect);
                        radius = hitRect.centerX() - hitRect.left;

//                        if (hitRect.contains((int)motionEvent.getX(actionIndex), (int)motionEvent.getY(actionIndex))) {
                        if (Math.sqrt(Math.pow(hitRect.centerX() - motionEvent.getX(actionIndex), 2) + Math.pow(hitRect.centerY() - motionEvent.getY(actionIndex), 2)) <= radius) {
                            view.callOnClick();
                            break;
                        }
                    }
                }
            break;
        }

        return true;
    }

    private void openOptionMenu() {
        // just open when no other button is tracked
        if (!mOptionMenuOpened && mActivePointerId == -1) {
            Log.d(LOG_TAG + ".openOptionMenu", "here we go");
            mOptionMenuOpened = true;
            mOptionWidget.setVisibility((mOptionMenuOpened ? View.VISIBLE : View.INVISIBLE));
        }
    }

    private void closeOptionMenu() {
        if (mOptionMenuOpened) {
            Log.d(LOG_TAG + ".closeOptionMenu", "here we go");
            mOptionMenuOpened = false;
            mOptionWidget.setVisibility((mOptionMenuOpened ? View.VISIBLE : View.INVISIBLE));
        }
    }

    //toggle the torch
    public void sendToggleTorch(View view) {
        Intent intent = new Intent(TOGGLE_FLASHLIGHT);
        intent.putExtra("strobe", false);
        intent.putExtra("period", 100);
        intent.putExtra("bright", false);
        sendBroadcast(intent);
        torchIsOn = !torchIsOn;
        if (torchIsOn) torchButton.setImageResource(R.drawable.ic_appwidget_torch_on);
        else torchButton.setImageResource(R.drawable.ic_appwidget_torch_off);
    }

    /**
     * phone widget
     */
    private boolean onTouchEvent_PhoneWidget(MotionEvent motionEvent)
    {
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

    public void moveCallButton(View button, int offset)
    {
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
            // circle through corners
            //double radius = Math.sqrt(Math.pow(rect.centerX() - rect.left, 2) + Math.pow(rect.centerY() - rect.top, 2));
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

    public class FloatPoint {
        private float mX, mY;
        public FloatPoint(float x, float y) {
            mX = x;
            mY = y;
        }

        public float getX() { return mX; };
        public float getY() { return mY; };
    }
}
