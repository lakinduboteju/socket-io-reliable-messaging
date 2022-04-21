const config = require('./config');
const sticky = require('socketio-sticky-session');
const cluster = require('cluster');

const stickyOptions = {
  proxy: true,
  ignoreMissingHeader: true,
};

sticky(stickyOptions, () => {
  // This code will be executed only in slave workers
  const workerId = cluster.worker.id;

  const app = require('express')();

  const server = require('http').createServer(app);

  const io = require('socket.io')(server, {
    pingInterval: 25000,
    pingTimeout: 10000,
  });

  // Redis server should already be running
  const redisClient = require('redis').createClient(
    config.redisServerPort,
    config.redisServerHost
  );

  const messageHistory = require('./messageHistory');

  // Redis client ready handler
  redisClient.on('ready', () => {
    console.debug(`WORKER ${workerId} started. PID:${process.pid}`);

    // SocketIO connection handler
    io.sockets.on('connection', async socket => {
      console.debug('New Connection :', socket.id);

      // SocketIO socket message handler
      socket.on('message', async (msg, ack) => {
        // Acknowledge to received messages
        if (ack) {
          try {
            let inserted = await messageHistory.insertIfNotExist(redisClient, socket.id, msg);
            // Inserted in to history?
            if (inserted) {
              ack(JSON.stringify({status: 1, msg: 'Message received.'})); // Acknowledge and proceed further
            } else {
              // Message already exists in history
              return ack(JSON.stringify({status: 0, msg: 'Message is already received.'})); // Acknowledge and no need to proceed
            }
          } catch (err) {
            console.error('Failed to insert persistent message in to message history and acknowledge :', err);
          }
        }

        // wait for random time (between 0 and 5 secs)
        await new Promise(resolve => setTimeout(resolve, Math.floor(Math.random() * 5) * 1000));
        console.debug(`WRK_ID : ${workerId} MSG : ${msg}`);
      });

      // SocketIO socket disconnect handler
      socket.on('disconnect', async reason => {
        // Delete message history
        await messageHistory.delete(redisClient, socket.id);
        console.debug('Disconnected :', socket.id, ` : ${reason}`);
      });

      // SocketIO socket error handler
      socket.on('error', async err => {
        console.error('Connection Error :', err);
      });
    });
  });

  // Redis client error handler
  redisClient.on('error', err => {
    console.error("Redis client error :", err);
  });

  return server;
}).listen(config.socketIoPort, () => {
  if (cluster.isMaster) {
    console.debug(`MASTER WORKER PID:${process.pid} | SocketIO server listening at port ${config.socketIoPort}`);
  }
});