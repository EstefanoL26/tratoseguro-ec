// Service worker de TratoSeguro EC: recibe las notificaciones push aunque la app esté cerrada.
// No guarda páginas en caché: la app siempre muestra datos frescos del servidor.

self.addEventListener('install', function () {
    self.skipWaiting();
});

self.addEventListener('activate', function (evento) {
    evento.waitUntil(self.clients.claim());
});

// Llega un aviso del servidor: se muestra como notificación del sistema
self.addEventListener('push', function (evento) {
    let datos = { titulo: 'TratoSeguro EC', cuerpo: 'Tienes una novedad en tus tratos.', url: '/notificaciones' };
    try {
        if (evento.data) {
            datos = Object.assign(datos, evento.data.json());
        }
    } catch (e) {
        // Si el aviso no es JSON, se usa el texto por defecto
    }
    evento.waitUntil(self.registration.showNotification(datos.titulo, {
        body: datos.cuerpo,
        icon: '/img/icono-192.png',
        badge: '/img/icono-192.png',
        lang: 'es-EC',
        data: { url: datos.url }
    }));
});

// Al tocar la notificación se abre (o se enfoca) la app en el trato
self.addEventListener('notificationclick', function (evento) {
    evento.notification.close();
    const url = new URL((evento.notification.data && evento.notification.data.url) || '/notificaciones', self.location.origin).href;
    evento.waitUntil(self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then(function (ventanas) {
        for (const ventana of ventanas) {
            if (ventana.url === url && 'focus' in ventana) {
                return ventana.focus();
            }
        }
        return self.clients.openWindow(url);
    }));
});
