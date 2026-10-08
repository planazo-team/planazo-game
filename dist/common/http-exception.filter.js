"use strict";
var __decorate = (this && this.__decorate) || function (decorators, target, key, desc) {
    var c = arguments.length, r = c < 3 ? target : desc === null ? desc = Object.getOwnPropertyDescriptor(target, key) : desc, d;
    if (typeof Reflect === "object" && typeof Reflect.decorate === "function") r = Reflect.decorate(decorators, target, key, desc);
    else for (var i = decorators.length - 1; i >= 0; i--) if (d = decorators[i]) r = (c < 3 ? d(r) : c > 3 ? d(target, key, r) : d(target, key)) || r;
    return c > 3 && r && Object.defineProperty(target, key, r), r;
};
var HttpExceptionFilter_1;
Object.defineProperty(exports, "__esModule", { value: true });
exports.HttpExceptionFilter = void 0;
exports.codigoDesdeStatus = codigoDesdeStatus;
exports.normalizarRespuesta = normalizarRespuesta;
const common_1 = require("@nestjs/common");
function codigoDesdeStatus(status) {
    const nombre = common_1.HttpStatus[status];
    return typeof nombre === 'string' ? nombre : 'ERROR';
}
let HttpExceptionFilter = HttpExceptionFilter_1 = class HttpExceptionFilter {
    logger = new common_1.Logger(HttpExceptionFilter_1.name);
    catch(exception, host) {
        const response = host.switchToHttp().getResponse();
        let status = common_1.HttpStatus.INTERNAL_SERVER_ERROR;
        let body = {
            code: codigoDesdeStatus(status),
            message: 'Ocurrió un error inesperado en el servicio.',
        };
        if (exception instanceof common_1.HttpException) {
            status = exception.getStatus();
            body = normalizarRespuesta(exception.getResponse(), status);
        }
        else {
            const detalle = exception instanceof Error ? (exception.stack ?? exception.message) : String(exception);
            this.logger.error(detalle);
        }
        response.status(status).json(body);
    }
};
exports.HttpExceptionFilter = HttpExceptionFilter;
exports.HttpExceptionFilter = HttpExceptionFilter = HttpExceptionFilter_1 = __decorate([
    (0, common_1.Catch)()
], HttpExceptionFilter);
const MENSAJES_POR_DEFECTO = {
    400: 'La petición no es válida.',
    401: 'No estás autenticado.',
    403: 'No tienes permiso para esta operación.',
    404: 'El recurso solicitado no existe.',
    405: 'Método no permitido.',
    409: 'La operación entra en conflicto con el estado actual.',
    422: 'No se pudo procesar la petición.',
    429: 'Demasiadas peticiones, espera un momento.',
    500: 'Ocurrió un error inesperado en el servicio.',
    503: 'El servicio no está disponible en este momento.',
};
function esMensajePorDefectoDeNest(r, status) {
    if (typeof r.message !== 'string')
        return false;
    const soloLetras = (texto) => texto.toLowerCase().replace(/[^a-z]/g, '');
    return (r.message === r.error ||
        soloLetras(r.message) === soloLetras(codigoDesdeStatus(status)) ||
        /^Cannot [A-Z]+ /.test(r.message));
}
function mensajePorDefecto(status) {
    return MENSAJES_POR_DEFECTO[status] ?? 'Error en la petición.';
}
function normalizarRespuesta(respuesta, status) {
    const porDefecto = codigoDesdeStatus(status);
    if (typeof respuesta === 'string') {
        return { code: porDefecto, message: respuesta };
    }
    const r = respuesta;
    const code = typeof r.code === 'string' && r.code ? r.code : porDefecto;
    let message;
    if (typeof r.message === 'string' && r.message && !esMensajePorDefectoDeNest(r, status)) {
        message = r.message;
    }
    else if (esMensajePorDefectoDeNest(r, status)) {
        message = mensajePorDefecto(status);
    }
    else if (Array.isArray(r.message)) {
        message = r.message.map(String).join('. ');
    }
    else if (typeof r.error === 'string') {
        message = r.error;
    }
    else {
        message = mensajePorDefecto(status);
    }
    return { code, message };
}
//# sourceMappingURL=http-exception.filter.js.map