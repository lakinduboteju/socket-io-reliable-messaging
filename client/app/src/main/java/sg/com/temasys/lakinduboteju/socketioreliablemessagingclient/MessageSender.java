package sg.com.temasys.lakinduboteju.socketioreliablemessagingclient;

import android.util.Log;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import io.socket.client.Ack;
import io.socket.client.Socket;

public class MessageSender {
    private Socket mSocket;
    private LinkedBlockingQueue<String> mMsgQueue;
    private MessageSendingThread mMsgSendingThread;

    public MessageSender(Socket socket) {
        mSocket = socket;
        mMsgQueue = new LinkedBlockingQueue<>();
        mMsgSendingThread = new MessageSendingThread();
    }

    public void start() {
        mMsgSendingThread.start();
    }

    public void stop() {
        mMsgSendingThread.isStoppedSafely = true;
        try {
            mMsgSendingThread.join();
        } catch (InterruptedException e) {
            Log.e(Constants.LOG_TAG, "Failed to wait for message sending thread to stop");
            e.printStackTrace();
        }
    }

    public boolean send(String message) {
        try {
            mMsgQueue.put(message);
        } catch (InterruptedException e) {
            Log.e(Constants.LOG_TAG, "Failed to add message to the message queue");
            e.printStackTrace();
            return false;
        }

        return true;
    }

    public int getSentMsgCount() {
        return mMsgSendingThread.sentCount;
    }

    private class MessageSendingThread extends Thread {
        private static final int MSG_POLLING_TIME_MS    = 200;
        private static final int ACK_WAIT_TIME_MS       = 2000;
        private static final int MAX_RETRY_COUNT        = 5;

        private volatile boolean isStoppedSafely;
        private int sentCount;

        public MessageSendingThread() {
            super();
            isStoppedSafely = false;
            sentCount = 0;
        }

        @Override
        public void run() {
            Log.d(Constants.LOG_TAG, "MsgSendingThread started");

            while ( !isStoppedSafely || !mMsgQueue.isEmpty() ) {
                String message = null;

                try {
                    message = mMsgQueue.poll(MSG_POLLING_TIME_MS, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Log.w(Constants.LOG_TAG, "Message sending thread was interrupted but not stopped");
                    e.printStackTrace();
                }

                if (message == null) continue; // No messages in queue

                // Message sending state
                final int state[] = {
                        0, // is message sent (0 = not yet sent)
                        0  // retry count
                };

                // Send message and wait for acknowledgement
                do {
                    final CountDownLatch l = new CountDownLatch(1);

                    // Send and receive acknowledgement
                    mSocket.emit("message", message, new Ack() {
                        @Override
                        public void call(Object... args) {
                            // JSONObject response = (JSONObject) args[0];
                            state[0] = 1; // message sent
                            l.countDown();
                        }
                    });

                    try { l.await(ACK_WAIT_TIME_MS, TimeUnit.MILLISECONDS); } catch (InterruptedException e) { e.printStackTrace(); }

                    ++state[1]; // Increment retry count
                } while (mSocket != null && mSocket.connected() && state[0] == 0 && state[1] < MAX_RETRY_COUNT);

                // Message was not sent?
                if (state[0] == 0) {
                    Log.e(Constants.LOG_TAG, "Failed to send message : " + message);
                } else {
                    ++sentCount;
                }
            }

            Log.d(Constants.LOG_TAG, "MsgSendingThread stopped");
        }
    }
}
