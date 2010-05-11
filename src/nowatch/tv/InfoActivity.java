package nowatch.tv;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

public class InfoActivity extends Activity {

    private final String TAG = "InfoActivity";
    private final String REQ = "SELECT feeds.title, items.title, items.description, "
            + "items.link, feeds.link, image, file_uri, file_size, file_type "
            + "FROM items INNER JOIN feeds ON items.feed_id=feeds._id WHERE items._id=";
    private final String PRE = "<meta http-equiv=\"Content-Type\" content=\"application/xhtml+xml; charset=UTF-8\"/>";
    private final String STYLE = "<style>*{color: white;}</style>";
    private final int IMG_DIP = 64;
    private DisplayMetrics displayMetrics;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.info_activity);
        final Context ctxt = getApplicationContext();

        // Screen metrics (for dip to px conversion)
        displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);

        // Get item information
        Bundle extra = getIntent().getExtras();
        SQLiteDatabase db = (new DB(ctxt)).getWritableDatabase();
        Cursor c = db.rawQuery(REQ + extra.getInt("item_id"), null);
        c.moveToFirst();
        final String title = c.getString(1);
        ((TextView) findViewById(R.id.title)).setText(title);
        ((WebView) findViewById(R.id.desc)).loadData(PRE + c.getString(2) + STYLE, "text/html",
                "utf-8");
        ((WebView) findViewById(R.id.desc)).setBackgroundColor(0);
        // ((TextView) findViewById(R.id.link)).setText(c.getString(3));
        ImageView logo = (ImageView) findViewById(R.id.logo);
        byte[] logo_byte = c.getBlob(5);
        if (logo_byte != null && logo_byte.length > 200) {
            logo.setImageBitmap(Bitmap.createScaledBitmap(BitmapFactory.decodeByteArray(logo_byte,
                    0, logo_byte.length), (int) (IMG_DIP * displayMetrics.density + 0.5f),
                    (int) (IMG_DIP * displayMetrics.density + 0.5f), true));
        } else {
            logo.setImageResource(R.drawable.icon);
        }

        // File
        final String file_uri = c.getString(6);
        // final String file_size = c.getString(7);
        final String file_type = c.getString(8);

        // Set buttons
        ((Button) findViewById(R.id.btn_play)).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent i = new Intent(Intent.ACTION_VIEW);
                try {
                    i.setDataAndType(Uri.parse(file_uri), file_type);
                    startActivity(i);
                } catch (ActivityNotFoundException e) {
                    Log.e(TAG, e.getMessage());
                    try {
                        i.setType("video/*");
                        startActivity(i);
                    } catch (ActivityNotFoundException e1) {
                        Log.e(TAG, e1.getMessage());
                        Toast.makeText(ctxt, "Format de fichier non supporté !", Toast.LENGTH_LONG)
                                .show();
                    }
                }
            }
        });
        final Intent i = new Intent(this, DownloadService.class);
        i.putExtra("item_id", extra.getInt("item_id"));
        ((Button) findViewById(R.id.btn_download)).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startService(i);
                finish();
                // ((Button)
                // findViewById(R.id.btn_download)).setClickable(false);
                // ((Button) findViewById(R.id.btn_download)).setEnabled(false);
            }
        });

        // Close stuff
        c.close();
        db.close();
    }
}
