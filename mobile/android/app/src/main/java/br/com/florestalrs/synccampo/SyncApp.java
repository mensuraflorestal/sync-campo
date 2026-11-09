package br.com.florestalrs.synccampo;

import android.app.Application;
import android.content.Context;
import android.util.Log;

import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

public class SyncApp extends Application {

    private static final String TAG = "SyncApp";
    private static final String UNIQUE_WORK_NAME = "RSFlorestalBackgroundSync";
    private static final String UNIQUE_IMMEDIATE_WORK_NAME = "RSFlorestalImmediateSync";

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "Inicializando RS Florestal SyncApp e agendando WorkManager...");
        scheduleBackgroundSync(this);
    }

    public static void scheduleBackgroundSync(Context context) {
        try {
            // Exige conexao de rede ativa
            Constraints constraints = new Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build();

            // Calcula o atraso inicial exato ate as 23:00 de hoje (ou de amanha se ja passou das 23h)
            java.util.Calendar now = java.util.Calendar.getInstance();
            java.util.Calendar target = java.util.Calendar.getInstance();
            target.set(java.util.Calendar.HOUR_OF_DAY, 23);
            target.set(java.util.Calendar.MINUTE, 0);
            target.set(java.util.Calendar.SECOND, 0);
            target.set(java.util.Calendar.MILLISECOND, 0);

            if (now.after(target)) {
                target.add(java.util.Calendar.DAY_OF_MONTH, 1);
            }

            long initialDelayMs = target.getTimeInMillis() - now.getTimeInMillis();
            long initialDelayMinutes = Math.max(1, initialDelayMs / (1000 * 60));

            // Sincronizacao noturna diaria automatica (a cada 24 horas, disparando as 23:00)
            PeriodicWorkRequest syncWorkRequest = new PeriodicWorkRequest.Builder(
                    BackgroundSyncWorker.class,
                    24, TimeUnit.HOURS
            )
                    .setInitialDelay(initialDelayMinutes, TimeUnit.MINUTES)
                    .setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.LINEAR, 15, TimeUnit.MINUTES)
                    .build();

            WorkManager.getInstance(context.getApplicationContext()).enqueueUniquePeriodicWork(
                    UNIQUE_WORK_NAME,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    syncWorkRequest
            );

            Log.i(TAG, "WorkManager diario noturno (23:00) agendado com sucesso! Atraso inicial: " + initialDelayMinutes + " minutos.");
        } catch (Exception e) {
            Log.e(TAG, "Erro ao agendar WorkManager: " + e.getMessage());
        }
    }

    public static void triggerImmediateBackgroundSync(Context context) {
        try {
            Constraints constraints = new Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build();

            OneTimeWorkRequest immediateRequest = new OneTimeWorkRequest.Builder(BackgroundSyncWorker.class)
                    .setConstraints(constraints)
                    .build();

            WorkManager.getInstance(context.getApplicationContext()).enqueueUniqueWork(
                    UNIQUE_IMMEDIATE_WORK_NAME,
                    ExistingWorkPolicy.REPLACE,
                    immediateRequest
            );
        } catch (Exception e) {
            Log.e(TAG, "Erro ao disparar sync imediato: " + e.getMessage());
        }
    }

    public static void cancelImmediateBackgroundSync(Context context) {
        try {
            WorkManager.getInstance(context.getApplicationContext()).cancelUniqueWork(UNIQUE_IMMEDIATE_WORK_NAME);
        } catch (Exception ignored) {}
    }
}

