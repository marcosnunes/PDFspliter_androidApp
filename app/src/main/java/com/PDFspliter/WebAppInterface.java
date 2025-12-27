package com.PDFspliter;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.os.Environment;
import android.util.Base64;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;

public class WebAppInterface {
    private final Context mContext;
    private final Activity activity;

    WebAppInterface(Context c) {
        mContext = c;
        this.activity = (Activity) c;
    }

    @JavascriptInterface
    public void downloadPdf(String base64Data, String fileName) {
        try {
            // Garante que o nome do arquivo seja seguro para o sistema de arquivos
            String safeFileName = fileName.replaceAll("[^a-zA-Z0-9._-]", "_");

            byte[] pdfAsBytes = Base64.decode(base64Data, Base64.NO_WRAP);

            File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (!downloadsDir.exists()) {
                if (!downloadsDir.mkdirs()) {
                    Log.e("WebAppInterface", "Não foi possível criar o diretório de downloads.");
                    return;
                }
            }

            File file = new File(downloadsDir, safeFileName);

            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(pdfAsBytes);
            }

            DownloadManager downloadManager = (DownloadManager) mContext.getSystemService(Context.DOWNLOAD_SERVICE);
            if (downloadManager != null) {
                downloadManager.addCompletedDownload(
                        file.getName(),
                        "PDF Processado",
                        true,
                        "application/pdf",
                        file.getAbsolutePath(),
                        file.length(),
                        true
                );
            }

            Log.i("WebAppInterface", "Download concluído: " + safeFileName);
            activity.runOnUiThread(() -> Toast.makeText(activity, "Salvo em Downloads: " + safeFileName, Toast.LENGTH_LONG).show());

        } catch (Exception e) {
            Log.e("WebAppInterface", "Erro no download do PDF", e);
            activity.runOnUiThread(() -> Toast.makeText(activity, "Falha no download.", Toast.LENGTH_SHORT).show());
        }
    }
}
