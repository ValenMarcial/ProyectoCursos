/**
 * Módulo guardia.js
 *
 * Entrada de las pantallas de Administración: exige sesión, muestra quién la tiene y deja
 * entrar solo al grupo Administracion (ver common/js/seccion.js). Va como primer script de
 * cada pantalla de esta carpeta.
 */

import { cerrarSesion, usuarioActual } from './api.js';
import { protegerSeccion } from '../../common/js/seccion.js';

protegerSeccion('administracion', { consultarUsuario: usuarioActual, cerrarSesion });
