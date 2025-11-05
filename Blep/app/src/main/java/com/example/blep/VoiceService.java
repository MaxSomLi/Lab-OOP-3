package com.example.blep;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.res.AssetManager;
import android.os.IBinder;
import android.provider.MediaStore;
import android.speech.tts.TextToSpeech;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import org.json.JSONException;
import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.RecognitionListener;
import org.vosk.android.SpeechService;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class VoiceService extends Service implements RecognitionListener, TextToSpeech.OnInitListener {

    private TextToSpeech tts;
    private Recognizer recognizer;
    private Model model;
    private SpeechService speechService;
    private final int SR = 16000, NOTIF_ID = 1, BT = 1024, MP = -1;
    private String MD = "model/";
    private static final String CHANNEL_ID = "voice_channel", DASH = "/", FORM = "HH:mm", T = "text", TP = "text/plain";

    @Override
    public void onCreate() {
        super.onCreate();
        MD += getString(R.string.type);
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, getString(R.string.app_name), NotificationManager.IMPORTANCE_HIGH);
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) {
            nm.createNotificationChannel(channel);
            Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID).setContentTitle(getString(R.string.app_name)).setContentText(getString(R.string.listening)).setSmallIcon(R.drawable.notif).setPriority(NotificationCompat.PRIORITY_HIGH).setDefaults(Notification.DEFAULT_VIBRATE).setOngoing(true).build();
            startForeground(NOTIF_ID, notification);
        }
        tts = new TextToSpeech(this, this);
        AssetManager assetManager = getAssets();
        File modelDir = new File(getFilesDir(), getString(R.string.type));
        try {
            copyAssetFolder(assetManager, MD, modelDir.getAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        if (!modelDir.exists()) {
            try {
                copyAssetFolder(assetManager, MD, modelDir.getAbsolutePath());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        try {
            model = new Model(modelDir.getAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        startRecognition();
    }

    private void copyAssetFolder(AssetManager assetManager, String fromAssetPath, String toPath) throws IOException {
        String[] files = assetManager.list(fromAssetPath);
        if (files == null || files.length == 0) {
            return;
        }
        File dir = new File(toPath);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        for (String file : files) {
            String assetPath = fromAssetPath + DASH + file;
            String destPath = toPath + DASH + file;
            String[] subFiles = assetManager.list(assetPath);
            if (subFiles != null && subFiles.length > 0) {
                copyAssetFolder(assetManager, assetPath, destPath);
            } else {
                copyAssetFile(assetManager, assetPath, destPath);
            }
        }
    }

    private void copyAssetFile(AssetManager assetManager, String fromAssetPath, String toPath) throws IOException {
        try (InputStream in = assetManager.open(fromAssetPath); OutputStream out = new FileOutputStream(toPath)) {
            byte[] buffer = new byte[BT];
            int read;
            while ((read = in.read(buffer)) != MP) {
                out.write(buffer, 0, read);
            }
        }
    }


    private void startRecognition() {
        try {
            recognizer = new Recognizer(model, SR);
            speechService = new SpeechService(recognizer, SR);
            speechService.startListening(this);
        } catch (IOException e) {
            onDestroy();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (speechService != null) {
            speechService.stop();
            speechService.shutdown();
        }
        if (model != null) {
            model.close();
        }
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onPartialResult(String hypothesis) {}

    @Override
    public void onResult(String hypothesis) {
        handleCommand(hypothesis);
    }

    @Override
    public void onFinalResult(String hypothesis) {
        handleCommand(hypothesis);
    }

    @Override
    public void onError(Exception exception) {}

    @Override
    public void onTimeout() {}

    private void handleCommand(String hypothesis) {
        try {
            JSONObject json = new JSONObject(hypothesis);
            String text = json.optString(T, "").toLowerCase(Locale.ROOT);
            if (text.contains(getString(R.string.time))) {
                tts.speak(LocalTime.now().format(DateTimeFormatter.ofPattern(FORM)), TextToSpeech.QUEUE_FLUSH, null);
            } else if (text.contains(getString(R.string.cam))) {
                Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            } else if (text.contains(getString(R.string.write))) {
                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.setType(TP);
                intent.putExtra(Intent.EXTRA_TEXT, text);
                startActivity(intent);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onInit(int status) {}

}
