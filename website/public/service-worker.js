// Service Worker for Map Marks background tasks and review reminders
const CACHE_NAME = 'mapmarks-review-v1';
const REVIEW_STATE_URL = '/review-state.json';

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
    event.waitUntil(
      caches.open(CACHE_NAME).then((cache) => {
        return cache.put(
          new Request(REVIEW_STATE_URL),
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
    const cache = await caches.open(CACHE_NAME);
    const response = await cache.match(REVIEW_STATE_URL);
    if (!response) return;
    const data = await response.json();
    if (isReviewDue(data)) {
      const title = data.appName || 'Map Marks';
      const label = (data.markNamePlural || 'marks').toLowerCase();
      const options = {
        body: `Some ${label} are due to be reviewed.`,
        icon: data.appIcon || 'favicon.ico'
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
  event.waitUntil(
    clients.matchAll({
      type: 'window',
      includeUncontrolled: true
    }).then((clientList) => {
      for (const client of clientList) {
        if (client.url && 'focus' in client) {
          return client.focus();
        }
      }
      if (clients.openWindow) {
        return clients.openWindow('./');
      }
    })
  );
});
