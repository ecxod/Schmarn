package im.conversations.android.xmpp.manager;

import android.content.Context;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import im.conversations.android.xml.Namespace;
import im.conversations.android.xmpp.XmppConnection;
import im.conversations.android.xmpp.model.disco.external.Service;
import im.conversations.android.xmpp.model.disco.external.Services;
import im.conversations.android.xmpp.model.stanza.Iq;
import java.util.Collection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExternalDiscoManager extends AbstractManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExternalDiscoManager.class);

    public ExternalDiscoManager(Context context, XmppConnection connection) {
        super(context, connection);
    }

    public ListenableFuture<Collection<Service>> getServices() {
        final var hasFeatureFuture =
                getManager(DiscoManager.class)
                        .hasServerFeatureAsync(Namespace.EXTERNAL_SERVICE_DISCOVERY);
        final var iqResultFuture =
                Futures.transformAsync(
                        hasFeatureFuture,
                        hasFeature -> {
                            if (Boolean.TRUE.equals(hasFeature)) {
                                final Iq request = new Iq(Iq.Type.GET);
                                request.setTo(getAccount().address.asDomainBareJid());
                                request.addExtension(new Services());
                                return connection.sendIqPacket(request);
                            }
                            throw new IllegalStateException(
                                    "Server does not support External Service Discovery");
                        },
                        MoreExecutors.directExecutor());
        return Futures.transform(
                iqResultFuture,
                result -> {
                    final var services = result.getExtension(Services.class);
                    if (services == null) {
                        throw new IllegalStateException("Server result did not contain services");
                    }
                    return services.getExtensions(Service.class);
                },
                MoreExecutors.directExecutor());
    }
}
