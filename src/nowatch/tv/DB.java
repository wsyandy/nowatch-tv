package nowatch.tv;

import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public class DB extends SQLiteOpenHelper {
    private final static String TAG = "DB";
    private final static String DB_NAME = "nowatch.db";
    private final static int DB_VERSION = 1;
    private final String CREATE_FEEDS = "create table feeds (_id INTEGER PRIMARY KEY, title TEXT, description TEXT, link TEXT, pubDate NUMERIC, image BLOB);";
    private final String CREATE_ITEMS = "create table items (_id INTEGER PRIMARY KEY, feed_id INTEGER, title TEXT, description TEXT, link TEXT, pubDate NUMERIC, file_uri TEXT, file_size INTEGER, file_type TEXT);";
    private final String[] podcasts = new String[] { "cinefuzz", "geekinc", "scudstv", "zapcasttv" };
    
    public DB(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        createTable(db, "feeds", CREATE_FEEDS);
        createTable(db, "items", CREATE_ITEMS);
        for (String podcast : podcasts) {
            db.execSQL("insert into feeds (\"title\") values (\"" + podcast + "\");");
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int Old, int New) {
    }

    private void createTable(SQLiteDatabase db, String table_name, String create) {
        Log.v(TAG, "createTable " + table_name);
        Cursor c = db.rawQuery("select name from sqlite_master where type='table' and name='"
                + table_name + "'", null);
        try {
            if (c.getCount() == 0) {
                db.execSQL(create);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            c.close();
        }
    }
}
