// Notificaciones push de TratoSeguro EC: activa o desactiva los avisos en este dispositivo.
// El servidor solo guarda la suscripción; el aviso lo muestra el service worker (/sw.js).
(function () {
    'use strict';

    const csrf = document.querySelector('meta[name="_csrf"]');
    const csrfCabecera = document.querySelector('meta[name="_csrf_header"]');

    function enviar(url, datos) {
        const cabeceras = { 'Content-Type': 'application/json' };
        if (csrf && csrfCabecera) {
            cabeceras[csrfCabecera.content] = csrf.content;
        }
        return fetch(url, { method: 'POST', headers: cabeceras, body: JSON.stringify(datos), keepalive: true, credentials: 'same-origin' });
    }

    // La clave pública VAPID viene en base64url; el navegador la necesita como bytes
    function claveEnBytes(base64url) {
        const relleno = '='.repeat((4 - base64url.length % 4) % 4);
        const base64 = (base64url + relleno).replace(/-/g, '+').replace(/_/g, '/');
        const crudo = atob(base64);
        return Uint8Array.from(crudo, c => c.charCodeAt(0));
    }

    const soportado = 'serviceWorker' in navigator && 'PushManager' in window && 'Notification' in window;
    const esIOS = !/Android/.test(navigator.userAgent)
        && (/iPad|iPhone|iPod/.test(navigator.userAgent) || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1));
    const instalada = window.matchMedia('(display-mode: standalone)').matches || navigator.standalone === true;

    // Al cerrar sesión se quita este dispositivo, para que otra persona que entre no reciba avisos ajenos
    document.querySelectorAll('form[action$="/logout"]').forEach(function (formulario) {
        formulario.addEventListener('submit', function () {
            if (!soportado) { return; }
            navigator.serviceWorker.getRegistration().then(function (registro) {
                return registro && registro.pushManager.getSubscription();
            }).then(function (suscripcion) {
                if (suscripcion) {
                    enviar('/notificaciones/push/cancelar', { endpoint: suscripcion.endpoint });
                    suscripcion.unsubscribe();
                }
            }).catch(function () { /* cerrar sesión igual */ });
        });
    });

    const panel = document.getElementById('panelPush');
    if (!panel) { return; }

    const boton = document.getElementById('botonPush');
    const estado = document.getElementById('estadoPush');
    const etiqueta = document.getElementById('etiquetaPush');
    const ayudaIOS = document.getElementById('ayudaIOS');
    const clave = panel.dataset.vapid;

    function mostrar(texto, activo, textoBoton) {
        estado.textContent = texto;
        etiqueta.textContent = activo ? 'Activadas' : 'Desactivadas';
        etiqueta.className = 'etiqueta ' + (activo ? 'etiqueta-verde' : '');
        if (textoBoton) {
            boton.hidden = false;
            boton.disabled = false;
            boton.querySelector('span').textContent = textoBoton;
            boton.dataset.accion = activo ? 'desactivar' : 'activar';
        } else {
            boton.hidden = true;
        }
    }

    if (!clave) {
        mostrar('Este servidor todavía no tiene configuradas las notificaciones push.', false, null);
        return;
    }
    if (esIOS && !instalada) {
        // En iPhone el push solo funciona con la app agregada a la pantalla de inicio (iOS 16.4 o superior)
        ayudaIOS.hidden = false;
        mostrar('En iPhone primero agrega TratoSeguro a tu pantalla de inicio y ábrelo desde ahí.', false, null);
        return;
    }
    if (!soportado) {
        mostrar('Este navegador no permite notificaciones push. Prueba con Chrome, Edge, Firefox o Safari actualizado.', false, null);
        return;
    }
    if (Notification.permission === 'denied') {
        mostrar('Bloqueaste las notificaciones de este sitio. Actívalas en la configuración del navegador (ícono del candado) y recarga la página.', false, null);
        return;
    }

    let registro = null;
    navigator.serviceWorker.register('/sw.js').then(function (r) {
        registro = r;
        return navigator.serviceWorker.ready;
    }).then(function () {
        return registro.pushManager.getSubscription();
    }).then(function (suscripcion) {
        if (suscripcion) {
            // Se vuelve a enviar por si el servidor no la tenía (por ejemplo, otra cuenta en este navegador)
            enviar('/notificaciones/push/suscribir', suscripcion.toJSON());
            mostrar('Este dispositivo recibe los avisos de tus tratos aunque la app esté cerrada.', true, 'Desactivar en este dispositivo');
        } else {
            mostrar('Activa los avisos para enterarte al instante cuando alguien pague, envíe o abra una disputa.', false, 'Activar notificaciones push');
        }
    }).catch(function () {
        mostrar('No se pudo preparar el servicio de notificaciones en este navegador.', false, null);
    });

    boton.addEventListener('click', function () {
        if (!registro) { return; }
        boton.disabled = true;
        if (boton.dataset.accion === 'desactivar') {
            registro.pushManager.getSubscription().then(function (suscripcion) {
                if (!suscripcion) { return; }
                return enviar('/notificaciones/push/cancelar', { endpoint: suscripcion.endpoint })
                    .then(function () { return suscripcion.unsubscribe(); });
            }).then(function () {
                mostrar('Desactivaste los avisos en este dispositivo.', false, 'Activar notificaciones push');
            });
            return;
        }
        Notification.requestPermission().then(function (permiso) {
            if (permiso !== 'granted') {
                mostrar('No diste permiso para mostrar notificaciones. Puedes activarlo cuando quieras.', false, 'Activar notificaciones push');
                return;
            }
            return registro.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: claveEnBytes(clave) })
                .then(function (suscripcion) {
                    return enviar('/notificaciones/push/suscribir', suscripcion.toJSON()).then(function (respuesta) {
                        if (!respuesta.ok) { throw new Error('servidor'); }
                        mostrar('Listo: este dispositivo recibirá los avisos de tus tratos.', true, 'Desactivar en este dispositivo');
                    });
                });
        }).catch(function () {
            mostrar('No se pudieron activar los avisos. Vuelve a intentarlo.', false, 'Activar notificaciones push');
        });
    });
})();
