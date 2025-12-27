package com.PDFspliter;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import org.json.JSONObject;

import java.io.OutputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class FullscreenActivity extends AppCompatActivity {

    private WebView webView;
    private String webUrl;
    private AlertDialog exitConfirmationDialog;
    private ValueCallback<Uri[]> uploadMessage;
    private ActivityResultLauncher<Intent> fileChooserLauncher;

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fullscreen);

        webUrl = getString(R.string.web_url); // Certifique-se que strings.xml tem este valor

        webView = findViewById(R.id.webview);
        SwipeRefreshLayout swipeRefreshLayout = findViewById(R.id.swipe_refresh_layout);
        swipeRefreshLayout.setEnabled(false);

        webView.setVisibility(View.GONE);

        // Configuração do Seletor de Arquivos
        fileChooserLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (uploadMessage == null) return;

                    Uri[] results = null;
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        Intent data = result.getData();
                        if (data != null) {
                            ClipData clipData = data.getClipData();
                            if (clipData != null) {
                                results = new Uri[clipData.getItemCount()];
                                for (int i = 0; i < clipData.getItemCount(); i++) {
                                    results[i] = clipData.getItemAt(i).getUri();
                                }
                            } else if (data.getData() != null) {
                                results = new Uri[]{data.getData()};
                            }
                        }
                    }

                    uploadMessage.onReceiveValue(results);
                    uploadMessage = null;
                });

        loadWebContent();

        // Botão voltar
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack();
                } else {
                    showExitConfirmationDialog();
                }
            }
        });
    }

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    private void loadWebContent() {
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                webView.setVisibility(View.VISIBLE);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return false;
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                if (uploadMessage != null) {
                    uploadMessage.onReceiveValue(null);
                }
                uploadMessage = filePathCallback;

                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);

                try {
                    fileChooserLauncher.launch(intent);
                } catch (Exception e) {
                    uploadMessage.onReceiveValue(null);
                    uploadMessage = null;
                    return false;
                }
                return true;
            }
        });

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);

        webView.addJavascriptInterface(new WebAppInterface(this, webView), "Android");

        webView.clearCache(true);
        webView.loadUrl(webUrl);
    }

    private void showExitConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Sair")
                .setMessage("Deseja sair do aplicativo?")
                .setPositiveButton("Sim", (dialog, i) -> finish())
                .setNegativeButton("Não", null)
                .show();
    }

    public static class WebAppInterface {
        private final Context mContext;
        private final Activity activity;
        private final WebView mWebView;

        WebAppInterface(Context c, WebView w) {
            mContext = c;
            activity = (Activity) c;
            mWebView = w;
        }

        @JavascriptInterface
        public void exitApp() {
            activity.finish();
        }

        private void setLocale(String language) {
            Locale locale = new Locale(language);
            Locale.setDefault(locale);
            Resources res = mContext.getResources();
            Configuration config = new Configuration(res.getConfiguration());
            config.setLocale(locale);
            res.updateConfiguration(config, res.getDisplayMetrics());
        }

        @JavascriptInterface
        public String getTranslations(String lang) {
            if (lang != null && !lang.isEmpty()) {
                setLocale(lang);
            }
            
            Resources res = mContext.getResources();
            Map<String, String> translations = new HashMap<>();
            int[] stringIds = {
                    R.string.app_name, R.string.title_activity_fullscreen, R.string.web_url, R.string.exit, R.string.exit_confirmation, R.string.yes, R.string.no, R.string.saved_in_downloads, R.string.error_creating_file, R.string.error,
                    R.string.home_title, R.string.menu_split, R.string.menu_merge, R.string.menu_split_only, R.string.menu_jpg_to_pdf, R.string.menu_pdf_to_jpg, R.string.menu_privacy, R.string.menu_language, R.string.main_title,
                    R.string.main_subtitle, R.string.select_pdf, R.string.no_file_selected, R.string.process_pdf, R.string.instructions, R.string.instructions_text, R.string.processed_pages, R.string.alert_no_valid_pdf,
                    R.string.alert_some_files_ignored, R.string.log_ready_to_process, R.string.file_selected_count, R.string.log_ready_to_process_select_files, R.string.log_processing_page, R.string.log_applying_ocr,
                    R.string.log_reading_file, R.string.log_generating_files, R.string.log_processing_complete, R.string.log_critical_error, R.string.log_loading_system, R.string.log_error_loading_script, R.string.btn_process_pdf_processing,
                    R.string.btn_download, R.string.btn_download_saving, R.string.btn_download_again, R.string.unknown_group_name, R.string.page_count_label, R.string.alert_processing_error, R.string.unir_pdf_title,
                    R.string.unir_pdf_main_title, R.string.unir_pdf_subtitle, R.string.unir_pdf_select_files, R.string.unir_pdf_merge_button, R.string.unir_pdf_instructions_text, R.string.unir_pdf_download_title,
                    R.string.unir_pdf_log_ready, R.string.unir_pdf_log_files_selected, R.string.unir_pdf_log_ready_to_merge, R.string.unir_pdf_log_merging, R.string.unir_pdf_log_processing_file, R.string.unir_pdf_log_file_embedded,
                    R.string.unir_pdf_log_error_processing_file, R.string.unir_pdf_log_creating_download, R.string.unir_pdf_log_ready_to_download, R.string.unir_pdf_log_error_saving, R.string.unir_pdf_log_error_merging,
                    R.string.unir_pdf_merged_filename, R.string.unir_pdf_btn_download, R.string.unir_pdf_alert_error_merging, R.string.dividir_apenas_title, R.string.dividir_apenas_subtitle, R.string.dividir_apenas_instructions_text,
                    R.string.dividir_apenas_log_error_reading_file, R.string.dividir_apenas_log_file_selected, R.string.dividir_apenas_log_ready_select_file, R.string.dividir_apenas_log_loading_libraries,
                    R.string.dividir_apenas_log_error_page, R.string.dividir_apenas_log_starting, R.string.dividir_apenas_log_file_read, R.string.dividir_apenas_log_pdf_loaded, R.string.dividir_apenas_log_generating_buttons,
                    R.string.dividir_apenas_log_page_fail, R.string.dividir_apenas_log_done, R.string.dividir_apenas_alert_lib_error, R.string.dividir_apenas_alert_select_file, R.string.dividir_apenas_alert_processing_error,
                    R.string.dividir_apenas_btn_page, R.string.dividir_apenas_btn_loading, R.string.dividir_apenas_lib_error_reload, R.string.jpg_to_pdf_title, R.string.jpg_to_pdf_main_title, R.string.jpg_to_pdf_subtitle,
                    R.string.jpg_to_pdf_select_files, R.string.jpg_to_pdf_generate_button, R.string.jpg_to_pdf_instructions_text, R.string.jpg_to_pdf_download_title, R.string.jpg_to_pdf_log_ready,
                    R.string.jpg_to_pdf_log_ready_to_generate, R.string.jpg_to_pdf_log_generating, R.string.jpg_to_pdf_log_processing_image, R.string.jpg_to_pdf_log_error_processing_image, R.string.jpg_to_pdf_log_creating_download,
                    R.string.jpg_to_pdf_log_ready_to_download, R.string.jpg_to_pdf_log_error_saving, R.string.jpg_to_pdf_log_error_generating, R.string.jpg_to_pdf_generated_filename, R.string.jpg_to_pdf_btn_download,
                    R.string.jpg_to_pdf_alert_error_generating, R.string.jpg_to_pdf_alert_no_valid_images, R.string.jpg_to_pdf_alert_some_files_ignored, R.string.jpg_to_pdf_log_images_selected, R.string.pdf_to_jpg_title,
                    R.string.pdf_to_jpg_main_title, R.string.pdf_to_jpg_subtitle, R.string.pdf_to_jpg_select_file, R.string.pdf_to_jpg_convert_button, R.string.pdf_to_jpg_instructions_text, R.string.pdf_to_jpg_download_title,
                    R.string.pdf_to_jpg_log_ready, R.string.pdf_to_jpg_log_ready_to_convert, R.string.pdf_to_jpg_log_converting, R.string.pdf_to_jpg_log_pdf_loaded, R.string.pdf_to_jpg_log_converting_page, R.string.pdf_to_jpg_log_conversion_complete,
                    R.string.pdf_to_jpg_log_error_reading_file, R.string.pdf_to_jpg_log_error_converting, R.string.pdf_to_jpg_btn_download_page, R.string.pdf_to_jpg_alert_invalid_file_type, R.string.pdf_to_jpg_alert_select_file,
                    R.string.pdf_to_jpg_alert_error_converting, R.string.privacy_policy_title, R.string.privacy_policy_main_title, R.string.privacy_policy_intro, R.string.privacy_policy_collected_title, R.string.privacy_policy_collected_text,
                    R.string.privacy_policy_permissions_title, R.string.privacy_policy_permissions_text, R.string.privacy_policy_permissions_storage, R.string.privacy_policy_usage_title, R.string.privacy_policy_usage_text,
                    R.string.privacy_policy_providers_title, R.string.privacy_policy_providers_text, R.string.privacy_policy_security_title, R.string.privacy_policy_security_text, R.string.privacy_policy_links_title,
                    R.string.privacy_policy_links_text, R.string.privacy_policy_children_title, R.string.privacy_policy_children_text, R.string.privacy_policy_changes_title, R.string.privacy_policy_changes_text,
                    R.string.privacy_policy_effective_date, R.string.privacy_policy_contact_title, R.string.privacy_policy_contact_text, R.string.language_picker_title, R.string.test_string
            };

            for (int id : stringIds) {
                String name = res.getResourceEntryName(id);
                String value = res.getString(id);
                translations.put(name, value);
            }
            return new JSONObject(translations).toString();
        }


        @JavascriptInterface
        public void performOCR(String base64Image, String callbackId) {
            try {
                byte[] decodedString = Base64.decode(base64Image, Base64.DEFAULT);
                Bitmap bitmap = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);

                if (bitmap == null) {
                    returnResultToJs(callbackId, "");
                    return;
                }

                InputImage image = InputImage.fromBitmap(bitmap, 0);
                TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

                recognizer.process(image)
                        .addOnSuccessListener(visionText -> {
                            String text = visionText.getText();
                            // MELHORIA DE SEGURANÇA PARA STRING JS:
                            // Escapar barras invertidas, aspas simples, aspas duplas e quebras de linha
                            String safeText = text.replace("\\", "\\\\")
                                    .replace("'", "\\'")
                                    .replace("\"", "\\\"")
                                    .replace("\n", "\\n")
                                    .replace("\r", "");

                            Log.d("OCR_DEBUG", "Texto extraído (inicio): " + (safeText.length() > 50 ? safeText.substring(0, 50) : safeText));

                            returnResultToJs(callbackId, safeText);
                        })
                        .addOnFailureListener(e -> {
                            Log.e("OCR", "Falha no ML Kit", e);
                            returnResultToJs(callbackId, "");
                        });

            } catch (Exception e) {
                Log.e("OCR", "Erro crítico", e);
                returnResultToJs(callbackId, "");
            }
        }

        private void returnResultToJs(String callbackId, String text) {
            activity.runOnUiThread(() -> mWebView.evaluateJavascript("javascript:onOcrResult('" + callbackId + "', '" + text + "')", null));
        }

        @JavascriptInterface
        public void downloadPdf(String base64Data, String fileName) {
            try {
                String safeFileName = fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
                byte[] pdfAsBytes = Base64.decode(base64Data, Base64.NO_WRAP);

                ContentValues contentValues = new ContentValues();
                contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, safeFileName);
                contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf");
                contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);

                ContentResolver resolver = mContext.getContentResolver();
                Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues);

                if (uri != null) {
                    try (OutputStream outputStream = resolver.openOutputStream(uri)) {
                        if (outputStream != null) {
                            outputStream.write(pdfAsBytes);
                            outputStream.flush();
                            showToast("Salvo em Downloads: " + safeFileName);
                            return;
                        }
                    }
                }
                showToast("Erro ao criar arquivo no dispositivo.");
            } catch (Exception e) {
                Log.e("Download", "Erro ao salvar PDF", e);
                showToast("Erro: " + e.getMessage());
            }
        }

        @JavascriptInterface
        public void downloadJpg(String base64Data, String fileName) {
            try {
                String safeFileName = fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
                byte[] jpgAsBytes = Base64.decode(base64Data, Base64.NO_WRAP);

                ContentValues contentValues = new ContentValues();
                contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, safeFileName);
                contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
                contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);

                ContentResolver resolver = mContext.getContentResolver();
                Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues);

                if (uri != null) {
                    try (OutputStream outputStream = resolver.openOutputStream(uri)) {
                        if (outputStream != null) {
                            outputStream.write(jpgAsBytes);
                            outputStream.flush();
                            showToast("Salvo em Downloads: " + safeFileName);
                            return;
                        }
                    }
                }
                showToast("Erro ao criar arquivo no dispositivo.");
            } catch (Exception e) {
                Log.e("Download", "Erro ao salvar JPG", e);
                showToast("Erro: " + e.getMessage());
            }
        }

        private void showToast(String msg) {
            activity.runOnUiThread(() -> Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show());
        }
    }
}