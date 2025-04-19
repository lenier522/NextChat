package cu.lenier.nextchat.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.sun.mail.imap.IMAPFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Properties;

import javax.mail.Folder;
import javax.mail.Part;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.event.MessageCountAdapter;
import javax.mail.event.MessageCountEvent;

import cu.lenier.nextchat.data.AppDatabase;
import cu.lenier.nextchat.data.MessageDao;
import cu.lenier.nextchat.model.Message;
import cu.lenier.nextchat.util.CryptoHelper;

public class MailService extends Service {
    private static final String TAG      = "MailService";
    private static final String TXT_SUBJ = "NextChat";
    private static final String AUD_SUBJ = "NextChat Audio";
    private static final String CHAN_ID  = "MailSyncChannel";
    private static final int    NOTIF_ID = 1;

    private MessageDao dao;
    private Session    session;
    private boolean    pollingStarted = false;

    @Override
    public void onCreate() {
        super.onCreate();
        dao = AppDatabase.getInstance(this).messageDao();

        // Configuración IMAP
        Properties props = new Properties();
        props.put("mail.imap.host", "imap.nauta.cu");
        props.put("mail.imap.port", "143");
        props.put("mail.imap.ssl.enable", "false");
        session = Session.getInstance(props);

        // Canal de notificación (Android O+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel chan = new NotificationChannel(
                    CHAN_ID, "NextChat Sync", NotificationManager.IMPORTANCE_LOW
            );
            chan.setDescription("Sincronización de correo IMAP");
            ((NotificationManager)getSystemService(NOTIFICATION_SERVICE))
                    .createNotificationChannel(chan);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Crear y mostrar notificación en primer plano
        Notification notif = new NotificationCompat.Builder(this, CHAN_ID)
                .setContentTitle("NextChat")
                .setContentText("Sincronizando mensajes…")
                .setSmallIcon(android.R.drawable.ic_popup_sync)
                .setOngoing(true)
                .build();
        startForeground(NOTIF_ID, notif);

        // Iniciar polling sólo una vez
        if (!pollingStarted) {
            pollingStarted = true;
            new Thread(this::pollLoop).start();
        }

        return START_STICKY;
    }

    private void pollLoop() {
        try {
            String email = getSharedPreferences("prefs", MODE_PRIVATE)
                    .getString("email", "");
            String pass  = getSharedPreferences("prefs", MODE_PRIVATE)
                    .getString("pass", "");

            Store store = session.getStore("imap");
            store.connect("imap.nauta.cu", 143, email, pass);

            IMAPFolder inbox = (IMAPFolder) store.getFolder("INBOX");
            inbox.open(Folder.READ_WRITE);
            inbox.addMessageCountListener(new MessageCountAdapter() {
                @Override
                public void messagesAdded(MessageCountEvent ev) {
                    for (javax.mail.Message m : ev.getMessages()) {
                        handleIncoming(m);
                    }
                }
            });

            while (true) {
                inbox.getMessageCount();
                Thread.sleep(5000);
            }
        } catch (Exception e) {
            Log.e(TAG, "pollLoop error", e);
        }
    }

    private void handleIncoming(javax.mail.Message m) {
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
            msg.read           = false;
            msg.sent           = false;

            Object content = m.getContent();
            if (TXT_SUBJ.equals(subj) && !(content instanceof javax.mail.Multipart)) {
                msg.body           = CryptoHelper.decrypt(content.toString());
                msg.attachmentPath = null;
                msg.type           = "text";

            } else if (AUD_SUBJ.equals(subj) && content instanceof javax.mail.Multipart) {
                javax.mail.Multipart mp = (javax.mail.Multipart) content;
                msg.body = "";
                msg.type = "audio";
                for (int i = 0; i < mp.getCount(); i++) {
                    Part part = mp.getBodyPart(i);
                    if (Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition())) {
                        File dir = new File(getExternalFilesDir(null), "audios_recibidos");
                        if (!dir.exists()) dir.mkdirs();
                        File enc = new File(dir, System.currentTimeMillis() + "_" + part.getFileName());
                        try (InputStream is = part.getInputStream();
                             FileOutputStream fos = new FileOutputStream(enc)) {
                            byte[] buf = new byte[4096];
                            int r;
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

            dao.insert(msg);
            Log.d(TAG, "Incoming " + msg.type + " from " + msg.fromAddress);

        } catch (Exception e) {
            Log.e(TAG, "handleIncoming error", e);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
