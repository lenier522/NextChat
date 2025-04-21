package cu.lenier.nextchat.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

import java.io.File;
import java.util.Properties;
import java.util.concurrent.Executors;

import javax.activation.DataHandler;
import javax.activation.FileDataSource;
import javax.mail.Message.RecipientType;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

import cu.lenier.nextchat.data.AppDatabase;
import cu.lenier.nextchat.data.MessageDao;
import cu.lenier.nextchat.model.Message;

public class MailHelper {
    private static final String TXT_SUBJ = "NextChat";
    private static final String AUD_SUBJ = "NextChat Audio";

    public static void sendEmail(Context ctx, Message m) {
        // Ejecutamos TODO en background
        Executors.newSingleThreadExecutor().execute(() -> {
            MessageDao dao = AppDatabase.getInstance(ctx).messageDao();

            // 1) Insert inicial
            long id = dao.insert(m);
            m.id = (int) id;

            // 2) Comprobar red
            if (!hasNetwork(ctx)) {
                m.sendState = Message.STATE_FAILED;
                dao.update(m);
                return;
            }

            // 3) Intentar envío SMTP
            try {
                String cipher = CryptoHelper.encrypt(m.body);
                Properties props = new Properties();
                props.put("mail.smtp.host", "smtp.nauta.cu");
                props.put("mail.smtp.port", "25");
                props.put("mail.smtp.auth", "true");
                props.put("mail.smtp.starttls.enable", "false");
                Session session = Session.getInstance(props);

                MimeMessage msg = new MimeMessage(session);
                SharedPreferences prefs = ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE);
                String email = prefs.getString("email", "");
                String pass  = prefs.getString("pass", "");
                msg.setFrom(new InternetAddress(email));
                msg.setRecipient(RecipientType.TO, new InternetAddress(m.toAddress));
                msg.setSubject(TXT_SUBJ);
                msg.setText(cipher);

                Transport tr = session.getTransport("smtp");
                tr.connect("smtp.nauta.cu", 25, email, pass);
                tr.sendMessage(msg, msg.getAllRecipients());
                tr.close();

                m.sendState = Message.STATE_SENT;
            } catch (Exception e) {
                Log.e("MailHelper", "Error enviando texto", e);
                m.sendState = Message.STATE_FAILED;
            }

            // 4) Actualizar estado en la BD
            dao.update(m);
        });
    }

    public static void sendAudioEmail(Context ctx, Message m) {
        Executors.newSingleThreadExecutor().execute(() -> {
            MessageDao dao = AppDatabase.getInstance(ctx).messageDao();

            // 1) Insert inicial
            long id = dao.insert(m);
            m.id = (int) id;

            // 2) Comprobar red
            if (!hasNetwork(ctx)) {
                m.sendState = Message.STATE_FAILED;
                dao.update(m);
                return;
            }

            // 3) Envío SMTP con adjunto
            try {
                File in  = new File(m.attachmentPath);
                File tmp = File.createTempFile("aud_enc", ".tmp", ctx.getCacheDir());
                CryptoHelper.encryptAudio(in, tmp);

                Properties props = new Properties();
                props.put("mail.smtp.host", "smtp.nauta.cu");
                props.put("mail.smtp.port", "25");
                props.put("mail.smtp.auth", "true");
                props.put("mail.smtp.starttls.enable", "false");
                Session session = Session.getInstance(props);

                MimeMessage msg = new MimeMessage(session);
                SharedPreferences prefs = ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE);
                String email = prefs.getString("email", "");
                String pass  = prefs.getString("pass", "");
                msg.setFrom(new InternetAddress(email));
                msg.setRecipient(RecipientType.TO, new InternetAddress(m.toAddress));
                msg.setSubject(AUD_SUBJ);

                MimeBodyPart textPart = new MimeBodyPart();
                textPart.setText("[Audio]");
                MimeBodyPart filePart = new MimeBodyPart();
                filePart.setDataHandler(new DataHandler(new FileDataSource(tmp)));
                filePart.setFileName("audio.enc");
                Multipart mp = new MimeMultipart();
                mp.addBodyPart(textPart);
                mp.addBodyPart(filePart);
                msg.setContent(mp);

                Transport tr = session.getTransport("smtp");
                tr.connect("smtp.nauta.cu", 25, email, pass);
                tr.sendMessage(msg, msg.getAllRecipients());
                tr.close();
                tmp.delete();

                m.sendState = Message.STATE_SENT;
            } catch (Exception e) {
                Log.e("MailHelper", "Error enviando audio", e);
                m.sendState = Message.STATE_FAILED;
            }

            // 4) Actualizar estado en la BD
            dao.update(m);
        });
    }

    private static boolean hasNetwork(Context ctx) {
        ConnectivityManager cm = (ConnectivityManager)
                ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo ni = cm.getActiveNetworkInfo();
        return ni != null && ni.isConnected();
    }
}
