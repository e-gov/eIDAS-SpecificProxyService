package ee.ria.eidas.proxy.specific.storage;

import ee.ria.eidas.proxy.specific.SpecificProxyTest;
import ee.ria.eidas.proxy.specific.config.SpecificProxyServiceConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.apache.ignite.Ignite;
import org.apache.ignite.events.Event;
import org.apache.ignite.internal.DiscoverySpiTestListener;
import org.apache.ignite.internal.managers.discovery.IgniteDiscoverySpi;
import org.apache.ignite.internal.util.typedef.internal.U;
import org.apache.ignite.lang.IgnitePredicate;
import org.apache.ignite.spi.discovery.DiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

import java.util.concurrent.CountDownLatch;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.apache.ignite.events.EventType.EVT_CLIENT_NODE_DISCONNECTED;
import static org.apache.ignite.events.EventType.EVT_CLIENT_NODE_RECONNECTED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@Slf4j
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ContextConfiguration(classes = SpecificProxyServiceConfiguration.class, initializers = IgniteInstanceInitializerTests.TestContextInitializer.class)
public class IgniteInstanceInitializerTests extends SpecificProxyTest {
    private static final long RECONNECT_TIMEOUT = 10_000;

    @Autowired
    private Ignite igniteClient;

    @Test
    void igniteClientReconnectWhenDisconnected() throws Exception {
        DiscoverySpi serverDiscoverySpi = eidasNodeIgnite.configuration().getDiscoverySpi();
        IgniteDiscoverySpi clientDiscoverySpi = (IgniteDiscoverySpi) igniteClient.configuration().getDiscoverySpi();
        CountDownLatch disconnectLatch = new CountDownLatch(1);
        CountDownLatch reconnectLatch = new CountDownLatch(1);
        DiscoverySpiTestListener clientSpiListener = new DiscoverySpiTestListener();
        clientDiscoverySpi.setInternalListener(clientSpiListener);
        clientSpiListener.startBlockJoin();

        igniteClient.events().localListen((IgnitePredicate<Event>) evt -> {
            log.info("Ignite client event: {}", evt.type());
            if (evt.type() == EVT_CLIENT_NODE_DISCONNECTED) {
                assertEquals(1, reconnectLatch.getCount());
                disconnectLatch.countDown();
            } else if (evt.type() == EVT_CLIENT_NODE_RECONNECTED) {
                reconnectLatch.countDown();
            }
            return true;
        }, EVT_CLIENT_NODE_DISCONNECTED, EVT_CLIENT_NODE_RECONNECTED);

        serverDiscoverySpi.failNode(igniteClient.cluster().localNode().id(), null);
        waitEvent(disconnectLatch);
        U.sleep(2000);
        clientSpiListener.stopBlockJoin();
        waitEvent(reconnectLatch);

        assertWarningIsLogged(TcpDiscoverySpi.class, "Local node was dropped from cluster due to network problems");
        assertErrorIsLogged(TcpDiscoverySpi.class, "Failed to send message: TcpDiscoveryClientMetricsUpdateMessage");
        assertWarningIsLogged(TcpDiscoverySpi.class, "Client node was reconnected after it was already considered failed by the server topology");
    }

    private void waitEvent(CountDownLatch latch) throws Exception {
        if (!latch.await(RECONNECT_TIMEOUT, MILLISECONDS)) {
            fail("Failed to wait for disconnect/reconnect event.");
        }
    }
}
