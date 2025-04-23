package cu.lenier.nextchat.service;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.sun.mail.imap.IMAPFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.Executors;

import javax.mail.Flags;
import javax.mail.Folder;
import javax.mail.Part;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.search.FlagTerm;
import javax.mail.event.MessageCountAdapter;
import javax.mail.event.MessageCountEvent;

import cu.lenier.nextchat.R;
import cu.lenier.nextchat.config.AppConfig;
import cu.lenier.nextchat.data.AppDatabase;
import cu.lenier.nextchat.data.MessageDao;
import cu.lenier.nextchat.model.Message;
import cu.lenier.nextchat.util.CryptoHelper;

public class MailService extends Service {
    private static final String TAG            = "MailService";
    private static final String TXT_SUBJ       = "NextChat";
    private static final String AUD_SUBJ       = "NextChat Audio";
    private static final String CHANNEL_SYNC   = "MailSyncChannel";
    private static final String CHANNEL_NEWMSG = "NewMsgChannel";
    private static final int    NOTIF_ID_SYNC  = 1;

    private MessageDao dao;
    private Session    session;
    private IMAPFolder inbox;               // Campo de clase
    private volatile boolean running = false;

    @Override
    public void onCreate() {
        super.onCreate();
        dao = AppDatabase.getInstance(this).messageDao();

        Properties props = new Properties();
        props.put("mail.imap.host", "imap.nauta.cu");
        props.put("mail.imap.port", "143");
        props.put("mail.imap.ssl.enable", "false");
        session = Session.getInstance(props);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            nm.createNotificationChannel(new NotificationChannel(
                    CHANNEL_SYNC,   "NextChat Sync",    NotificationManager.IMPORTANCE_LOW));
            nm.createNotificationChannel(new NotificationChannel(
                    CHANNEL_NEWMSG, "Notificaciones",   NotificationManager.IMPORTANCE_DEFAULT));
        }
    }

    @Override
    public int onStartCommand(Intent intent,int flags,int startId) {
        Notification notif = new NotificationCompat.Builder(this, CHANNEL_SYNC)
                .setContentTitle("NextChat")
                .setContentText("Esperando mensajes…")
                .setSmallIcon(android.R.drawable.ic_popup_sync)
                .setOngoing(true)
                .build();
        startForeground(NOTIF_ID_SYNC, notif);

        if (!running) {
            running = true;
            Executors.newSingleThreadExecutor().execute(this::imapIdleLoop);
        }
        return START_STICKY;
    }

    private void imapIdleLoop() {
        while (running) {
            Store store = null;
            try {
                String email = getSharedPreferences("prefs", MODE_PRIVATE)
                        .getString("email", "");
                String pass  = getSharedPreferences("prefs", MODE_PRIVATE)
                        .getString("pass", "");

                store = session.getStore("imap");
                store.connect("imap.nauta.cu", 143, email, pass);

                // Abrir o crear carpeta NextChat
                Folder root = store.getFolder("INBOX");
                inbox = (IMAPFolder) root.getFolder("NextChat");
                if (!inbox.exists()) {
                    inbox.create(Folder.HOLDS_MESSAGES);
                }
                inbox.open(Folder.READ_WRITE);

                // Descarga inicial de mensajes no vistos
                FlagTerm unseen = new FlagTerm(new Flags(Flags.Flag.SEEN), false);
                javax.mail.Message[] init = inbox.search(unseen);
                for (javax.mail.Message m : init) {
                    handleMessage(m);
                }
                inbox.setFlags(init, new Flags(Flags.Flag.SEEN), true);

                // Listener IMAP IDLE
                inbox.addMessageCountListener(new MessageCountAdapter() {
                    @Override public void messagesAdded(MessageCountEvent ev) {
                        for (javax.mail.Message m : ev.getMessages()) {
                            handleMessage(m);
                        }
                        // Marcamos SEEN
                        try {
                            inbox.setFlags(ev.getMessages(),
                                    new Flags(Flags.Flag.SEEN), true);
                        } catch (Exception ignore) {}
                    }
                });

                // Bucle IDLE + reconexión
                while (running && inbox.isOpen()) {
                    try {
                        inbox.idle();
                    } catch (Exception idleEx) {
                        Log.w(TAG, "IDLE error, reiniciando loop", idleEx);
                        break;
                    }
                }

            } catch (Exception e) {
                Log.e(TAG, "imapIdleLoop fallo, reintentando en 5s", e);
            } finally {
                try { if (inbox != null && inbox.isOpen()) inbox.close(false); } catch (Exception ignored) {}
                try { if (store != null && store.isConnected()) store.close(); } catch (Exception ignored) {}
            }

            // Esperar 5s antes de reconectar
            try { Thread.sleep(5000); } catch (InterruptedException ignored) {}
        }
    }

    private void handleMessage(javax.mail.Message m) {
        try {
            String subj = m.getSubject();
            if (!TXT_SUBJ.equals(subj) && !AUD_SUBJ.equals(subj)) return;

            String me = getSharedPreferences("prefs", MODE_PRIVATE)
                    .getString("email", "");
            Message msg = new Message();
            msg.fromAddress    = m.getFrom()[0].toString();
            msg.toAddress      = me;
            msg.subject        = subj;
            msg.timestamp      = m.getReceivedDate().getTime();
            msg.sent           = false;
            msg.read           = false;

            Object content = m.getContent();
            if (TXT_SUBJ.equals(subj) && !(content instanceof javax.mail.Multipart)) {
                msg.type = "text";
                msg.body = CryptoHelper.decrypt(content.toString());
            } else {
                msg.type = "audio";
                msg.body = "";
                javax.mail.Multipart mp = (javax.mail.Multipart) content;
                for (int i = 0; i < mp.getCount(); i++) {
                    Part part = mp.getBodyPart(i);
                    if (Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition())) {
                        File dir = new File(getExternalFilesDir(null), "audios_recibidos");
                        if (!dir.exists()) dir.mkdirs();
                        File enc = new File(dir, System.currentTimeMillis() + "_" + part.getFileName());
                        try (InputStream is = part.getInputStream();
                             FileOutputStream fos = new FileOutputStream(enc)) {
                            byte[] buf = new byte[4096]; int r;
                            while ((r = is.read(buf)) > 0) fos.write(buf, 0, r);
                        }
                        File dec = new File(dir, enc.getName().replace(".enc", ".3gp"));
                        CryptoHelper.decryptAudio(enc, dec);
                        enc.delete();
                        msg.attachmentPath = dec.getAbsolutePath();
                        break;
                    }
                }
            }

            // Guardar en BD
            Executors.newSingleThreadExecutor().execute(() -> dao.insert(msg));

            // Notificar si chat no está abierto
            String fromNorm = msg.fromAddress.trim().toLowerCase(Locale.ROOT);
            String curr     = AppConfig.getCurrentChat();
            if (curr != null) curr = curr.trim().toLowerCase(Locale.ROOT);
            if (curr == null || !curr.equals(fromNorm)) {
                NotificationCompat.Builder nb = new NotificationCompat.Builder(this, CHANNEL_NEWMSG)
                        .setContentTitle("Mensaje de " + msg.fromAddress)
                        .setContentText(msg.type.equals("text") ? msg.body : "Audio recibido")
                        .setSmallIcon(R.drawable.ic_notification)
                        .setAutoCancel(true)
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT);

                if (Build.VERSION.SDK_INT < 33 ||
                        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                                == PackageManager.PERMISSION_GRANTED) {
                    NotificationManagerCompat.from(this)
                            .notify((int) msg.timestamp, nb.build());
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "handleMessage error", e);
        }
    }

    @Override public IBinder onBind(Intent intent) {
        return null;
    }

    @Override public void onDestroy() {
        super.onDestroy();
        running = false;
    }
}
