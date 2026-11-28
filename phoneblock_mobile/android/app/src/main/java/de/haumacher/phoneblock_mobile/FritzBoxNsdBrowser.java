package de.haumacher.phoneblock_mobile;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.net.InetAddress;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Searches the local network via mDNS for Fritz!Box devices, which announce themselves as
 * {@code _tr064._tcp} (issue #660).
 *
 * <p>
 * Browses for a fixed time, resolves every service found (one at a time, as older Android
 * versions allow only one resolve at a time) and reports the hosts under which each service can
 * be reached (see {@link Tr064ServiceHosts}). On Android 17 the browse requires the permission
 * {@code ACCESS_LOCAL_NETWORK}. All state is handled on the main thread; the callback is invoked
 * there exactly once.
 * </p>
 */
final class FritzBoxNsdBrowser {

    private static final String TAG = "FritzBoxNsdBrowser";

    /** The mDNS service type of the TR-064 interface of a Fritz!Box. */
    static final String SERVICE_TYPE = "_tr064._tcp";

    /** Time to wait for resolves still running after browsing has stopped. */
    private static final long RESOLVE_GRACE_MS = 1500;

    /** Receives the result of a search. */
    interface Callback {
        /**
         * @param services One map per resolved service with the keys {@code name} (service name)
         *        and {@code hosts} (list of host strings, best first).
         */
        void onDone(List<Map<String, Object>> services);
    }

    private final NsdManager _nsd;
    private final Handler _main = new Handler(Looper.getMainLooper());
    private final Callback _callback;

    private final Map<String, List<String>> _hosts = new LinkedHashMap<>();
    private final Set<String> _seen = new HashSet<>();
    private final Deque<NsdServiceInfo> _pending = new ArrayDeque<>();

    private boolean _resolving;
    private boolean _browsing;
    private boolean _browseStopped;
    private boolean _finished;

    private final NsdManager.DiscoveryListener _discoveryListener = new NsdManager.DiscoveryListener() {
        @Override
        public void onStartDiscoveryFailed(String serviceType, int errorCode) {
            Log.w(TAG, "mDNS browse failed to start: " + errorCode);
            _main.post(() -> {
                _browsing = false;
                finish();
            });
        }

        @Override
        public void onStopDiscoveryFailed(String serviceType, int errorCode) {
            Log.w(TAG, "mDNS browse failed to stop: " + errorCode);
        }

        @Override
        public void onDiscoveryStarted(String serviceType) {
            Log.d(TAG, "mDNS browse started");
        }

        @Override
        public void onDiscoveryStopped(String serviceType) {
            Log.d(TAG, "mDNS browse stopped");
        }

        @Override
        public void onServiceFound(NsdServiceInfo serviceInfo) {
            _main.post(() -> enqueue(serviceInfo));
        }

        @Override
        public void onServiceLost(NsdServiceInfo serviceInfo) {
            // A service lost during the short search is still reported, if resolved.
        }
    };

    private FritzBoxNsdBrowser(Context context, Callback callback) {
        _nsd = (NsdManager) context.getApplicationContext().getSystemService(Context.NSD_SERVICE);
        _callback = callback;
    }

    /**
     * Starts a search of {@code timeoutMs} milliseconds and reports the result to
     * {@code callback} on the main thread.
     */
    static void search(Context context, long timeoutMs, Callback callback) {
        new FritzBoxNsdBrowser(context, callback).start(timeoutMs);
    }

    private void start(long timeoutMs) {
        if (_nsd == null) {
            finish();
            return;
        }
        try {
            _nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, _discoveryListener);
            _browsing = true;
        } catch (RuntimeException ex) {
            Log.w(TAG, "mDNS browse not possible", ex);
            finish();
            return;
        }
        _main.postDelayed(this::stopBrowsing, timeoutMs);
    }

    private void stopBrowsing() {
        if (_finished) {
            return;
        }
        _browseStopped = true;
        stopDiscovery();
        if (!_resolving && _pending.isEmpty()) {
            finish();
        } else {
            _main.postDelayed(this::finish, RESOLVE_GRACE_MS);
        }
    }

    private void stopDiscovery() {
        if (_browsing) {
            _browsing = false;
            try {
                _nsd.stopServiceDiscovery(_discoveryListener);
            } catch (RuntimeException ex) {
                Log.w(TAG, "Stopping the mDNS browse failed", ex);
            }
        }
    }

    private void enqueue(NsdServiceInfo serviceInfo) {
        if (_finished || _browseStopped) {
            return;
        }
        String name = serviceInfo.getServiceName();
        if (name == null || !_seen.add(name)) {
            return;
        }
        _pending.add(serviceInfo);
        resolveNext();
    }

    @SuppressWarnings("deprecation")
    private void resolveNext() {
        if (_finished || _resolving || _pending.isEmpty()) {
            return;
        }
        NsdServiceInfo next = _pending.poll();
        _resolving = true;
        try {
            _nsd.resolveService(next, new NsdManager.ResolveListener() {
                @Override
                public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
                    Log.w(TAG, "Resolving " + serviceInfo.getServiceName() + " failed: " + errorCode);
                    _main.post(() -> resolved(null));
                }

                @Override
                public void onServiceResolved(NsdServiceInfo serviceInfo) {
                    _main.post(() -> resolved(serviceInfo));
                }
            });
        } catch (RuntimeException ex) {
            Log.w(TAG, "Resolving " + next.getServiceName() + " not possible", ex);
            _resolving = false;
            resolveNext();
        }
    }

    private void resolved(NsdServiceInfo serviceInfo) {
        _resolving = false;
        if (_finished) {
            return;
        }
        if (serviceInfo != null) {
            List<String> hosts = Tr064ServiceHosts.candidateHosts(serviceInfo.getAttributes(), addresses(serviceInfo));
            if (!hosts.isEmpty()) {
                _hosts.put(serviceInfo.getServiceName(), hosts);
            }
        }
        resolveNext();
        if (_browseStopped && !_resolving && _pending.isEmpty()) {
            finish();
        }
    }

    @SuppressWarnings("deprecation")
    private static List<String> addresses(NsdServiceInfo serviceInfo) {
        List<String> result = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            for (InetAddress address : serviceInfo.getHostAddresses()) {
                result.add(address.getHostAddress());
            }
        } else {
            InetAddress host = serviceInfo.getHost();
            if (host != null) {
                result.add(host.getHostAddress());
            }
        }
        return result;
    }

    private void finish() {
        if (_finished) {
            return;
        }
        _finished = true;
        stopDiscovery();
        _main.removeCallbacksAndMessages(null);

        List<Map<String, Object>> services = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : _hosts.entrySet()) {
            Map<String, Object> service = new HashMap<>();
            service.put("name", entry.getKey());
            service.put("hosts", entry.getValue());
            services.add(service);
        }
        Log.d(TAG, "mDNS search found " + services.size() + " service(s)");
        _callback.onDone(services);
    }
}
