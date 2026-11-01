// Microinteracciones de TratoSeguro EC (solo presentación, no toca la lógica del servidor).
// Las animaciones están en estilos.css y respetan "prefers-reduced-motion".
document.addEventListener('DOMContentLoaded', function () {

    // Lee una duración en ms desde las variables CSS (para que JS y CSS usen el mismo tiempo)
    const estilos = getComputedStyle(document.documentElement);
    const ms = function (nombre, valorPorDefecto) {
        const valor = parseFloat(estilos.getPropertyValue(nombre));
        return Number.isFinite(valor) ? valor : valorPorDefecto;
    };

    // ---------- 1. Toast de confirmación ----------
    // Aparece al cargar la página, se cierra solo a los 5 segundos o con la X.
    // Si el mouse está encima, espera a que salga para cerrarse.
    const toast = document.querySelector('.t-toast');
    if (toast) {
        let temporizador = null;

        const cerrar = function () {
            toast.classList.remove('is-open');
            // Cuando termina la animación de salida se oculta del todo (tampoco se puede enfocar)
            setTimeout(function () { toast.hidden = true; }, ms('--toast-close', 250) + 50);
        };
        const programarCierre = function () {
            clearTimeout(temporizador);
            temporizador = setTimeout(cerrar, 5000);
        };

        // Doble requestAnimationFrame: el navegador pinta primero el estado inicial y luego anima
        requestAnimationFrame(function () {
            requestAnimationFrame(function () { toast.classList.add('is-open'); });
        });
        programarCierre();

        toast.addEventListener('mouseenter', function () { clearTimeout(temporizador); });
        toast.addEventListener('mouseleave', programarCierre);
        const botonCerrar = toast.querySelector('[data-cerrar-toast]');
        if (botonCerrar) {
            botonCerrar.addEventListener('click', function () {
                clearTimeout(temporizador);
                cerrar();
            });
        }
    }

    // ---------- 2. Sacudida del primer campo que el servidor rechazó ----------
    // El borde rojo y el mensaje los pone Bootstrap; aquí solo se sacude el campo una vez
    // y se le da el foco para que el usuario lo corrija directamente.
    const campoConError = document.querySelector('form .is-invalid');
    if (campoConError) {
        campoConError.classList.add('t-input', 'is-shaking');
        const duracion = ms('--shake-dur-a', 80) * 2 + ms('--shake-dur-b', 60) * 2;
        setTimeout(function () { campoConError.classList.remove('is-shaking'); }, duracion + 20);
        campoConError.focus();
    }
});
