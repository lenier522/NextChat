package cu.lenier.nextchat.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.io.File;
import java.util.Properties;

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
import cu.lenier.nextchat.model.Message;

public class MailHelper {
    private static final String TAG      = "MailHelper";
    private static final String TXT_SUBJ = "NextChat";
    private static final String AUD_SUBJ = "NextChat Audio";

    public static void sendEmail(Context ctx, String to, String plainBody) {
        new Thread(() -> {
            try {
                SharedPreferences p = ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE);
                String email = p.getString("email",""), pass = p.getString("pass","");
                if (email.isEmpty()||pass.isEmpty()) return;

                String cipher = CryptoHelper.encrypt(plainBody);

                Properties props = new Properties();
                props.put("mail.smtp.host","smtp.nauta.cu");
                props.put("mail.smtp.port","25");
                props.put("mail.smtp.auth","true");
                props.put("mail.smtp.starttls.enable","false");

                Session s = Session.getInstance(props);
                MimeMessage msg = new MimeMessage(s);
                msg.setFrom(new InternetAddress(email));
                msg.setRecipient(RecipientType.TO,new InternetAddress(to));
                msg.setSubject(TXT_SUBJ);
                msg.setText(cipher);

                Transport t = s.getTransport("smtp");
                t.connect("smtp.nauta.cu",25,email,pass);
                t.sendMessage(msg,msg.getAllRecipients());
                t.close();

                Message m = new Message();
                m.fromAddress = email;
                m.toAddress   = to;
                m.subject     = TXT_SUBJ;
                m.body        = plainBody;
                m.attachmentPath = null;
                m.timestamp   = System.currentTimeMillis();
                m.sent        = true;
                m.read        = true;
                m.type        = "text";
                AppDatabase.getInstance(ctx).messageDao().insert(m);

                Log.d(TAG,"Texto enviado");
            } catch(Exception e){
                Log.e(TAG,"Error enviar texto",e);
            }
        }).start();
    }

    public static void sendAudioEmail(Context ctx, String to, String audioPath) {
        new Thread(() -> {
            try {
                SharedPreferences p = ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE);
                String email = p.getString("email",""), pass = p.getString("pass","");
                if (email.isEmpty()||pass.isEmpty()) return;

                File in  = new File(audioPath);
                File tmp = File.createTempFile("aud_enc",".tmp",ctx.getCacheDir());
                CryptoHelper.encryptAudio(in,tmp);

                Properties props = new Properties();
                props.put("mail.smtp.host","smtp.nauta.cu");
                props.put("mail.smtp.port","25");
                props.put("mail.smtp.auth","true");
                props.put("mail.smtp.starttls.enable","false");

                Session s = Session.getInstance(props);
                MimeMessage msg = new MimeMessage(s);
                msg.setFrom(new InternetAddress(email));
                msg.setRecipient(RecipientType.TO,new InternetAddress(to));
                msg.setSubject(AUD_SUBJ);

                MimeBodyPart text = new MimeBodyPart();
                text.setText("[Audio]");
                MimeBodyPart file = new MimeBodyPart();
                FileDataSource fds = new FileDataSource(tmp);
                file.setDataHandler(new DataHandler(fds));
                file.setFileName("audio.enc");

                Multipart mp = new MimeMultipart();
                mp.addBodyPart(text);
                mp.addBodyPart(file);
                msg.setContent(mp);

                Transport t = s.getTransport("smtp");
                t.connect("smtp.nauta.cu",25,email,pass);
                t.sendMessage(msg,msg.getAllRecipients());
                t.close();

                Message m = new Message();
                m.fromAddress = email;
                m.toAddress   = to;
                m.subject     = AUD_SUBJ;
                m.body        = "";
                m.attachmentPath = audioPath;
                m.timestamp   = System.currentTimeMillis();
                m.sent        = true;
                m.read        = true;
                m.type        = "audio";
                AppDatabase.getInstance(ctx).messageDao().insert(m);

                tmp.delete();
                Log.d(TAG,"Audio enviado");
            } catch(Exception e){
                Log.e(TAG,"Error enviar audio",e);
            }
        }).start();
    }
}
