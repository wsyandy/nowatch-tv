// HTTP Client based on AndroidHttpClient (API level 8)

package net.nowatch.network;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

import net.nowatch.Main;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.params.HttpClientParams;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;

import android.content.Context;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Environment;
import android.util.Log;

public class GetFile {

    private final String TAG = Main.TAG + "GetFile";
    private static final String USERAGENT = "Android/" + android.os.Build.VERSION.RELEASE + " ("
            + android.os.Build.MODEL + ") NoWatch.NET/";
    private String version = "0.3.x";
    private HttpGet httpget = null;
    private DefaultHttpClient httpclient = null;
    private final int buffer_size = 4 * 1024; // in Bytes
    private boolean resume = false;

    protected String etag;
    protected long file_local_size = 0;
    protected long file_remote_size = 0;

    public Boolean isCancelled = false;
    public boolean deleteOnFinish;
    public static final String PATH_CACHE = "Android/data/net.nowatch/cache";
    public static final String PATH_PODCASTS = "Podcasts/NoWatch.TV";

    public GetFile(final Context ctxt) {
        if (ctxt != null) {
            try {
                version = ctxt.getPackageManager().getPackageInfo("net.nowatch", 0).versionName;
            } catch (NameNotFoundException e) {
            }
        }
    }

    private HttpEntity openUrl(String src, String etag) {
        // Set HTTP Client params
        HttpParams params = new BasicHttpParams();
        HttpConnectionParams.setStaleCheckingEnabled(params, false);
        HttpConnectionParams.setConnectionTimeout(params, 20 * 1000);
        HttpConnectionParams.setSoTimeout(params, 20 * 1000);
        HttpConnectionParams.setSocketBufferSize(params, 8192);
        HttpClientParams.setRedirecting(params, true);
        params.setParameter("http.useragent", USERAGENT + version);

        // Register standard protocols
        SchemeRegistry schemeRegistry = new SchemeRegistry();
        schemeRegistry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
        ClientConnectionManager manager = new ThreadSafeClientConnManager(params, schemeRegistry);

        // Init Client
        httpget = new HttpGet(src);
        httpclient = new DefaultHttpClient(manager, params);

        try {
            // Headers
            if (etag != null) {
                httpget.addHeader("If-None-Match", etag);
            }
            if (file_local_size > 0 && resume) {
                httpget.addHeader("Range", file_local_size + "-");
            } else {
                file_local_size = 0;
            }
            // Execute HTTP request
            HttpResponse response = httpclient.execute(httpget);
            final int statusCode = response.getStatusLine().getStatusCode();
            Log.i(TAG, "Status:[" + statusCode + "] " + src);
            if (statusCode != HttpStatus.SC_OK) {
                return null;
            }
            // Resume only if possible
            if (response.getLastHeader("Accept-Ranges") == null) {
                resume = false;
            }
            // Get etag
            if (response.getLastHeader("ETag") != null) {
                this.etag = response.getLastHeader("ETag").getValue();
            }
            // Get file_size
            if (response.getLastHeader("Content-Length") != null) {
                try {
                    file_remote_size = Long.parseLong(response.getLastHeader("Content-Length")
                            .getValue());
                } catch (NumberFormatException e) {
                    Log.e(TAG, "Length=" + response.getLastHeader("Content-Length").getValue()
                            + ", " + e.getMessage());
                }
            }
            // Retrieve content
            return response.getEntity();
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
            httpget.abort();
            return null;
        }
    }

    public File getDestination(String dst) {
        try {
            if (dst != null) {
                return new File(dst);
            } else {
                File dstFile = new File(Environment.getExternalStorageDirectory()
                        .getCanonicalPath()
                        + "/" + PATH_CACHE);
                dstFile.mkdirs();
                return File.createTempFile("nowatchtv", "", dstFile);
            }
        } catch (IOException e) {
            Log.e(TAG, e.getMessage());
        }
        return null;
    }

    // Non-Blocking download (large files?)
    public void getChannel(String src, String dst, String etag) throws ClientProtocolException,
            UnknownHostException, IOException {
        getChannel(src, dst, etag, true, false);
    }

