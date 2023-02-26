package com.example.floatingbubble

import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.*
import android.widget.ImageView
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.view.marginStart
import com.example.floatingbubble.utils.NotificationUtils
import kotlin.math.roundToInt


class FloatingWidgetService: Service() {

    companion object{
        private const val TAG = "FloatingWidgetService"
    }


    private var layoutFlag: Int = 0
    private lateinit var windowManager: WindowManager
    private lateinit var mFloatingWidget: View
    private lateinit var mTrashView: ImageView
    private lateinit var mMenuView: View

    private lateinit var floatingWidgetLayoutParam: WindowManager.LayoutParams
    private lateinit var trashLayoutParams        : WindowManager.LayoutParams
    private lateinit var menuLayoutParam        : WindowManager.LayoutParams

    private var screenWidth: Int = 0
    private var screenHeight: Int = 0

    private var trashWidth: Int = 140
    private var trashHeight: Int = 140

    private var trashOffsetY = 0

    private var trashTouchablePadding = 0


    private var initialX = 0
    private var initialY = 0

    private var initialTouchX = 0.0f
    private var initialTouchY = 0.0f

    private var clickTime: Long = 0
    private var releaseTime: Long = 0

    private lateinit var orientationEventListener: OrientationEventListener

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        layoutFlag = when{
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else                    -> WindowManager.LayoutParams.TYPE_PHONE
        }

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        initDimensions()
        initViews()
        attachTouchListener()

        orientationEventListener = object : OrientationEventListener(this){
            override fun onOrientationChanged(orientation: Int) {
                initDimensions()
            }

        }
        if (orientationEventListener.canDetectOrientation()) {
            orientationEventListener.enable();
        }

        val notificationId = 1001
        val notification = createNotification()

