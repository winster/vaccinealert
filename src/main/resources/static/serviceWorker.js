self.addEventListener('activate', event => event.waitUntil(clients.claim()));

self.addEventListener('push', event => event.waitUntil(handlePushEvent(event)));

self.addEventListener('notificationclick', event => event.waitUntil(handleNotificationClick(event)));

self.addEventListener('notificationclose', event => console.info('notificationclose event fired'));

async function handlePushEvent(event) {
  console.info('push event received');
  const needToShow = await needToShowNotification();
  const dataCache = await caches.open('data');

  if (!event.data) {
	console.info('no payload - just ignore');
  } else {
	console.info('payload ', event.data.json());
    const msg = event.data.json();
    if (needToShow) {
      self.registration.showNotification(msg.title, {
        body: msg.body,
        icon: 'chuck.png'
      });
    }
    await dataCache.put('alerts', new Response(msg.body));
  }

  const allClients = await clients.matchAll({ includeUncontrolled: true });
  for (const client of allClients) {
    client.postMessage('alert');
  }
}

const urlToOpen1 = new URL('/index.html#alert', self.location.origin).href;
const urlToOpen2 = new URL('/', self.location.origin).href;

async function handleNotificationClick(event) {

  let openClient = null;
  const allClients = await clients.matchAll({ includeUncontrolled: true, type: 'window' });
  for (const client of allClients) {
    if (client.url === urlToOpen1 || client.url === urlToOpen2) {
      openClient = client;
      break;
    }
  }

  if (openClient) {
    await openClient.focus();
  } else {
    await clients.openWindow(urlToOpen1);
  }

  event.notification.close();
}

async function needToShowNotification() {
  console.log("inside needToShowNotification")
  const allClients = await clients.matchAll({ includeUncontrolled: true });
  for (const client of allClients) {
    if (client.visibilityState === 'visible') {
      console.log("client is visible")
      return false;
    }
  }
  return true;
}
