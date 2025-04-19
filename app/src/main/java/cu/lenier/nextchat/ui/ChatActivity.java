package cu.lenier.nextchat.ui;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Observer;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executors;

import cu.lenier.nextchat.R;
import cu.lenier.nextchat.adapter.MessageAdapter;
import cu.lenier.nextchat.data.AppDatabase;
import cu.lenier.nextchat.model.Message;
import cu.lenier.nextchat.util.MailHelper;

public class ChatActivity extends AppCompatActivity {
    private static final int REQ_AUDIO = 1001;

    private RecyclerView rv;
    private EditText     et;
    private Button       btn;
    private MessageAdapter adapter;
    private String contact;
    private String me;

    private MediaRecorder recorder;
    private String        audioPath;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        rv      = findViewById(R.id.rvMessages);
        et      = findViewById(R.id.etMessage);
        btn     = findViewById(R.id.btnSend);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new MessageAdapter();
        rv.setAdapter(adapter);

        contact = getIntent().getStringExtra("contact");
        me      = getSharedPreferences("prefs", MODE_PRIVATE)
                .getString("email", "");

        // Observamos mensajes y marcamos TODO como leído al mostrar
        AppDatabase.getInstance(this)
                .messageDao()
                .getByContact(contact)
                .observe(this, (Observer<List<Message>>) messages -> {
                    adapter.setMessages(messages);
                    rv.scrollToPosition(adapter.getItemCount() - 1);

                    // Marcar como leídos todos los entrantes
                    Executors.newSingleThreadExecutor().execute(() ->
                            AppDatabase.getInstance(this)
                                    .messageDao()
                                    .markAsRead(contact, me)
                    );
                });

        // Envío de texto
        btn.setOnClickListener(v -> {
            String text = et.getText().toString().trim();
            if (text.isEmpty()) return;
            et.setText("");
            MailHelper.sendEmail(this, contact, text);
        });

        // Pulsación larga para grabar audio
        btn.setOnLongClickListener(v -> {
            if (checkAudioPerm()) startRecording();
            return true;
        });

        // Al soltar, paramos y enviamos
        btn.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_UP && recorder != null) {
                stopRecordingAndSend();
            }
            return false;
        });
    }

    private boolean checkAudioPerm() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    REQ_AUDIO
            );
            return false;
        }
        return true;
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] perms, int[] results) {
        if (requestCode == REQ_AUDIO
                && results.length > 0
                && results[0] == PackageManager.PERMISSION_GRANTED) {
            startRecording();
        } else {
            Toast.makeText(this, "Permiso de audio denegado", Toast.LENGTH_SHORT).show();
        }
    }

    private void startRecording() {
        try {
            File dir = new File(getExternalFilesDir(null), "audios_enviados");
            if (!dir.exists()) dir.mkdirs();
            audioPath = new File(dir, "audio_" + System.currentTimeMillis() + ".3gp")
                    .getAbsolutePath();

            recorder = new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            recorder.setOutputFile(audioPath);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            recorder.prepare();
            recorder.start();
            Toast.makeText(this, "Grabando audio...", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Error al grabar audio", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopRecordingAndSend() {
        try {
            recorder.stop();
            recorder.release();
            recorder = null;
            Toast.makeText(this, "Enviando audio...", Toast.LENGTH_SHORT).show();
            MailHelper.sendAudioEmail(this, contact, audioPath);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Error al enviar audio", Toast.LENGTH_SHORT).show();
        }
    }
}
