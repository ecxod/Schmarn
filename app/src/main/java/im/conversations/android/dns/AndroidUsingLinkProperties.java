package im.conversations.android.dns;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkInfo;
import android.net.RouteInfo;
import de.measite.minidns.dnsserverlookup.AbstractDNSServerLookupMechanism;
import de.measite.minidns.dnsserverlookup.AndroidUsingExec;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

public class AndroidUsingLinkProperties extends AbstractDNSServerLookupMechanism {

    private final Context context;

    AndroidUsingLinkProperties(Context context) {
        super(AndroidUsingLinkProperties.class.getSimpleName(), AndroidUsingExec.PRIORITY - 1);
        this.context = context;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public String[] getDnsServerAddresses() {
        final ConnectivityManager connectivityManager =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        final Network[] networks =
                connectivityManager == null ? null : connectivityManager.getAllNetworks();
        if (networks == null) {
            return new String[0];
        }
        final Network activeNetwork = connectivityManager.getActiveNetwork();
        final List<String> servers = new ArrayList<>();
        int vpnOffset = 0;
        for (Network network : networks) {
            LinkProperties linkProperties = connectivityManager.getLinkProperties(network);
            if (linkProperties == null) {
                continue;
            }
            final NetworkInfo networkInfo = connectivityManager.getNetworkInfo(network);
            final boolean isActiveNetwork = network.equals(activeNetwork);
            final boolean isVpn =
                    networkInfo != null && networkInfo.getType() == ConnectivityManager.TYPE_VPN;
            if (isActiveNetwork && isVpn) {
                final List<String> tmp = getIPv4First(linkProperties.getDnsServers());
                servers.addAll(0, tmp);
                vpnOffset += tmp.size();
            } else if (hasDefaultRoute(linkProperties)
                    || isActiveNetwork
                    || activeNetwork == null
                    || isVpn) {
                servers.addAll(vpnOffset, getIPv4First(linkProperties.getDnsServers()));
            }
        }
        return servers.toArray(new String[0]);
    }

    private static List<String> getIPv4First(List<InetAddress> in) {
        List<String> out = new ArrayList<>();
        for (InetAddress address : in) {
            if (address instanceof Inet4Address) {
                out.add(0, address.getHostAddress());
            } else {
                out.add(address.getHostAddress());
            }
        }
        return out;
    }

    private static boolean hasDefaultRoute(LinkProperties linkProperties) {
        for (RouteInfo route : linkProperties.getRoutes()) {
            if (route.isDefaultRoute()) {
                return true;
            }
        }
        return false;
    }
}
