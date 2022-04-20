const _sortedSetName = socketId => `msgHistory_${socketId}`;

/**
 * Inserts in to message history if the given message is not already exist in the history.
 * @param {RedisClient} redisClient
 * @param {string} socketId
 * @param {string} roomId
 * @param {Object} message
 * @returns {Promise<Boolean>} Promise that returns if message was inserted in to the history or not.
 */
exports.insertIfNotExist = (redisClient, socketId, message) => {
  return new Promise((success, failure) => {
    const sortedSetName = _sortedSetName(socketId);

    // Get message count
    redisClient.zcard(sortedSetName, (err, count) => {
      if (err) return failure(err);

      // Message count in history has reached 10? Time to clean-up
      if (count > 9) {
        // This was done to clean-up the message history intermittently
        if (Math.floor(Math.random() * 5) === 0) {
          // Cleaning-up = Reducing history count by half by removing oldest messages
          redisClient.zremrangebyrank(sortedSetName, 0, Math.floor(count / 2), (err, removeCount) => {
          });
        }
      }

      // Insert message in to history
      redisClient.zadd(sortedSetName, count, JSON.stringify(message), (err, insertCount) => {
        if (err) return failure(err);

        // Not inserted as message already exists?
        if (insertCount === 0) return success(false);
        return success(true);
      });
    });
  });
}

/**
 * Deletes message history if exists.
 * @param redisClient
 * @param socketId
 * @param roomId
 * @returns {Promise<Boolean>} Promise that returns if history was deleted or not.
 */
exports.delete = (redisClient, socketId) => {
  return new Promise((success, failure) => {
    redisClient.del(_sortedSetName(socketId), (err, removeCount) => {
      if (err) return failure(err);

      if (removeCount === 0) return success(false);
      return success(true);
    });
  });
}