package cu.lenier.nextchat.exceptions;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

public class ExceptionHandler implements Thread.UncaughtExceptionHandler {
    private final Context context;
    private final Thread.UncaughtExceptionHandler defaultHandler;

    public ExceptionHandler(Context context) {
        this.context = context.getApplicationContext();
        this.defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
    }

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        Log.e("NextChatCrash", "App crash detectado", e);

        // GUARDAR EL ERROR EN UN ARCHIVO crash_log.txt
        try {
            File log = new File(context.getFilesDir(), "crash_log.txt");
            PrintWriter pw = new PrintWriter(new FileWriter(log));
            e.printStackTrace(pw);
            pw.close();
        } catch (IOException ioException) {
            ioException.printStackTrace();
        }

        // Mostrar mensaje opcional (NO SIEMPRE FUNCIONA porque app se cierra muy rápido)
        Toast.makeText(context, "Ocurrió un error inesperado", Toast.LENGTH_SHORT).show();

        // Terminar la app correctamente o delegar al handler por defecto
        if (defaultHandler != null) {
            defaultHandler.uncaughtException(t, e);
        } else {
            android.os.Process.killProcess(android.os.Process.myPid());
            System.exit(1);
        }
    }
}