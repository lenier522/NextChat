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
import cu.lenier.nextchat.data.AppDatabase;
import cu.lenier.nextchat.model.Message;
import cu.lenier.nextchat.model.Message;
import cu.lenier.nextchat.util.MailHelper;
import cu.lenier.nextchat.util.SimpleTextWatcher;

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
    private Handler handler = new Handler();
    private ConnectivityManager.NetworkCallback netCallback;
    private ConnectivityManager cm;

    private Runnable showOfflineRunnable = () -> {
        if (getSupportActionBar() != null) {
            Locale es = new Locale("es");
            String date = new SimpleDateFormat("d 'de' MMM", es)
                    .format(new Date(lastIncomingTs));
            String time = new SimpleDateFormat("hh:mm a", es)
                    .format(new Date(lastIncomingTs));
            getSupportActionBar().setSubtitle("últ. vez " + date + ", " + time);
        }
    };

    @Override protected void onCreate(Bundle s) {
        super.onCreate(s);
        setContentView(R.layout.activity_chat);

        // Toolbar
        toolbar = findViewById(R.id.toolbar_chat);
        setSupportActionBar(toolbar);
        contact = getIntent().getStringExtra("contact");
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(contact);
            getSupportActionBar().setSubtitle("Esperando conexión...");
        }

        // Recycler + adapter
        rv   = findViewById(R.id.rvMessages);
        et   = findViewById(R.id.etMessage);
        fab  = findViewById(R.id.fabSend);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new MessageAdapter();
        rv.setAdapter(adapter);

        me = getSharedPreferences("prefs", MODE_PRIVATE).getString("email", "");

        // Observe messages
        AppDatabase.getInstance(this)
                .messageDao()
                .getByContact(contact)
                .observe(this, (Observer<List<Message>>) msgs -> {
                    adapter.setMessages(msgs);
                    rv.scrollToPosition(adapter.getItemCount() - 1);
                    // mark read
                    Executors.newSingleThreadExecutor().execute(() ->
                            AppDatabase.getInstance(this)
                                    .messageDao()
                                    .markAsRead(contact, me)
                    );
                    // update lastIncomingTs
                    for (int i = msgs.size() - 1; i >= 0; i--) {
                        Message m = msgs.get(i);
                        if (!m.sent) {
                            lastIncomingTs = m.timestamp;
                            updateConnectedSubtitle();
                            scheduleOffline();
                            break;
                        }
                    }
                });

        // FAB icon toggle
        et.addTextChangedListener(new SimpleTextWatcher() {
            @Override public void onTextChanged(CharSequence s,int st,int b,int c) {
                if (s.toString().trim().isEmpty()) {
                    fab.setImageResource(R.mipmap.ic_mic);
                } else {
                    fab.setImageResource(R.mipmap.ic_send);
                }
            }
        });

        // Text send
        fab.setOnClickListener(v -> {
            String txt = et.getText().toString().trim();
            if (!txt.isEmpty()) {
                et.setText("");
                Message m = new Message();
                m.fromAddress    = me;
                m.toAddress      = contact;
                m.subject        = "NextChat";
                m.body           = txt;
                m.attachmentPath = null;
                m.timestamp      = System.currentTimeMillis();
                m.sent           = true;
                m.read           = true;
                m.type           = "text";
                m.sendState      = Message.STATE_PENDING;
                MailHelper.sendEmail(this, m);
            } else {
                Toast.makeText(this,
                        "Mantén presionado para grabar audio",
                        Toast.LENGTH_SHORT).show();
            }
        });

        // Audio record long‑press
        fab.setOnLongClickListener(v -> {
            if (checkAudioPerm()) startRecording();
            return true;
        });
        fab.setOnTouchListener((v,e) -> {
            if (e.getAction() == MotionEvent.ACTION_UP && recorder != null) {
                stopRecordingAndSend();
            }
            return false;
        });

        // Network callback
        cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        netCallback = new ConnectivityManager.NetworkCallback(){
            @Override public void onAvailable(Network network) {
                handler.post(() -> updateConnectedSubtitle());
            }
            @Override public void onLost(Network network) {
                handler.post(() -> {
                    if (getSupportActionBar() != null)
                        getSupportActionBar().setSubtitle("Esperando conexión...");
                });
            }
        };
        cm.registerDefaultNetworkCallback(netCallback);
    }

    private void updateConnectedSubtitle() {
        if (getSupportActionBar() == null) return;
        long now = System.currentTimeMillis();
        if (now < lastIncomingTs + 2*60_000) {
            getSupportActionBar().setSubtitle("en línea");
        } else {
            showOfflineRunnable.run();
        }
    }

    private void scheduleOffline() {
        handler.removeCallbacks(showOfflineRunnable);
        long delay = lastIncomingTs + 2*60_000 - System.currentTimeMillis();
        if (delay < 0) delay = 0;
        handler.postDelayed(showOfflineRunnable, delay);
    }

    private boolean checkAudioPerm() {
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.RECORD_AUDIO
        ) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{ Manifest.permission.RECORD_AUDIO },
                    REQ_AUDIO
            );
            return false;
        }
        return true;
    }

    @Override public void onRequestPermissionsResult(
            int requestCode, String[] perms, int[] grants
    ) {
        if (requestCode == REQ_AUDIO
                && grants.length>0
                && grants[0]==PackageManager.PERMISSION_GRANTED) {
            startRecording();
        } else {
            Toast.makeText(this,
                    "Permiso de audio denegado",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void startRecording(){
        try {
            File dir = new File(
                    getExternalFilesDir(null),"audios_enviados"
            );
            if (!dir.exists()) dir.mkdirs();
            audioPath = new File(
                    dir,"audio_"+System.currentTimeMillis()+".3gp"
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
            recorder.prepare(); recorder.start();
            Toast.makeText(this,
                    "Grabando audio...",Toast.LENGTH_SHORT
            ).show();
        } catch (IOException e){
            e.printStackTrace();
            Toast.makeText(this,
                    "Error al grabar audio",Toast.LENGTH_SHORT
            ).show();
        }
    }

    private void stopRecordingAndSend(){
        recorder.stop();
        recorder.release();
        recorder = null;
        Toast.makeText(this,
                "Enviando audio...",Toast.LENGTH_SHORT
        ).show();

        Message m = new Message();
        m.fromAddress    = me;
        m.toAddress      = contact;
        m.subject        = "NextChat Audio";
        m.body           = "";
        m.attachmentPath = audioPath;
        m.timestamp      = System.currentTimeMillis();
        m.sent           = true;
        m.read           = true;
        m.type           = "audio";
        m.sendState      = Message.STATE_PENDING;

        MailHelper.sendAudioEmail(this, m);
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        cm.unregisterNetworkCallback(netCallback);
        handler.removeCallbacks(showOfflineRunnable);
    }
}
