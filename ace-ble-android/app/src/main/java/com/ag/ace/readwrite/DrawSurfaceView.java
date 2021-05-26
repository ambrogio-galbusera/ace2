package com.ag.ace.readwrite;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathEffect;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.RectF;
import android.text.TextPaint;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;

import com.stkent.polygondrawingutil.PolygonDrawingUtil;

import java.text.DecimalFormat;
import java.util.StringTokenizer;
import java.util.Timer;
import java.util.TimerTask;

import static com.ag.ace.readwrite.R.color.cardview_light_background;

public class DrawSurfaceView {
    public enum GraphModeEnum
    {
        Single,
        Overlapped
    };

    private SurfaceView drawView;
    private SurfaceView drawViewTag;
    private SurfaceView drawViewRuler;
    private SurfaceHolder drawViewHolder;
    private SurfaceHolder drawViewHolderTag;
    private SurfaceHolder drawViewHolderRuler;
    private DrawThread drawThread = null;
    private GraphModeEnum graphMode;
    private boolean surfaceCreated;
    private int countValue = -1;
    private int countStatus = -1;
    private PolygonDrawingUtil polygonDrawingUtil;

    final double PORT_FULL_SCALE = 2000.0;
    final int NUM_CHANNELS = 6;
    private int height, width;
    private boolean shouldRefresh = false;
    private int deltaX = 0;
    private int touchingState = 0;

    private int x = 0, lastX = 0;
    private int[] ys, lastYs;
    private int[] gValues;
    private int rulerX = 0, rulerY = 0, rulerStartX = 0, rulerStartY = 0;
    private Context context;

    private int COLORS[] = {
            Color.argb(255, 255, 0, 0),
            Color.argb(255, 0, 255, 0),
            Color.argb(255, 0, 0, 255),
            Color.argb(255, 255, 0, 0),
            Color.argb(255, 0, 255, 0),
            Color.argb(255, 0, 0, 255)
    };

    private double SCALES[]={
            1000.0,
            1000.0,
            1000.0,
            1,
            1,
            1
    };

    private String NUMBER_FORMATS[]={
            "0.000",
            "0.000",
            "0.000",
            "0",
            "0",
            "0"
    };

    private String UNITS[]={
            "g",
            "g",
            "g",
            "deg/s",
            "deg/s",
            "deg/s"
    };

    private String LABELS[]={
            "[X] ",
            "[Y] ",
            "[Z] ",
            "[gX] ",
            "[gY] ",
            "[gZ] "
    };

    private Timer viewRefreshTimer = new Timer();

