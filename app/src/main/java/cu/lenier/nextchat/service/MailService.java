package cu.lenier.nextchat.service;

import android.app.Service;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.util.Log;

import com.sun.mail.imap.IMAPFolder;

import java.util.Properties;

import javax.mail.Folder;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.event.MessageCountAdapter;
import javax.mail.event.MessageCountEvent;

import cu.lenier.nextchat.data.AppDatabase;
import cu.lenier.nextchat.data.MessageDao;
import cu.lenier.nextchat.model.Message;
import cu.lenier.nextchat.util.CryptoHelper;

public class MailService extends Service {
    private static final String TAG     = "MailService";
    private static final String SUBJECT = "NextChat";

    private Session session;
    private MessageDao dao;

    @Override public void onCreate() {
        super.onCreate();
        dao = AppDatabase.getInstance(this).messageDao();

        Properties props = new Properties();
        props.put("mail.imap.host", "imap.nauta.cu");
        props.put("mail.imap.port", "143");
        props.put("mail.imap.ssl.enable", "false");
        session = Session.getInstance(props);
        session.setDebug(true);

        new Thread(this::pollLoop).start();
    }

    private void pollLoop() {
        try {
            SharedPreferences prefs = getSharedPreferences("prefs", MODE_PRIVATE);
            String email = prefs.getString("email", "");
            String pass  = prefs.getString("pass", "");

            Store store = session.getStore("imap");
            store.connect("imap.nauta.cu", 143, email, pass);

            IMAPFolder inbox = (IMAPFolder) store.getFolder("INBOX");
            inbox.open(Folder.READ_WRITE);
            inbox.addMessageCountListener(new MessageCountAdapter() {
                @Override public void messagesAdded(MessageCountEvent ev) {
                    for (javax.mail.Message m : ev.getMessages()) {
                        handleIncoming(m);
                    }
                }
            });

            while (!Thread.currentThread().isInterrupted()) {
                inbox.getMessageCount();
                Thread.sleep(5000);
            }
        } catch (Exception e) {
            Log.e(TAG, "pollLoop error", e);
        }
    }

    private void handleIncoming(javax.mail.Message m) {
        try {
            // 1) Filtrar asunto
            if (!SUBJECT.equals(m.getSubject())) {
                Log.d(TAG, "Ignorado asunto=" + m.getSubject());
                return;
            }

            // 2) Obtener y descifrar
            String cipher = m.getContent().toString();
            String plain  = CryptoHelper.decrypt(cipher);

            SharedPreferences prefs = getSharedPreferences("prefs", MODE_PRIVATE);
            String me = prefs.getString("email", "");

            // 3) Guardar en claro
            Message msg = new Message();
            msg.fromAddress = m.getFrom()[0].toString();
            msg.toAddress   = me;
            msg.subject     = SUBJECT;
            msg.body        = plain;
            msg.timestamp   = m.getReceivedDate().getTime();
            msg.sent        = false;
            msg.read        = false;
            dao.insert(msg);

            Log.d(TAG, "NextChat entrante de " + msg.fromAddress);
        } catch (Exception e) {
            Log.e(TAG, "Error descifrando/guardando", e);
        }
    }

    @Override public IBinder onBind(android.content.Intent intent) {
        return null;
    }
}