    public void getChannel(String src, String dst, String etag, boolean _deleteOnFinish,
            boolean resumable) throws ClientProtocolException, UnknownHostException, IOException {

        resume = resumable;
        deleteOnFinish = _deleteOnFinish;

        if (!Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
            return;
        }

        // Get OutputStream and InputStream
        File dstFile = getDestination(dst);
        file_local_size = dstFile.length();
        final HttpEntity entity = openUrl(src, etag);
        if (entity == null) {
            return;
        }
        if (file_remote_size < 1) {
            file_remote_size = entity.getContentLength();
        }
        InputStream in = entity.getContent();
        FileOutputStream out = new FileOutputStream(dstFile, resume);
        if (in != null && dstFile != null) {
            update(0);
            final ReadableByteChannel inputChannel = Channels.newChannel(in);
            final WritableByteChannel outputChannel = Channels.newChannel(out);
            // TODO: see if FileChannel.transferFrom() would be nice
            try {
                // Fast Channel Copy

                // if (inputChannel != null && out != null) {
                // FileChannel filechannel = out.getChannel();
                // Log.v(TAG, "size=" + file_local_size);
                // filechannel.transferFrom(inputChannel, 0L, file_local_size);
                // if (filechannel != null) {
                // filechannel.close();
                // }
                // }

                if (inputChannel != null && outputChannel != null) {
                    final ByteBuffer buffer = ByteBuffer.allocateDirect(buffer_size);
                    // final ByteBuffer buffer = ByteBuffer.allocate(buffer);
                    long count;
                    cancelled: {
                        while ((count = inputChannel.read(buffer)) != -1) {
                            synchronized (isCancelled) {
                                if (isCancelled == true) {
                                    break cancelled;
                                }
                            }
                            buffer.flip();
                            outputChannel.write(buffer);
                            buffer.compact();
                            update(count);
                        }
                        buffer.flip();
                        while (buffer.hasRemaining()) {
                            update(outputChannel.write(buffer));
                        }
                    }
                }
            } catch (NullPointerException e) {
                Log.e(TAG, e.getMessage());
                e.printStackTrace();
            } catch (IOException e) {
                Log.e(TAG, e.getMessage());
                e.printStackTrace();
            } finally {
                try {
                    if (httpget != null) {
                        httpget.abort();
                    }
                    if (entity != null) {
                        entity.consumeContent();
                    }
                    if (in != null) {
                        in.close();
                    }
                    if (out != null) {
                        out.close();
                    }
                    if (inputChannel != null) {
                        inputChannel.close();
                    }
                    if (outputChannel != null) {
                        outputChannel.close();
                    }
                } catch (Exception e) {
                    Log.e(TAG, e.getMessage());
                }
                finish(deleteOnFinish, dstFile.getAbsolutePath());
            }
            return;
        } else {
            if (dstFile != null) {
                finish(deleteOnFinish, dstFile.getAbsolutePath());
            }
        }
    }

    // public void getBlocking(String src, String dst, String etag) {
    // getBlocking(src, dst, etag, true, false);
    // }
    //
    // // Blocking download (small files?)
    // public void getBlocking(String src, String dst, String etag, boolean
    // deleteOnFinish,
    // boolean resume) {
    //
    // if
    // (!Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState()))
    // {
    // return;
    // }
    //
    // InputStream in = null;
    // OutputStream out = null;
    // HttpEntity entity = null;
    // File dstFile = null;
    //
    // try {
    // entity = openUrl(src, etag, resume);
    // if (entity == null) {
    // return;
    // }
    // in = entity.getContent();
    // dstFile = getDestination(dst);
    // if (in != null && dstFile != null) {
    // out = new FileOutputStream(dstFile);
    // byte data[] = new byte[1024];
    // int total = 0;
    // int count;
    // cancelled: {
    // while ((count = in.read(data)) != -1) {
    // if (isCancelled == true) {
    // break cancelled;
    // }
    // out.write(data, 0, count);
    // total += count;
    // update(count);
    // }
    // }
    // }
    // } catch (IOException e) {
    // Log.e(TAG, e.getMessage());
    // } finally {
    // try {
    // if (httpget != null) {
    // httpget.abort();
    // }
    // if (in != null) {
    // in.close();
    // }
    // if (out != null) {
    // out.close();
    // }
    // if (entity != null) {
    // entity.consumeContent();
    // }
    // if (dstFile != null) {
    // finish(deleteOnFinish, dstFile.getAbsolutePath());
    // }
    // } catch (Exception e) {
    // Log.e(TAG, e.getMessage());
    // }
    // }
    // }

    protected void update(long count) {
        // Nothing to do here
    }

    protected void finish(boolean deleteOnFinish, String file) {
        if (deleteOnFinish && file != null) {
            new File(file).delete();
        }
    }

    // TODO: Merge with getChannel()
    public static void fastChannelCopy(final ReadableByteChannel src, final WritableByteChannel dest)
            throws IOException, NullPointerException {
        if (src != null && dest != null) {
            final ByteBuffer buffer = ByteBuffer.allocateDirect(16 * 1024);
            while (src.read(buffer) != -1) {
                buffer.flip();
                dest.write(buffer);
                buffer.compact();
            }
            buffer.flip();
            while (buffer.hasRemaining()) {
                dest.write(buffer);
            }
            if (src != null) {
                src.close();
            }
            if (dest != null) {
                dest.close();
            }
        }
    }
}