        startForeground(notificationId, notification)
        return START_STICKY
    }
    override fun onBind(p0: Intent?): IBinder? {
       return null
    }

    override fun onDestroy() {
        super.onDestroy()

        if(::mFloatingWidget.isInitialized) windowManager.removeView(mFloatingWidget)
        if(::mTrashView.isInitialized) windowManager.removeView(mTrashView)
        if(::mMenuView.isInitialized) windowManager.removeView(mMenuView)
        if(orientationEventListener.canDetectOrientation()){
            orientationEventListener.disable()
        }
    }

    private fun initDimensions(){
        screenWidth = applicationContext.resources.displayMetrics.widthPixels
        screenHeight = applicationContext.resources.displayMetrics.heightPixels
        trashOffsetY = (screenHeight * 0.1).roundToInt()
        trashTouchablePadding = trashHeight
    }

    private fun initViews(){

        mFloatingWidget = LayoutInflater.from(this).inflate(R.layout.layout_floating_widget, null)
        mTrashView = LayoutInflater.from(this).inflate(R.layout.layout_trash, null) as ImageView

        floatingWidgetLayoutParam = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            y = 100
        }

        trashLayoutParams = WindowManager.LayoutParams(
                trashWidth,
                trashHeight,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER
        }

        windowManager.addView(mTrashView, trashLayoutParams)
        windowManager.addView(mFloatingWidget, floatingWidgetLayoutParam)

        mTrashView.visibility = View.GONE
        mFloatingWidget.visibility = View.VISIBLE

        initMenuLayout()
    }

    private fun initMenuLayout(){

        mMenuView = LayoutInflater.from(this).inflate(R.layout.layout_menu, null)

        menuLayoutParam = WindowManager.LayoutParams(
            screenWidth - mFloatingWidget.width,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = floatingWidgetLayoutParam.gravity
        }
        windowManager.addView(mMenuView, menuLayoutParam)
        mMenuView.visibility = View.GONE

    }

    private fun attachTouchListener(){
        mFloatingWidget.setOnTouchListener { view, motionEvent ->

            when (motionEvent.action) {
                MotionEvent.ACTION_DOWN -> {
                    clickTime = System.currentTimeMillis()
                    onTouchDown(motionEvent)
                }
                MotionEvent.ACTION_UP -> {

                    releaseTime = System.currentTimeMillis()
                    if(releaseTime - clickTime <= 100){
                        view.performClick()
                        onClick()
                    }
                    onTouchUp(motionEvent)

                }
                MotionEvent.ACTION_MOVE -> {
                    onTouchMove(motionEvent)
                }
            }
            true
        }


    }


    private fun onClick(){
       if(mMenuView.visibility == View.GONE){
           animateMenuLayout(0, floatingWidgetLayoutParam.x + mFloatingWidget.width)
           animateMenuLayout(0, 300)
       }
       else{
           animateMenuLayout(menuLayoutParam.x, 0)
           animateMenuLayout(300, 0)
        }
    }

    private fun onTouchDown(motionEvent: MotionEvent){

        animateTrashView(0, trashOffsetY)

        initialX = floatingWidgetLayoutParam.x
        initialY = floatingWidgetLayoutParam.y

        initialTouchX = motionEvent.rawX
        initialTouchY = motionEvent.rawY
    }

    private fun onTouchMove(motionEvent: MotionEvent){
        floatingWidgetLayoutParam.x = initialX + (initialTouchX - motionEvent.rawX).toInt()
        floatingWidgetLayoutParam.y = initialY + (motionEvent.rawY - initialTouchY).toInt()

        windowManager.updateViewLayout(mFloatingWidget, floatingWidgetLayoutParam)


        if(checkCollision(motionEvent.rawX, motionEvent.rawY)) {
            mTrashView.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.red))
            mTrashView.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.white))
        } else {
            mTrashView.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.grey_light))
            mTrashView.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.grey_dark))
        }
    }

    private fun onTouchUp(motionEvent: MotionEvent){

        floatingWidgetLayoutParam.x = initialX + (initialTouchX - motionEvent.rawX).toInt()
        floatingWidgetLayoutParam.y = initialY + (motionEvent.rawY - initialTouchY).toInt()


        when {
            checkCollision(motionEvent.rawX, motionEvent.rawY) -> {
                stopSelf()
            }
            floatingWidgetLayoutParam.x <= screenWidth / 2 -> {
                animateFloatingWidget(floatingWidgetLayoutParam.x, 0)
                animateTrashView(trashOffsetY, 0)
            }
            floatingWidgetLayoutParam.x > screenWidth / 2 -> {
                animateFloatingWidget( floatingWidgetLayoutParam.x, screenWidth)
                animateTrashView(trashOffsetY, 0)
            }

        }
    }

    private fun checkCollision(x: Float, y: Float): Boolean {
        return  y <= screenHeight - trashOffsetY + trashTouchablePadding &&
                y >= screenHeight - trashOffsetY - trashTouchablePadding &&
                x <= screenWidth/2 + trashTouchablePadding &&
                x >= screenWidth/2 - trashTouchablePadding
    }

    private fun animateFloatingWidget(startX: Int, endX: Int){
        ValueAnimator.ofInt( startX, endX ).apply {

            addUpdateListener { animation ->
                floatingWidgetLayoutParam.x = animation.animatedValue as Int
                windowManager.updateViewLayout(mFloatingWidget, floatingWidgetLayoutParam)
            }

            duration = 300
            start()
        }
    }

    private fun animateMenuLayout(startX: Int, endX: Int){
        if(startX == 0)
            mMenuView.visibility = View.VISIBLE
        ValueAnimator.ofInt( startX, endX ).apply {

            addUpdateListener { animation ->
                menuLayoutParam.x = animation.animatedValue as Int
                menuLayoutParam.y = floatingWidgetLayoutParam.y + mFloatingWidget.height
                windowManager.updateViewLayout(mMenuView, menuLayoutParam)
                if(endX == 0)
                    mMenuView.visibility = View.GONE
            }
            duration = 300
            start()
        }
    }

    private fun animateTrashView(startY: Int, endY: Int){

        if(startY == 0) mTrashView.visibility = View.VISIBLE
        ValueAnimator.ofInt( startY, endY ).apply {
            addUpdateListener { animation ->
                trashLayoutParams.y = animation.animatedValue as Int
                windowManager.updateViewLayout(mTrashView, trashLayoutParams)
                if(endY == 0) mTrashView.visibility = View.GONE
            }

            duration = 100
            start()
        }
    }


    private fun createNotification(): Notification{

        NotificationUtils.createNotificationChannel(this)
        // Create an explicit intent for an Activity in your app
        val intent = Intent(this, HomeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)


        var builder = NotificationCompat.Builder(this, NotificationUtils.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("My notification")
            .setContentText("Much longer text that cannot fit one line...")
            .setStyle(
                NotificationCompat.BigTextStyle()
                .bigText("Much longer text that cannot fit one line..."))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)

        return builder.build()
    }
}