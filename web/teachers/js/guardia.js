/**
 * Módulo guardia.js
 *
 * Entrada de las pantallas del equipo docente: exige sesión, muestra quién la tiene y deja
 * entrar solo al grupo Docentes (ver common/js/seccion.js). Va como primer script de cada
 * pantalla de esta carpeta. Usa el adaptador de docentes: en modo simulado sus usuarios
 * son los integrantes del equipo.
 */

import { cerrarSesion, usuarioActual } from './api-docentes.js';
import { protegerSeccion } from '../../common/js/seccion.js';

protegerSeccion('docentes', { consultarUsuario: usuarioActual, cerrarSesion });
