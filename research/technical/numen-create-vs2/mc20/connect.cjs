const mc = require('minecraft-protocol');
const c = mc.createClient({ host: '127.0.0.1', port: Number(process.argv[2] || 25576), username: 'LabOwner', auth: 'offline', version: process.argv[3] || '1.20.1' });
c.on('login', () => console.log('logged in'));
c.on('position', p => {c.write('teleport_confirm', {teleportId:p.teleportId}); console.log('position', p.x, p.y, p.z);});
if (process.send) {
  c.on('system_chat', p => {
    if (p.isActionBar) return;
    const component = JSON.parse(p.content);
    const text = typeof component === 'string' ? component : component?.text;
    if (text) process.send(text);
  });
}
c.on('kick_disconnect', p => console.log('kick', JSON.stringify(p)));
c.on('disconnect', p => console.log('disconnect', JSON.stringify(p)));
c.on('end', x => {console.log('end',x); process.exit(0);});
c.on('error', x => console.error(x));
