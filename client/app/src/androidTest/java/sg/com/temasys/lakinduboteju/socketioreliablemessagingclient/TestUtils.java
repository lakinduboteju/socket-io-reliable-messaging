package sg.com.temasys.lakinduboteju.socketioreliablemessagingclient;

import static org.junit.Assert.assertTrue;

import android.util.Log;

import java.util.concurrent.CountDownLatch;

import io.socket.client.Socket;
import io.socket.emitter.Emitter;

public class TestUtils {
    public static void connect(Socket socket) {
        socket.connect();

        final CountDownLatch l = new CountDownLatch(1);

        socket.on(Socket.EVENT_CONNECT, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                l.countDown();
            }
        });

        socket.on(Socket.EVENT_CONNECT_ERROR, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                StringBuilder str = new StringBuilder();
                str.append("Socket connect error :");
                for (Object a : args) {
                    str.append(' ').append(a);
                }
                Log.e(Constants.LOG_TAG, str.toString());

                if (args[0] instanceof Exception) {
                    Exception e = (Exception) args[0];
                    e.printStackTrace();
                }

                l.countDown();
            }
        });

        try {
            l.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
            assertTrue("Waiting for connection failed : " + e.getMessage(), false);
        }
    }
}
