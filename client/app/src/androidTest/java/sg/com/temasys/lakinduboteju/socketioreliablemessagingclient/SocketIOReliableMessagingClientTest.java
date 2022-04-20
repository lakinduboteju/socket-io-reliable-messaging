package sg.com.temasys.lakinduboteju.socketioreliablemessagingclient;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.URISyntaxException;

import io.socket.client.IO;
import io.socket.client.Socket;


@RunWith(AndroidJUnit4.class)
public class SocketIOReliableMessagingClientTest {
    @Test
    public void test() {
        final Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();

        final String serverHost     = appContext.getResources().getString(R.string.socketio_server_host);
        final int serverPort        = appContext.getResources().getInteger(R.integer.sockerio_server_port);
        final String serverPath     = "/socket.io/";
        final int numbOfMsgsToSend  = 2000;

        IO.Options options = new IO.Options();
        options.path = serverPath;

        // Few other options
        /*
        options.secure = true;
        options.forceNew = true;
        options.reconnection = false;
        options.transports = new String[] {WebSocket.NAME};
        options.timeout = 5000;
        */

        final String serverUri = "http://" + serverHost + ':' + serverPort;

        Socket socket = null;
        try {
            socket = IO.socket(serverUri, options);
        } catch (URISyntaxException e) {
            e.printStackTrace();
            assertTrue("Creating socket failed : " + e.getMessage(), false);
        }

        // Connect to SocketIO server and wait for connection
        TestUtils.connect(socket);
        assertTrue(socket.connected());

        MessageSender sender = new MessageSender(socket);
        sender.start(); // start message sending thread

        for (int i = 0; i < numbOfMsgsToSend; i++) {
            assertTrue( sender.send(Integer.toString(i)) );
        }

        sender.stop(); // safely stop message sending thread and wait until it stops

        socket.close();

        assertEquals(numbOfMsgsToSend, sender.getSentMsgCount());
    }
}