package cu.lenier.nextchat.service;

import android.app.Service;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.util.Log;

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

    private MessageDao dao;
    private Session    session;

    @Override public void onCreate() {
        super.onCreate();
        dao = AppDatabase.getInstance(this).messageDao();
        Properties props = new Properties();
        props.put("mail.imap.host","imap.nauta.cu");
        props.put("mail.imap.port","143");
        props.put("mail.imap.ssl.enable","false");
        session = Session.getInstance(props);
        new Thread(this::pollLoop).start();
    }

    private void pollLoop() {
        try {
            SharedPreferences p = getSharedPreferences("prefs", MODE_PRIVATE);
            String email = p.getString("email",""), pass = p.getString("pass","");

            Store store = session.getStore("imap");
            store.connect("imap.nauta.cu",143,email,pass);

            IMAPFolder inbox = (IMAPFolder)store.getFolder("INBOX");
            inbox.open(Folder.READ_WRITE);
            inbox.addMessageCountListener(new MessageCountAdapter(){
                @Override public void messagesAdded(MessageCountEvent ev){
                    for (javax.mail.Message m : ev.getMessages()) {
                        handleIncoming(m);
                    }
                }
            });

            while(true) {
                inbox.getMessageCount();
                Thread.sleep(5000);
            }
        } catch(Exception e){
            Log.e(TAG,"pollLoop",e);
        }
    }

    private void handleIncoming(javax.mail.Message m) {
        try {
            String subj = m.getSubject();
            if (!TXT_SUBJ.equals(subj) && !AUD_SUBJ.equals(subj)) return;

            SharedPreferences p = getSharedPreferences("prefs", MODE_PRIVATE);
            String me = p.getString("email","");

            Message msg = new Message();
            msg.fromAddress = m.getFrom()[0].toString();
            msg.toAddress   = me;
            msg.subject     = subj;
            msg.read        = false;
            msg.sent        = false;
            msg.timestamp   = m.getReceivedDate().getTime();

            Object cnt = m.getContent();
            if (TXT_SUBJ.equals(subj) && !(cnt instanceof javax.mail.Multipart)) {
                String cipher = cnt.toString();
                msg.body           = CryptoHelper.decrypt(cipher);
                msg.attachmentPath = null;
                msg.type           = "text";

            } else if (AUD_SUBJ.equals(subj) && cnt instanceof javax.mail.Multipart) {
                javax.mail.Multipart mp = (javax.mail.Multipart)cnt;
                msg.body = "";
                msg.type = "audio";
                for (int i=0;i<mp.getCount();i++){
                    Part part = mp.getBodyPart(i);
                    if (Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition())) {
                        File dir = new File(getExternalFilesDir(null),"audios_recibidos");
                        if (!dir.exists()) dir.mkdirs();
                        File enc = new File(dir, System.currentTimeMillis()+"_"+part.getFileName());
                        try (InputStream is = part.getInputStream();
                             FileOutputStream fos = new FileOutputStream(enc)) {
                            byte[] buf=new byte[4096];int r;
                            while((r=is.read(buf))>0) fos.write(buf,0,r);
                        }
                        File dec = new File(dir, enc.getName().replace(".enc",".3gp"));
                        CryptoHelper.decryptAudio(enc,dec);
                        enc.delete();
                        msg.attachmentPath = dec.getAbsolutePath();
                        break;
                    }
                }
            }

            dao.insert(msg);
            Log.d(TAG,"Incoming "+msg.type+" from "+msg.fromAddress);

        } catch(Exception e){
            Log.e(TAG,"handleIncoming",e);
        }
    }

    @Override public IBinder onBind(android.content.Intent intent) {
        return null;
    }
}
