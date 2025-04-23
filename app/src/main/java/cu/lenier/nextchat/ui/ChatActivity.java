package cu.lenier.nextchat.ui;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.MediaRecorder;
import android.net.ConnectivityManager;
import android.net.Network;
import android.os.Bundle;
import android.os.Handler;
import android.view.MotionEvent;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Observer;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;

import cu.lenier.nextchat.R;
import cu.lenier.nextchat.adapter.MessageAdapter;
import cu.lenier.nextchat.config.AppConfig;
import cu.lenier.nextchat.data.AppDatabase;
import cu.lenier.nextchat.model.Message;
import cu.lenier.nextchat.util.MailHelper;
import cu.lenier.nextchat.util.SimpleTextWatcher;
import cu.lenier.nextchat.work.MailSyncWorker;


public class ChatActivity extends AppCompatActivity {
    private static final int REQ_AUDIO = 1001;

    private Toolbar toolbar;
    private RecyclerView rv;
    private EditText et;
    private FloatingActionButton fab;
    private MessageAdapter adapter;
    private String contact, me;
    private MediaRecorder recorder;
    private String audioPath;
    private long lastIncomingTs = 0;
    private final Handler handler = new Handler();
    private ConnectivityManager.NetworkCallback netCallback;
    private ConnectivityManager cm;

    private final Runnable showOfflineRunnable = () -> {
        if (getSupportActionBar() != null) {
            Locale es = new Locale("es");
            String date = new SimpleDateFormat("d 'de' MMM", es)
                    .format(new Date(lastIncomingTs));
            String time = new SimpleDateFormat("hh:mm a", es)
                    .format(new Date(lastIncomingTs));
            getSupportActionBar().setSubtitle("últ. vez " + date + ", " + time);
        }
    };

    // **Esto**: handler para disparar sync periódica
    private final Handler syncHandler = new Handler();
    private final Runnable syncRunnable = new Runnable() {
        @Override public void run() {
            // fuerza al Worker a sincronizar YA
            MailSyncWorker.forceSyncNow(ChatActivity.this);
            // reprograma en 5 segundos
            syncHandler.postDelayed(this, 5_000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        toolbar = findViewById(R.id.toolbar_chat);
        setSupportActionBar(toolbar);
        contact = getIntent().getStringExtra("contact");
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(contact);
            getSupportActionBar().setSubtitle("Esperando conexión...");
        }

        rv = findViewById(R.id.rvMessages);
        et = findViewById(R.id.etMessage);
        fab = findViewById(R.id.fabSend);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new MessageAdapter();
        rv.setAdapter(adapter);

        me = getSharedPreferences("prefs", MODE_PRIVATE).getString("email", "");

        AppDatabase.getInstance(this)
                .messageDao()
                .getByContact(contact)
                .observe(this, msgs -> {
                    adapter.setMessages(msgs);
                    rv.scrollToPosition(adapter.getItemCount() - 1);
                    Executors.newSingleThreadExecutor().execute(() ->
                            AppDatabase.getInstance(this)
                                    .messageDao()
                                    .markAsRead(contact, me)
                    );
                    for (int i = msgs.size() - 1; i >= 0; i--) {
                        Message m = msgs.get(i);
                        if (!m.sent) {
                            lastIncomingTs = m.timestamp;
                            break;
                        }
                    }
                });

        et.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int st, int b, int c) {
                fab.setImageResource(
                        s.toString().trim().isEmpty()
                                ? R.mipmap.ic_mic
                                : R.mipmap.ic_send
                );
            }
        });

        fab.setOnClickListener(v -> {
            String txt = et.getText().toString().trim();
            if (!txt.isEmpty()) {
                et.setText("");
                Message m = new Message();
                m.fromAddress = me;
                m.toAddress = contact;
                m.subject = "NextChat";
                m.body = txt;
                m.attachmentPath = null;
                m.timestamp = System.currentTimeMillis();
                m.sent = true;
                m.read = true;
                m.type = "text";
                m.sendState = Message.STATE_PENDING;
                MailHelper.sendEmail(this, m);
            } else {
                Toast.makeText(this,
                        "Mantén presionado para grabar audio",
                        Toast.LENGTH_SHORT).show();
            }
        });

        fab.setOnLongClickListener(v -> {
            if (checkAudioPerm()) startRecording();
            return true;
        });
        fab.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_UP && recorder != null) {
                stopRecordingAndSend();
            }
            return false;
        });

        cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        netCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                handler.post(this::updateConnectedSubtitle);
            }

            private void updateConnectedSubtitle() {
                if (getSupportActionBar() == null) return;
                long now = System.currentTimeMillis();
                if (now < lastIncomingTs + 2 * 60_000) {
                    getSupportActionBar().setSubtitle("en línea");
                } else {
                    handler.removeCallbacks(showOfflineRunnable);
                    handler.post(showOfflineRunnable);
                }
            }

            @Override
            public void onLost(Network network) {
                handler.post(() -> {
                    if (getSupportActionBar() != null)
                        getSupportActionBar().setSubtitle("Esperando conexión...");
                });
            }
        };
        cm.registerDefaultNetworkCallback(netCallback);
    }

    @Override
    protected void onResume() {
        super.onResume();
        AppConfig.setCurrentChat(contact);
        syncHandler.post(syncRunnable);
    }

    @Override protected void onPause() {
        super.onPause();
        // paro el loop
        syncHandler.removeCallbacks(syncRunnable);
        // ya no estoy en este chat
        AppConfig.setCurrentChat(null);
    }

    @Override
    protected void onStart() {
        super.onStart();
        AppConfig.setCurrentChat(contact);
    }

    @Override
    protected void onStop() {
        super.onStop();
        AppConfig.setCurrentChat(null);
    }


    private boolean checkAudioPerm() {
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.RECORD_AUDIO
        ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    REQ_AUDIO
            );
            return false;
        }
        return true;
    }

    private void startRecording() {
        try {
            File dir = new File(
                    getExternalFilesDir(null), "audios_enviados"
            );
            if (!dir.exists()) dir.mkdirs();
            audioPath = new File(
                    dir, "audio_" + System.currentTimeMillis() + ".3gp"
            ).getAbsolutePath();

            recorder = new MediaRecorder();
            recorder.setAudioSource(
                    MediaRecorder.AudioSource.MIC
            );
            recorder.setOutputFormat(
                    MediaRecorder.OutputFormat.THREE_GPP
            );
            recorder.setOutputFile(audioPath);
            recorder.setAudioEncoder(
                    MediaRecorder.AudioEncoder.AMR_NB
            );
            recorder.prepare();
            recorder.start();
            Toast.makeText(this,
                    "Grabando audio...", Toast.LENGTH_SHORT
            ).show();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this,
                    "Error al grabar audio", Toast.LENGTH_SHORT
            ).show();
        }
    }

    private void stopRecordingAndSend() {
        recorder.stop();
        recorder.release();
        recorder = null;
        Toast.makeText(this,
                "Enviando audio...", Toast.LENGTH_SHORT
        ).show();

        Message m = new Message();
        m.fromAddress = me;
        m.toAddress = contact;
        m.subject = "NextChat Audio";
        m.body = "";
        m.attachmentPath = audioPath;
        m.timestamp = System.currentTimeMillis();
        m.sent = true;
        m.read = true;
        m.type = "audio";
        m.sendState = Message.STATE_PENDING;

        MailHelper.sendAudioEmail(this, m);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cm.unregisterNetworkCallback(netCallback);
        handler.removeCallbacksAndMessages(null);
    }
}
