package nowatch.tv.service;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import nowatch.tv.Main;
import nowatch.tv.R;
import nowatch.tv.network.Network;
import nowatch.tv.ui.ListItems;
import nowatch.tv.utils.Feed;
import nowatch.tv.utils.UpdateDb;
import android.app.Activity;
import android.app.Service;
import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;

public class UpdateTask extends AsyncTask<Void, Void, Void> {

    private static final String TAG = Main.TAG + "UpdateTask";
    private boolean sdcarderror = false;
    private List<Feed> feeds;
    protected WeakReference<ListItems> mActivity = null;
    protected WeakReference<NWService> mService = null;

    public UpdateTask(ListItems activity) {
        mActivity = new WeakReference<ListItems>(activity);
        initFeeds();
    }

    public UpdateTask(NWService service) {
        mService = new WeakReference<NWService>(service);
        initFeeds();
    }

    private void initFeeds() {
        // Add all feeds
        feeds = new ArrayList<Feed>();
        feeds.add(new Feed(1, R.string.feed_cinefuzz));
        feeds.add(new Feed(2, R.string.feed_geekinc));
        feeds.add(new Feed(3, R.string.feed_scuds));
        feeds.add(new Feed(4, R.string.feed_zapcast));
        feeds.add(new Feed(5, R.string.feed_tom));
        feeds.add(new Feed(6, R.string.feed_revuetech));
    }

    @Override
    protected void onPreExecute() {
        final Context ctxt = getContext();
        final Network net = new Network(ctxt);
        if (!net.isConnected()) {
            Toast.makeText(ctxt, R.string.toast_notconnected, Toast.LENGTH_SHORT).show();
            cancel(false);
        }
    }

    @Override
    protected Void doInBackground(Void... params) {
        final Context ctxt = getContext();
        try {
            for (Feed f : feeds) {
                UpdateDb.update(ctxt, "" + f._id, f._resource);
            }
        } catch (IOException e) {
            Log.e(TAG, e.getMessage());
            sdcarderror = true;
        }
        return null;
    }

    @Override
    protected void onPostExecute(Void unused) {
        if (sdcarderror) {
            final Context ctxt = getContext();
            Toast.makeText(ctxt, R.string.toast_sdcard, Toast.LENGTH_SHORT).show();
        }
    }

    protected Context getContext() {
        if (mActivity != null) {
            final Activity a = mActivity.get();
            if (a != null) {
                return a.getApplicationContext();
            }
        } else if (mService != null) {
            final Service s = mService.get();
            if (s != null) {
                return s.getApplicationContext();
            }
        }
        return null;
    }

    protected ListItems getActivity() {
        if (mActivity != null) {
            final ListItems a = mActivity.get();
            if (a != null) {
                return a;
            }
        }
        return null;
    }

    protected NWService getService() {
        if (mService != null) {
            final NWService s = mService.get();
            if (s != null) {
                return s;
            }
        }
        return null;
    }

}