package br.com.florestalrs.synccampo;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class BackgroundSyncWorker extends Worker {

    private static final String TAG = "SyncCampoWorker";
    private static final String CHANNEL_ID = "rs_florestal_sync_channel";

    public BackgroundSyncWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    private static String sanitizeFileName(String name) {
        return MainActivity.sanitizeFileName(name);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        Log.d(TAG, "Iniciando sincronização nativa em segundo plano no celular...");

        // 1. Obtém a URL do servidor configurada pelo usuário ou o padrão
        SharedPreferences prefs = context.getSharedPreferences("sync_campo_prefs", Context.MODE_PRIVATE);
        if (prefs.getBoolean("fg_sync_active", false)) {
            Log.d(TAG, "Sincronização em primeiro plano já ativa na tela. Ignorando worker de segundo plano.");
            prefs.edit().putBoolean("bg_sync_active", false).apply();
            return Result.success();
        }
        String serverUrl = prefs.getString("server_url", "").trim();

        if (serverUrl.isEmpty() || serverUrl.contains("trycloudflare.com") || serverUrl.contains("teste.florestalrs.com.br") || serverUrl.contains("192.168.") || serverUrl.contains("10.0.")) {
            serverUrl = "https://sst.florestalrs.com.br";
            prefs.edit().putString("server_url", serverUrl).commit();
        }

        if (serverUrl.endsWith("/")) {
            serverUrl = serverUrl.substring(0, serverUrl.length() - 1);
        }

        try {
            // 2. Consulta a lista de arquivos da matriz
            URL listUrl = new URL(serverUrl + "/api/files");
            HttpURLConnection conn = (HttpURLConnection) listUrl.openConnection();
            conn.setRequestProperty(MainActivity.AUTH_TOKEN_HEADER, MainActivity.AUTH_TOKEN_VALUE);
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(25000);

            if (conn.getResponseCode() != 200) {
                Log.w(TAG, "Servidor retornou status " + conn.getResponseCode());
                return Result.retry();
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();

            String jsonString = response.toString();
            JSONObject json = new JSONObject(jsonString);
            JSONArray files = json.optJSONArray("files");
            if (files == null || files.length() == 0) {
                Log.d(TAG, "Nenhum arquivo listado no Google Drive.");
                prefs.edit().putLong("last_sync_ts", System.currentTimeMillis()).putString("last_sync_source", "auto").commit();
                return Result.success();
            }

            // Salva imediatamente o catálogo atualizado (de forma síncrona com .commit() + arquivo físico redundante)
            prefs.edit().putString("cached_files_json", jsonString).commit();
            MainActivity.saveCatalogBackupFile(context, jsonString);

            File storageDir = new File(context.getFilesDir(), "campo_docs");
            if (!storageDir.exists()) {
                storageDir.mkdirs();
            }

            // Primeiro identifica a lista exata de arquivos novos ou atualizados que precisam de download
            java.util.List<JSONObject> pendingList = new java.util.ArrayList<>();
            for (int i = 0; i < files.length(); i++) {
                try {
                    JSONObject fileObj = files.getJSONObject(i);
                    String fileId = fileObj.optString("id", "");
                    String fileName = fileObj.optString("name", "documento");
                    long size = fileObj.optLong("size", 0);
                    String versionKey = fileObj.optString("checksum", "") + "_" + fileObj.optString("modifiedTime", "");
                    boolean isFolder = fileObj.optBoolean("is_folder", false)
                            || "application/vnd.google-apps.folder".equals(fileObj.optString("mimeType"));

                    if (isFolder || fileId.isEmpty()) {
                        continue;
                    }

                    File localFile = new File(storageDir, fileId + "_" + sanitizeFileName(fileName));
                    String savedVersion = prefs.getString("ver_" + fileId, "");
                    long savedSize = prefs.getLong("size_" + fileId, -1L);

                    boolean sizeMatches = (savedSize > 0) ? (localFile.length() == savedSize) : (size <= 0 || localFile.length() == size);
                    boolean versionMatches = savedVersion.isEmpty() || savedVersion.equals(versionKey);
                    if (localFile.exists() && localFile.length() > 0 && sizeMatches && versionMatches) {
                        if (savedVersion.isEmpty() || savedSize <= 0) {
                            prefs.edit()
                                    .putString("ver_" + fileId, versionKey)
                                    .putLong("size_" + fileId, localFile.length())
                                    .apply();
                        }
                        continue;
                    }
                    pendingList.add(fileObj);
                } catch (Exception ignored) {}
            }

            int totalPending = pendingList.size();
            int downloadedCount = 0;

            if (totalPending == 0) {
                prefs.edit()
                        .putBoolean("bg_sync_active", false)
                        .putLong("last_sync_ts", System.currentTimeMillis())
                        .putString("last_sync_source", "auto")
                        .commit();
                return Result.success();
            }

            prefs.edit()
                    .putBoolean("bg_sync_active", true)
                    .putInt("bg_sync_done", 0)
                    .putInt("bg_sync_total", totalPending)
                    .putString("bg_sync_current_file", "Iniciando transferência...")
                    .commit();

            // 3. Baixa os arquivos pendentes atualizando o progresso em tempo real para a UI
            for (int idx = 0; idx < totalPending; idx++) {
                if (isStopped() || prefs.getBoolean("fg_sync_active", false)) {
                    prefs.edit().putBoolean("bg_sync_active", false).commit();
                    return Result.success();
                }
                try {
                    JSONObject fileObj = pendingList.get(idx);
                    String fileId = fileObj.optString("id", "");
                    String fileName = fileObj.optString("name", "documento");
                    String versionKey = fileObj.optString("checksum", "") + "_" + fileObj.optString("modifiedTime", "");

                    prefs.edit()
                            .putBoolean("bg_sync_active", true)
                            .putInt("bg_sync_done", idx)
                            .putInt("bg_sync_total", totalPending)
                            .putString("bg_sync_current_file", fileName)
                            .apply();

                    File localFile = new File(storageDir, fileId + "_" + sanitizeFileName(fileName));
                    File tempFile = new File(storageDir, localFile.getName() + ".bg.part");
                    try {
                        Log.d(TAG, "Baixando em background: " + fileName);
                        URL downloadUrl = new URL(serverUrl + "/api/files/" + fileId + "/download");
                        HttpURLConnection dlConn = (HttpURLConnection) downloadUrl.openConnection();
                        dlConn.setRequestProperty(MainActivity.AUTH_TOKEN_HEADER, MainActivity.AUTH_TOKEN_VALUE);
                        dlConn.setConnectTimeout(15000);
                        dlConn.setReadTimeout(45000);

                        if (dlConn.getResponseCode() == 200) {
                            long expectedLen = dlConn.getContentLengthLong();
                            InputStream in = dlConn.getInputStream();
                            FileOutputStream out = new FileOutputStream(tempFile);
                            byte[] buffer = new byte[32768];
                            int bytesRead;
                            boolean aborted = false;
                            while ((bytesRead = in.read(buffer)) != -1) {
                                if (isStopped() || prefs.getBoolean("fg_sync_active", false)) {
                                    aborted = true;
                                    break;
                                }
                                out.write(buffer, 0, bytesRead);
                            }
                            out.flush();
                            out.close();
                            in.close();

                            if (aborted) {
                                if (tempFile.exists()) tempFile.delete();
                                prefs.edit().putBoolean("bg_sync_active", false).commit();
                                return Result.success();
                            }

                            boolean lengthValid = tempFile.exists() && tempFile.length() > 0 && (expectedLen <= 0 || tempFile.length() == expectedLen);
                            if (lengthValid) {
                                if (localFile.exists()) localFile.delete();
                                if (tempFile.renameTo(localFile)) {
                                    prefs.edit()
                                            .putString("ver_" + fileId, versionKey)
                                            .putLong("size_" + fileId, localFile.length())
                                            .putInt("bg_sync_done", idx + 1)
                                            .apply();
                                    downloadedCount++;
                                }
                            }
                        }
                    } catch (Exception dlErr) {
                        Log.e(TAG, "Falha ao baixar arquivo individual " + fileName + ": " + dlErr.getMessage());
                    } finally {
                        if (tempFile.exists()) {
                            tempFile.delete();
                        }
                    }
                } catch (Exception itemErr) {
                    Log.e(TAG, "Erro ao processar item do JSON: " + itemErr.getMessage());
                }
            }

            // Garante persistência síncrona do catálogo no final
            prefs.edit()
                    .putBoolean("bg_sync_active", false)
                    .putString("cached_files_json", jsonString)
                    .putLong("last_sync_ts", System.currentTimeMillis())
                    .putString("last_sync_source", "auto")
                    .commit();
            MainActivity.saveCatalogBackupFile(context, jsonString);

            Log.i(TAG, "Sincronização concluída com sucesso! Novos arquivos: " + downloadedCount);

            // 4. Notifica o operador se houver novidades baixadas
            if (downloadedCount > 0) {
                showNotification(context, downloadedCount);
            }

            return Result.success();

        } catch (Exception e) {
            prefs.edit().putBoolean("bg_sync_active", false).apply();
            Log.e(TAG, "Erro na sincronização de background: " + e.getMessage());
            return Result.retry();
        }
    }

    private void showNotification(Context context, int count) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Sincronização Documentos SST",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription("Notificações de sincronização de documentos SST - RS Florestal");
            manager.createNotificationChannel(channel);
        }

        android.content.Intent intent = new android.content.Intent(context, MainActivity.class);
        intent.setFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP | android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP);
        android.app.PendingIntent pendingIntent = android.app.PendingIntent.getActivity(
                context,
                0,
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? android.app.PendingIntent.FLAG_IMMUTABLE : 0)
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("Documentos SST - Atualizado")
                .setContentText(count + " novo(s) documento(s) sincronizado(s) e prontos para o campo.")
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true);

        manager.notify(1001, builder.build());
    }
}

