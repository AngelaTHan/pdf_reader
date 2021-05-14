package ca.uwaterloo.cs349.pdfreader;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.*;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

// PDF sample code from
// https://medium.com/@chahat.jain0/rendering-a-pdf-document-in-android-activity-fragment-using-pdfrenderer-442462cb8f9a
// Issues about cache etc. are not obvious from documentation, so read this carefully before making changes
// to the PDF display code.

public class MainActivity extends AppCompatActivity {

    final String LOGNAME = "pdf_viewer";
    final String FILENAME = "shannon1948.pdf";
    final int FILERESID = R.raw.shannon1948;

    // manage the pages of the PDF, see below
    PdfRenderer pdfRenderer;
    private ParcelFileDescriptor parcelFileDescriptor;
    private PdfRenderer.Page currentPage;

    // custom ImageView class that captures strokes and draws them over the image
    PDFimage pageImage;
    TextView pageNumberView;

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        setContentView(R.layout.activity_main);

        TextView filenameView = findViewById(R.id.filename);
        filenameView.setText(FILENAME);

        LinearLayout layout = findViewById(R.id.pdfLayout);
        pageImage = new PDFimage(this);
        layout.addView(pageImage);
        layout.setEnabled(true);
        pageImage.setMinimumWidth(1000);
        pageImage.setMinimumHeight(2000);

        pageNumberView = new TextView(this);
        layout.addView(pageNumberView);
        pageNumberView.setTextSize(24);
        pageNumberView.setPadding(1400, 0, 0, 0);


        final ImageButton undoButton = findViewById(R.id.undo);
        undoButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch(event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        int pageModified = pageImage.undo();
                        if (pageModified > -1) {
                            undoButton.setBackgroundResource(R.drawable.undo_clicked);
                            v.performClick();
                            if (pageModified != currentPage.getIndex()) {
                                showPage(pageModified);
                            }
                        }
                        Log.d("BUTTONS", "clicked undo: " + pageModified);
                        break;
                    case MotionEvent.ACTION_UP:
                        undoButton.setBackgroundResource(R.drawable.undo);
                        break;
                }
                return true;
            }
        });

        final ImageButton redoButton = findViewById(R.id.redo);
        redoButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch(event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        int pageModified = pageImage.redo();
                        if (pageModified > -1) {
                            redoButton.setBackgroundResource(R.drawable.redo_clicked);
                            v.performClick();
                            if (pageModified != currentPage.getIndex()) {
                                showPage(pageModified);
                            }
                        }
                        Log.d("BUTTONS", "clicked redo: " + pageModified);
                        break;
                    case MotionEvent.ACTION_UP:
                        redoButton.setBackgroundResource(R.drawable.redo);
                        break;
                }
                return true;
            }
        });

        final ImageButton previousButton = findViewById(R.id.previous);
        previousButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch(event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        if (currentPage.getIndex() > 0) {
                            previousButton.setBackgroundResource(R.drawable.left_clicked);
                            v.performClick();
                            showPage(currentPage.getIndex()-1);
                        }
                        Log.d("BUTTONS", "clicked prev: ");
                        break;
                    case MotionEvent.ACTION_UP:
                        previousButton.setBackgroundResource(R.drawable.left);
                        break;
                }
                return true;
            }
        });

        final ImageButton nextButton = findViewById(R.id.next);
        nextButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch(event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        if (currentPage.getIndex() < pdfRenderer.getPageCount() - 1) {
                            nextButton.setBackgroundResource(R.drawable.right_clicked);
                            v.performClick();
                            showPage(currentPage.getIndex()+1);
                        }
                        Log.d("BUTTONS", "clicked next: ");
                        break;
                    case MotionEvent.ACTION_UP:
                        nextButton.setBackgroundResource(R.drawable.right);
                        break;
                }
                return true;
            }
        });

        final ImageButton penButton = findViewById(R.id.pen);
        penButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch(event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        penButton.setBackgroundResource(R.drawable.pen_clicked);
                        v.performClick();
                        pageImage.setTool(PDFimage.Tool.PEN);
                        Log.d("BUTTONS", "clicked pen: ");
                        break;
                    case MotionEvent.ACTION_UP:
                        penButton.setBackgroundResource(R.drawable.pen);
                        break;
                }
                return true;
            }
        });

        final ImageButton markerButton = findViewById(R.id.marker);
        markerButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch(event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        markerButton.setBackgroundResource(R.drawable.marker_clicked);
                        v.performClick();
                        pageImage.setTool(PDFimage.Tool.MARKER);
                        Log.d("BUTTONS", "clicked marker: ");
                        break;
                    case MotionEvent.ACTION_UP:
                        markerButton.setBackgroundResource(R.drawable.marker);
                        break;
                }
                return true;
            }
        });

        final ImageButton eraserButton = findViewById(R.id.eraser);
        eraserButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch(event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        eraserButton.setBackgroundResource(R.drawable.eraser_clicked);
                        v.performClick();
                        pageImage.setTool(PDFimage.Tool.ERASER);
                        Log.d("BUTTONS", "clicked eraser: ");
                        break;
                    case MotionEvent.ACTION_UP:
                        eraserButton.setBackgroundResource(R.drawable.eraser);
                        break;
                }
                return true;
            }
        });

        // open page 0 of the PDF
        // it will be displayed as an image in the pageImage (above)
        try {
            openRenderer(this);
            showPage(0);
        } catch (IOException exception) {
            Log.d(LOGNAME, "Error opening PDF");
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            closeRenderer();
        } catch (IOException ex) {
            Log.d(LOGNAME, "Unable to close PDF renderer");
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void openRenderer(Context context) throws IOException {
        // In this sample, we read a PDF from the assets directory.
        File file = new File(context.getCacheDir(), FILENAME);
        if (!file.exists()) {
            // pdfRenderer cannot handle the resource directly,
            // so extract it into the local cache directory.
            InputStream asset = this.getResources().openRawResource(FILERESID);
            FileOutputStream output = new FileOutputStream(file);
            final byte[] buffer = new byte[1024];
            int size;
            while ((size = asset.read(buffer)) != -1) {
                output.write(buffer, 0, size);
            }
            asset.close();
            output.close();
        }
        parcelFileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);

        // capture PDF data
        // all this just to get a handle to the actual PDF representation
        if (parcelFileDescriptor != null) {
            pdfRenderer = new PdfRenderer(parcelFileDescriptor);
        }
    }

    // do this before you quit!
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void closeRenderer() throws IOException {
        if (null != currentPage) {
            currentPage.close();
        }
        pdfRenderer.close();
        parcelFileDescriptor.close();
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void showPage(int index) {
        int totalPage = pdfRenderer.getPageCount();
        if (totalPage <= index) {
            return;
        }
        // Close the current page before opening another one.
        if (null != currentPage) {
            currentPage.close();
        }
        // Use `openPage` to open a specific page in PDF.
        currentPage = pdfRenderer.openPage(index);
        // Important: the destination bitmap must be ARGB (not RGB).
        Bitmap bitmap = Bitmap.createBitmap(currentPage.getWidth(), currentPage.getHeight(), Bitmap.Config.ARGB_8888);

        // Here, we render the page onto the Bitmap.
        // To render a portion of the page, use the second and third parameter. Pass nulls to get the default result.
        // Pass either RENDER_MODE_FOR_DISPLAY or RENDER_MODE_FOR_PRINT for the last parameter.
        currentPage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);

        // Display the page
        pageImage.setImage(bitmap);
        pageImage.setCurrentIndex(index);
        pageNumberView.setText("Page " + (index + 1) + "/" + totalPage);
    }
}
