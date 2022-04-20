const config          = require('./config');

const app             = require('express')();

const server          = require('http').createServer(app);

const io              = require('socket.io')(server, {
                          pingInterval: 25000,
                          pingTimeout: 10000,
                        }).listen(config.socketIoPort); // SocketIO server port taken from config

const redisClient     = require('redis').createClient(
                          config.redisServerPort,
                          config.redisServerHost
                        ); // Redis server should already be running

const messageHistory  = require('./messageHistory');

// Redis client ready handler
redisClient.on('ready', () => {
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
      // console.debug('MSG :', msg);
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

  console.debug('SocketIO server listening at port', config.socketIoPort);
});

// Redis client error handler
redisClient.on('error', err => {
  console.error("Redis client error :", err);
});