const assert = require('node:assert/strict');

module.exports = function guide(probe, topic) {
  assert(typeof topic === 'string' && topic.length <= 96 && /^[a-zA-Z0-9 _:/-]+$/.test(topic),
    'expected a scene, keyword or recipe path');
  return probe('guide ' + topic);
};
