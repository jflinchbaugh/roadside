// Service Worker for Map Marks background tasks and review reminders
const CACHE_NAME_PREFIX = 'mapmarks-review-v2-';
const REVIEW_STATE_PATH = 'review-state.json';

function getScopeKey() {
  if (self.registration && self.registration.scope) {
    try {
      const scopePath = new URL(self.registration.scope).pathname;
      return scopePath.replace(/^\/+|\/+$/g, '').replace(/[^a-zA-Z0-9_-]/g, '_') || 'root';
    } catch (e) {
      return 'root';
    }
  }
  return 'root';
}

function getCacheName(site) {
  const identifier = site || getScopeKey();
  return `${CACHE_NAME_PREFIX}${identifier}`;
}

function getReviewStateUrl() {
  if (self.registration && self.registration.scope) {
    return new URL(REVIEW_STATE_PATH, self.registration.scope).href;
  }
  return `/${REVIEW_STATE_PATH}`;
}

self.addEventListener('install', (event) => {
  self.skipWaiting();
});

self.addEventListener('activate', (event) => {
  event.waitUntil(self.clients.claim());
});

// Save review configuration when received from client application
self.addEventListener('message', (event) => {
  if (event.data && event.data.type === 'SYNC_REVIEW_CONFIG') {
    const data = event.data;
    const cacheName = getCacheName(data.site);
    const requestUrl = getReviewStateUrl();
    event.waitUntil(
      caches.open(cacheName).then((cache) => {
        return cache.put(
          new Request(requestUrl),
          new Response(JSON.stringify(data), {
            headers: { 'Content-Type': 'application/json' }
          })
        );
      })
    );
  }
});

function isReviewDue(data) {
  if (!data || !data.hasOwnedMarks) return false;
  if (!data.lastReviewed) return true;
  try {
    const lastDate = new Date(data.lastReviewed.substring(0, 10));
    const now = new Date();
    const intervalDays = data.intervalDays || 30;
    const dueDate = new Date(lastDate.getTime() + intervalDays * 86400000);
    return now >= dueDate;
  } catch (e) {
    return true;
  }
}

async function checkReviewAndNotify() {
  try {
    const cacheName = getCacheName();
    const cache = await caches.open(cacheName);
    const requestUrl = getReviewStateUrl();
    let response = await cache.match(requestUrl);
    if (!response) {
      // Fallback: search all caches matching this app's prefix
      const cacheNames = await caches.keys();
      for (const name of cacheNames) {
        if (name.startsWith(CACHE_NAME_PREFIX)) {
          const c = await caches.open(name);
          const r = await c.match(requestUrl);
          if (r) {
            response = r;
            break;
          }
        }
      }
    }
    if (!response) return;
    const data = await response.json();
    if (isReviewDue(data)) {
      const title = data.appName || 'Map Marks';
      const label = (data.markNamePlural || 'marks').toLowerCase();
      const options = {
        body: `Some ${label} are due to be reviewed.`,
        icon: data.appIcon || 'favicon.ico',
        tag: `review-reminder-${data.site || getScopeKey()}`
      };
      await self.registration.showNotification(title, options);
    }
  } catch (err) {
    // Ignore errors during background check
  }
}

// Periodic Background Sync event handler
self.addEventListener('periodicsync', (event) => {
  if (event.tag === 'check-review-due') {
    event.waitUntil(checkReviewAndNotify());
  }
});

// Open/focus app when user clicks on notification
self.addEventListener('notificationclick', (event) => {
  event.notification.close();
  const targetScope = (self.registration && self.registration.scope) || './';
  event.waitUntil(
    clients.matchAll({
      type: 'window',
      includeUncontrolled: true
    }).then((clientList) => {
      for (const client of clientList) {
        if (client.url && client.url.startsWith(targetScope) && 'focus' in client) {
          return client.focus();
        }
      }
      if (clients.openWindow) {
        return clients.openWindow(targetScope);
      }
    })
  );
});
