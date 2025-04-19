package cu.lenier.nextchat.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.Properties;

import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import cu.lenier.nextchat.data.AppDatabase;
import cu.lenier.nextchat.model.Message;

public class MailHelper {
    private static final String TAG     = "MailHelper";
    private static final String SUBJECT = "NextChat";

    public static void sendEmail(Context ctx, String to, String plainBody) {
        final String finalTo   = to.trim();
        final String finalBody = plainBody.trim();

        new Thread(() -> {
            try {
                // 1) Cifrar
                String encrypted = CryptoHelper.encrypt(finalBody);

                SharedPreferences prefs = ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE);
                String email = prefs.getString("email", "");
                String pass  = prefs.getString("pass", "");
                if (email.isEmpty() || pass.isEmpty()) return;

                // 2) SMTP
                Properties props = new Properties();
                props.put("mail.smtp.host", "smtp.nauta.cu");
                props.put("mail.smtp.port", "25");
                props.put("mail.smtp.auth", "true");
                props.put("mail.smtp.starttls.enable", "false");

                Session session = Session.getInstance(props);
                MimeMessage msg  = new MimeMessage(session);
                msg.setFrom(new InternetAddress(email));
                msg.setRecipients(MimeMessage.RecipientType.TO,
                        InternetAddress.parse(finalTo));
                msg.setSubject(SUBJECT);
                msg.setText(encrypted);

                Transport t = session.getTransport("smtp");
                t.connect("smtp.nauta.cu", 25, email, pass);
                t.sendMessage(msg, msg.getAllRecipients());
                t.close();

                // 3) Guardar local sin cifrar
                Message m = new Message();
                m.fromAddress = email;
                m.toAddress   = finalTo;
                m.subject     = SUBJECT;
                m.body        = finalBody;
                m.timestamp   = System.currentTimeMillis();
                m.sent        = true;
                m.read        = true;
                AppDatabase.getInstance(ctx).messageDao().insert(m);

                Log.d(TAG, "NextChat enviado a " + finalTo);
            } catch (Exception e) {
                Log.e(TAG, "Error enviando NextChat", e);
            }
        }).start();
    }
}