    public DrawSurfaceView() {
        x = 1;
        graphMode = GraphModeEnum.Single;
        viewRefreshTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                shouldRefresh = true;
            }
        }, 0, 80);

        polygonDrawingUtil = new PolygonDrawingUtil();
    }

    public void setSurfaceView(SurfaceView currentSurfaceView,
                                   SurfaceView currentSurfaceViewTag,
                                   SurfaceView currentSurfaceViewRuler,
                                   Context context) {
        drawView = currentSurfaceView;
        drawViewHolder = drawView.getHolder();

        drawViewTag = currentSurfaceViewTag;
        drawViewHolderTag = drawViewTag.getHolder();
        drawViewTag.setZOrderOnTop(true);
        drawViewHolderTag.setFormat(PixelFormat.TRANSPARENT);

        drawViewRuler = currentSurfaceViewRuler;
        drawViewHolderRuler = drawViewRuler.getHolder();
        drawViewRuler.setZOrderOnTop(true);
        drawViewHolderRuler.setFormat(PixelFormat.TRANSPARENT);

        drawViewRuler.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        touchingState = 0;
                        rulerX = (int) event.getX();
                        rulerY = (int) event.getY();
                        rulerStartX = rulerX;
                        rulerStartY = rulerY;
                        break;
                    case MotionEvent.ACTION_MOVE:
                        rulerX = (int) event.getX();
                        rulerY = (int) event.getY();
                        touchingState = 1;
                        drawRulerThreadStarter();
                        break;
                    case MotionEvent.ACTION_UP:
                        rulerX = (int) event.getX();
                        rulerY = (int) event.getY();
                        if (Math.abs(rulerX - rulerStartX) < 5 && Math.abs(rulerY - rulerStartY) < 5)
                            touchingState = 3;
                        else
                            touchingState = 2;
                        drawRulerThreadStarter();
                        break;
                    default:
                        break;
                }
                return true;
            }
        });

        drawViewHolder.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                resetSurfaceViewX();
                resetCanvas();
                height = drawView.getHeight();
                width = drawView.getWidth();
                surfaceCreated = true;
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {

            }
        });

        lastYs = new int[NUM_CHANNELS];
        ys = new int[NUM_CHANNELS];
        gValues = new int[NUM_CHANNELS];
        for (int i = 0; i < NUM_CHANNELS; i++) {
            ys[i] = height / 2;
            lastYs[i] = ys[i];
        }

        this.context = context;
    }

    public void drawPoint(int drawX, int[] drawYs) {
        if (shouldRefresh) {
            for (int i=0; i<NUM_CHANNELS; i++) {
                int baseHeight = (height / numGraphsPerMode(graphMode));
                int baseY = (baseHeight * graphIndexPerMode(graphMode, i));
                ys[i] = baseY + (int)((baseHeight / 2) - (((float) drawYs[i]) / PORT_FULL_SCALE * (baseHeight / 2)));
            }

            deltaX = 1; //drawX - lastX;
            lastX = drawX;
            x += deltaX;
            shouldRefresh = false;
            for (int i=0; i<NUM_CHANNELS; i++)
                gValues[i] = drawYs[i];

            if (surfaceCreated) {
                drawThread = new DrawThread();
                drawThread.start();
            }
        }
    }

    private void drawRulerThreadStarter() {
        height = drawView.getHeight();
        width = drawView.getWidth();
        DrawRulerThread drawRulerThread = new DrawRulerThread(drawViewHolderRuler);
        drawRulerThread.start();
    }

    private class DrawRulerThread extends Thread {
        private SurfaceHolder surfaceHolder = null;

        public DrawRulerThread(SurfaceHolder surfaceHolder) {
            this.surfaceHolder = surfaceHolder;
        }

        public void run() {
            Canvas canvas = null;
            Bitmap bitmap = BitmapFactory.decodeResource(context.getResources(), R.drawable.tag);
            Paint pen = new Paint();
            pen.setColor(Color.GRAY);
            pen.setStrokeWidth(2);
            pen.setTextSize(60);
            Paint dotPen = new Paint();
            dotPen.setColor(Color.GRAY);
            dotPen.setStrokeWidth(2);
            PathEffect effect = new DashPathEffect(new float[]{2, 4, 2, 4,}, 1);
            dotPen.setPathEffect(effect);

            try {
                switch (touchingState) {
                    case 0:
                        canvas = surfaceHolder.lockCanvas();
                        break;
                    case 1:
                        canvas = surfaceHolder.lockCanvas();
                        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
                        canvas.drawLine(rulerStartX, rulerStartY, rulerX, rulerY, dotPen);
                        break;
                    case 2:
                        canvas = surfaceHolder.lockCanvas();
                        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
                        canvas.drawLine(rulerStartX, rulerStartY, rulerX, rulerY, pen);
                        canvas.drawBitmap(bitmap, null, new Rect(rulerStartX - 20, rulerStartY - 40,
                                rulerStartX + 20, rulerStartY), pen);
                        canvas.drawBitmap(bitmap, null, new Rect(rulerX - 20, rulerY - 40,
                                rulerX + 20, rulerY), pen);
                        canvas.drawLine(rulerStartX, rulerStartY, rulerX, rulerStartY, dotPen);
                        canvas.drawLine(rulerX, rulerStartY, rulerX, rulerY, dotPen);
                        double deltaTime = Math.abs(rulerStartX - rulerX) * IMUData.getRECORDRATE();
                        String timeInFormat = new DecimalFormat("0.##").format(deltaTime) + "s";
                        canvas.drawText(timeInFormat,
                                (float) (rulerX - rulerStartX) / 2 + rulerStartX, rulerStartY, pen);
                        double delta = Math.abs(rulerStartY - rulerY) / (double) (height/NUM_CHANNELS) * PORT_FULL_SCALE;
                        String measureFormat = new DecimalFormat("0.###").format(delta/1000.0) + "g";
                        canvas.drawText(measureFormat,
                                rulerX, (float) (rulerY - rulerStartY) / 2 + rulerStartY, pen);
                        break;
                    case 3 :
                        canvas = surfaceHolder.lockCanvas();
                        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
                    default:
                        break;
                }
            } finally {
                surfaceHolder.unlockCanvasAndPost(canvas);
            }
        }
    }

    private class DrawThread extends Thread {

        boolean mInited = false;
        Paint mPaint;
        Path mPath;
        TextPaint mTextPaint;
        float mTextOffset;

        private void Init(int w, int h)
        {
            mPaint = new Paint();
            mPaint.setStyle(Paint.Style.FILL);

            RectF bounds = new RectF(0, 0, w, h);

            mPath = new Path();
            polygonDrawingUtil.constructPolygonPath(mPath, 3, bounds.centerX(), bounds.centerY(), bounds.width()/2, 50,-90);

            mTextPaint = new TextPaint();
            mTextPaint.setColor(Color.argb(0x80, 0, 0, 0));
            mTextPaint.setTextSize(128);
            mTextPaint.setTextAlign(Paint.Align.CENTER);
            float textHeight = mTextPaint.descent() - mTextPaint.ascent();
            mTextOffset = (textHeight / 2) - mTextPaint.descent();
            Paint.FontMetrics mf = mTextPaint.getFontMetrics();

            mInited = true;
        }

        private void drawTriangle(Canvas canvas, int color)
        {
            int oldColor = mPaint.getColor();

            mPaint.setColor(color);
            canvas.drawPath(mPath, mPaint);

            mPaint.setColor(oldColor);
        }

        private void drawCountDown(Canvas canvas)
        {
            if (countValue == -1)
                return;

            Init(canvas.getWidth(), canvas.getHeight());

            if (countStatus == COUNT_STATUS_COUNTING) {
                String text = Integer.toString(countValue);

                RectF bounds = new RectF(0, 0, canvas.getWidth(), canvas.getHeight());
                drawTriangle(canvas, Color.argb(0x80, 255,0,0));

                canvas.drawText(text, bounds.centerX(), bounds.centerY() + mTextOffset, mTextPaint);

                mTextPaint.setTextSize(60);
                canvas.drawText("Fall detected!!", bounds.centerX(), bounds.centerY() + mTextOffset+60, mTextPaint);
            }
            else if (countStatus == COUNT_STATUS_SENDING) {
                RectF bounds = new RectF(0, 0, canvas.getWidth(), canvas.getHeight());
                drawTriangle(canvas, Color.argb(0x80, 255,0,0));

                mTextPaint.setTextSize(60);
                canvas.drawText("Sending SMS...", bounds.centerX(), bounds.centerY(), mTextPaint);
            }
            else if (countStatus == COUNT_STATUS_SENT) {
                RectF bounds = new RectF(0, 0, canvas.getWidth(), canvas.getHeight());
                drawTriangle(canvas, Color.argb(0x80, 255,0,0));

                mTextPaint.setTextSize(60);
                canvas.drawText("Delivering SMS...", bounds.centerX(), bounds.centerY(), mTextPaint);
            }
            else if (countStatus == COUNT_STATUS_DELIVERED) {
                RectF bounds = new RectF(0, 0, canvas.getWidth(), canvas.getHeight());
                drawTriangle(canvas, Color.argb(0x80, 0,255,0));

                mTextPaint.setTextSize(60);
                canvas.drawText("SMS DELIVERED", bounds.centerX(), bounds.centerY(), mTextPaint);
            }
        }
        @Override
        public void run() {
            Canvas canvas = null;
            Canvas tagCanvas = null;
            Paint pen = new Paint();
            pen.setStrokeWidth(5);
            pen.setAntiAlias(true);
            Paint tagPen = new Paint();
            tagPen.setColor(Color.GRAY);
            tagPen.setStrokeWidth(1);
            tagPen.setAntiAlias(true);
            tagPen.setTextSize(60);

            Paint dotPen = new Paint();
            dotPen.setColor(Color.GRAY);
            dotPen.setStrokeWidth(1);

            try {
                if (x > width) {
                    x = 0;
                }
                canvas = drawViewHolder.lockCanvas(new Rect(x - deltaX, 0, x + 100, height));
                tagCanvas = drawViewHolderTag.lockCanvas();
                canvas.drawColor(Color.WHITE);
                for (int i=0; i<NUM_CHANNELS; i++) {
                    pen.setColor(COLORS[i]);
                    canvas.drawLine(x - deltaX, lastYs[i], x, ys[i], pen);
                }

                for (int i=0; i<numGraphsPerMode(graphMode); i++)
                {
                    int baseY = (height / numGraphsPerMode(graphMode)) * (i+1);
                    canvas.drawLine(0, baseY, width, baseY, dotPen);
                }

                tagCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
                tagCanvas.drawLine(x, 0, x, height, tagPen);

                Paint.FontMetrics fm = tagPen.getFontMetrics();
                int fontHeight = (int)(fm.descent-fm.ascent);
                int[] textYs = new int[NUM_CHANNELS];
                for (int i=0; i<NUM_CHANNELS; i++)
                {
                    textYs[i] = ys[i];
                    for (int j=0; j<i; j++) {
                        int deltaY = Math.abs(textYs[i] - textYs[j]);
                        if (deltaY < fontHeight)
                            textYs[i] = textYs[j] + fontHeight;
                    }
                }
                for (int i=0; i<NUM_CHANNELS; i++) {
                    double f = ((double) gValues[i]) / SCALES[i];
                    String valueFormat = new DecimalFormat(NUMBER_FORMATS[i]).format(f);
                    tagCanvas.drawText(LABELS[i] + valueFormat + UNITS[i], x, textYs[i], tagPen);
                }

                drawCountDown(tagCanvas);

                for (int i=0; i<NUM_CHANNELS; i++)
                    lastYs [i] = ys[i];
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (canvas != null) {
                    drawViewHolder.unlockCanvasAndPost(canvas);
                    drawViewHolderTag.unlockCanvasAndPost(tagCanvas);
                }
            }
        }

    }

    public void resetSurfaceViewX() {
        x = 0;
    }

    public void resetCanvas(){
        SurfaceHolder holder = null;
        holder = drawViewHolder;

        Canvas canvas = holder.lockCanvas();
        canvas.drawColor(Color.rgb(255, 255, 255));

        Paint dotPen = new Paint();
        dotPen.setColor(Color.GRAY);
        dotPen.setStrokeWidth(1);

        for (int i = 0; i < numGraphsPerMode(graphMode); i++) {
            int baseY = (height / numGraphsPerMode(graphMode)) * (i + 1);
            canvas.drawLine(0, baseY, width, baseY, dotPen);
        }

        holder.unlockCanvasAndPost(canvas);
    }

    public GraphModeEnum getMode() {
        return graphMode;
    }

    public void switchMode()
    {
        if (graphMode == GraphModeEnum.Overlapped.Single)
            graphMode = GraphModeEnum.Overlapped;
        else
            graphMode = GraphModeEnum.Single;

        resetSurfaceViewX();
        resetCanvas();
    }

    public static int COUNT_STATUS_COUNTING = 0;
    public static int COUNT_STATUS_SENDING = 1;
    public static int COUNT_STATUS_SENT = 2;
    public static int COUNT_STATUS_DELIVERED = 3;

    public void updateCountDownStatus(int value, int status)
    {
        countValue = value;
        countStatus = status;
    }

    private int numGraphsPerMode(GraphModeEnum graphMode)
    {
        if (graphMode == GraphModeEnum.Overlapped)
            return 2;

        return NUM_CHANNELS;
    }

    private int graphIndexPerMode(GraphModeEnum graphMode, int index)
    {
        return (index / (NUM_CHANNELS / numGraphsPerMode(graphMode)));
    }

}
